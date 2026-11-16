import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LocalFund } from '../funds/fund.model';
import { apiErrorMessage } from '../shared/api-error';
import { ModalDialogDirective } from '../shared/modal-dialog.directive';
import { UnreadableImageError, compressPhoto, isHeic } from './image-compression';
import {
  DuplicateAction, ExistingMember, ImportPreview, ImportRow, ImportRowStatus, ImportRowUpdate, ImportValidation,
} from './import.model';
import { ImportService } from './import.service';

type Step = 1 | 2 | 3 | 4 | 5 | 6;
type Tab = 'toutes' | 'verifier' | 'doublons' | 'exclues';

/** Page à envoyer : photo compressée (avec aperçu) ou document tel quel. */
interface Page {
  key: number;
  blob: Blob;
  name: string;
  photo: boolean;
  previewUrl: string | null;
}

/** Ligne de l'aperçu, modifiable localement ; enregistrée au serveur à « Vérifier » ou « Continuer ». */
interface EditableRow {
  key: number;
  id: string | null;
  lineNumber: number | null;
  firstName: string;
  lastName: string;
  phone: string;
  included: boolean;
  confirmed: boolean;
  duplicateAction: DuplicateAction | null;
  status: ImportRowStatus | null;
  issues: string[];
  confirmable: boolean;
  duplicateMember: ExistingMember | null;
  duplicateOfLine: number | null;
  read: string | null;
  dirty: boolean;
}

const PRIMARY = 'PRINCIPALE';
const MAX_PHOTOS = 5;
const MAX_BYTES = 10 * 1024 * 1024;
const DOCUMENT = /\.(xlsx|csv|pdf)$/i;
const STEP_TITLES: Record<Step, string> = {
  1: 'Destination', 2: 'Document', 3: 'Lecture', 4: 'Aperçu', 5: 'Récapitulatif', 6: 'Terminé',
};
const STATUS: Record<ImportRowStatus | 'EXCLUE', { icon: string; label: string; css: string }> = {
  PRETE: { icon: '✓', label: 'Prête', css: 'ok' },
  A_VERIFIER: { icon: '!', label: 'À vérifier', css: 'warn' },
  DOUBLON: { icon: '⇄', label: 'Doublon possible', css: 'dup' },
  EXCLUE: { icon: '×', label: 'Exclue', css: 'off' },
};

/**
 * Import de membres par photo ou fichier, en 6 étapes (mobile d'abord) :
 * destination → document → lecture → aperçu modifiable → récapitulatif → résultat.
 * Rien n'est enregistré avant « Valider et enregistrer » ; fermer en cours de route annule le brouillon.
 */
@Component({
  selector: 'app-member-import',
  standalone: true,
  imports: [FormsModule, DatePipe, ModalDialogDirective],
  templateUrl: './member-import.component.html',
  styleUrl: './member-import.component.scss',
})
export class MemberImportComponent implements OnInit, OnDestroy {
  private readonly importService = inject(ImportService);

  /** Responsable : caisse principale ou n'importe quelle caisse locale. Trésorier : ses caisses uniquement. */
  readonly responsible = input(false);
  readonly localFunds = input<LocalFund[]>([]);
  readonly defaultLocalFundId = input<string | null>(null);

  readonly closed = output<void>();
  /** « Voir les membres importés » : la liste des membres est filtrée sur cet import. */
  readonly showMembers = output<string>();

  protected readonly primaryChoice = PRIMARY;
  protected readonly maxPhotos = MAX_PHOTOS;
  protected readonly step = signal<Step>(1);
  protected readonly fundChoice = signal('');
  protected readonly pages = signal<Page[]>([]);
  protected readonly preparing = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly preview = signal<ImportPreview | null>(null);
  protected readonly rows = signal<EditableRow[]>([]);
  protected readonly tab = signal<Tab>('toutes');
  protected readonly showPhoto = signal(true);
  protected readonly activePage = signal(0);
  protected readonly result = signal<ImportValidation | null>(null);
  protected enroll = false;
  protected acknowledged = false;
  private nextKey = 1;

  protected readonly stepTitle = computed(() => STEP_TITLES[this.step()]);
  protected readonly progress = computed(() => Math.round((this.step() / 6) * 100));
  protected readonly fundOptions = computed(() => {
    const options = this.localFunds().map((fund) => ({ id: fund.id, name: fund.name }));
    return this.responsible() ? [{ id: PRIMARY, name: 'Caisse principale' }, ...options] : options;
  });
  /** Trésorier d'une seule caisse : destination pré-remplie et verrouillée. */
  protected readonly fundLocked = computed(() => !this.responsible() && this.fundOptions().length === 1);
  protected readonly fundName = computed(() =>
    this.fundOptions().find((option) => option.id === this.fundChoice())?.name ?? '');
  protected readonly photos = computed(() => this.pages().filter((page) => page.photo));
  protected readonly hasDocument = computed(() => this.pages().some((page) => !page.photo));

  protected readonly counts = computed(() => {
    const rows = this.rows();
    return {
      toutes: rows.length,
      verifier: rows.filter((row) => row.included && this.statusOf(row) === 'A_VERIFIER').length,
      doublons: rows.filter((row) => row.included && this.statusOf(row) === 'DOUBLON').length,
      exclues: rows.filter((row) => !row.included).length,
    };
  });
  protected readonly visibleRows = computed(() => {
    const tab = this.tab();
    return this.rows().filter((row) => {
      if (tab === 'exclues') return !row.included;
      if (tab === 'verifier') return row.included && this.statusOf(row) === 'A_VERIFIER';
      if (tab === 'doublons') return row.included && this.statusOf(row) === 'DOUBLON';
      return true;
    });
  });
  protected readonly hasUnsavedChanges = computed(() => this.rows().some((row) => row.dirty)
    || this.rows().length !== (this.preview()?.rows.length ?? 0));

  ngOnInit(): void {
    this.fundChoice.set(this.initialFund());
  }

  ngOnDestroy(): void {
    this.revokePreviews(this.pages());
  }

  // ---- Étape 1 : destination -----------------------------------------------------------------

  private initialFund(): string {
    const options = this.fundOptions();
    if (this.responsible()) return PRIMARY;
    const preferred = this.defaultLocalFundId();
    return options.find((option) => option.id === preferred)?.id ?? options[0]?.id ?? '';
  }

  protected goToDocument(): void {
    if (!this.fundChoice()) return;
    this.error.set(null);
    this.step.set(2);
  }

  // ---- Étape 2 : photos ou fichier -----------------------------------------------------------

  protected async addFiles(event: Event, fromCamera: boolean): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = ''; // permet de reprendre la même photo
    if (files.length === 0) return;
    this.error.set(null);
    this.notice.set(null);
    const problem = this.checkFiles(files);
    if (problem) {
      this.error.set(problem);
      return;
    }
    const isPhoto = (file: File) => fromCamera || file.type.startsWith('image/') || isHeic(file);
    const document = files.find((file) => !isPhoto(file));
    if (document) {
      // Un document se lit seul : il remplace les photos déjà ajoutées.
      if (this.photos().length) this.notice.set('Le document remplace les photos ajoutées.');
      this.replacePages([{ key: this.nextKey++, blob: document, name: document.name, photo: false, previewUrl: null }]);
      return;
    }
    if (this.photos().length + files.length > MAX_PHOTOS) {
      this.error.set(`${MAX_PHOTOS} photos maximum par import.`);
      return;
    }
    await this.addPhotos(files);
  }

  /** Taille et format ; renvoie le message d'erreur, ou null si tout est accepté. */
  private checkFiles(files: File[]): string | null {
    const tooLarge = files.find((file) => file.size > MAX_BYTES);
    if (tooLarge) return `« ${tooLarge.name} » dépasse 10 Mo.`;
    const unsupported = files.some((file) => !file.type.startsWith('image/') && !isHeic(file) && !DOCUMENT.test(file.name));
    return unsupported ? 'Format non pris en charge. Envoyez une photo, un PDF, un fichier Excel (.xlsx) ou CSV.' : null;
  }

  /** Compression de toutes les photos en parallèle, ajoutées dans l'ordre choisi. */
  private async addPhotos(files: File[]): Promise<void> {
    this.preparing.set(true);
    try {
      const blobs = await Promise.all(files.map((file) => compressPhoto(file)));
      const added: Page[] = blobs.map((blob, index) => ({
        key: this.nextKey++, blob, name: files[index].name.replace(/\.[^.]+$/, '') + '.jpg', photo: true,
        previewUrl: URL.createObjectURL(blob),
      }));
      if (this.hasDocument()) this.notice.set('La photo remplace le document choisi.');
      this.replacePages([...this.photos(), ...added]);
    } catch (error) {
      this.error.set(error instanceof UnreadableImageError && files.some(isHeic)
        ? 'Photo HEIC illisible sur cet appareil : enregistrez-la en JPEG (ou réglez l’appareil photo sur « Le plus compatible »).'
        : 'Cette photo n’a pas pu être lue. Réessayez avec une autre photo.');
    } finally {
      this.preparing.set(false);
    }
  }

  protected removePage(key: number): void {
    const removed = this.pages().filter((page) => page.key === key);
    this.revokePreviews(removed);
    this.pages.update((pages) => pages.filter((page) => page.key !== key));
    this.activePage.set(0);
  }

  private replacePages(next: Page[]): void {
    const kept = new Set(next.map((page) => page.key));
    this.revokePreviews(this.pages().filter((page) => !kept.has(page.key)));
    this.pages.set(next);
  }

  private revokePreviews(pages: Page[]): void {
    for (const page of pages) if (page.previewUrl) URL.revokeObjectURL(page.previewUrl);
  }

  protected analyse(): void {
    if (this.pages().length === 0 || this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.step.set(3);
    const fund = this.fundChoice() === PRIMARY ? null : this.fundChoice();
    this.importService.analyse(this.pages().map(({ blob, name }) => ({ blob, name })), fund).subscribe({
      next: (preview) => {
        this.busy.set(false);
        this.load(preview);
        this.tab.set(preview.counters.toCheck > 0 ? 'verifier' : 'toutes');
        this.step.set(4);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.step.set(2);
        this.error.set(apiErrorMessage(error, 'Lecture impossible, réessayez avec une photo plus nette.'));
      },
    });
  }

  // ---- Étape 4 : aperçu modifiable -----------------------------------------------------------

  private load(preview: ImportPreview): void {
    this.preview.set(preview);
    this.rows.set(preview.rows.map((row) => this.editable(row)));
  }

  private editable(row: ImportRow): EditableRow {
    const read = [row.readFirstName, row.readLastName, row.readPhone].filter(Boolean).join(' · ');
    return {
      key: this.nextKey++, id: row.id, lineNumber: row.lineNumber,
      firstName: row.firstName ?? '', lastName: row.lastName ?? '', phone: row.phone ?? '',
      included: row.included, confirmed: row.confirmed, duplicateAction: row.duplicateAction,
      status: row.status, issues: row.issues, confirmable: row.confirmable, duplicateMember: row.duplicateMember,
      duplicateOfLine: row.duplicateOfLine, read: read || null, dirty: false,
    };
  }

  protected statusOf(row: EditableRow): ImportRowStatus | null {
    return row.status;
  }

  protected statusInfo(row: EditableRow) {
    return STATUS[row.included ? (row.status ?? 'A_VERIFIER') : 'EXCLUE'];
  }

  /** Modification d'une ligne : enregistrée (et revérifiée) à « Vérifier » ou « Continuer ». */
  protected change(row: EditableRow, patch: Partial<EditableRow>): void {
    this.rows.update((rows) => rows.map((item) => item.key === row.key ? { ...item, ...patch, dirty: true } : item));
  }

  protected removeRow(row: EditableRow): void {
    this.rows.update((rows) => rows.filter((item) => item.key !== row.key));
  }

  protected addRow(): void {
    this.tab.set('toutes');
    this.rows.update((rows) => [...rows, {
      key: this.nextKey++, id: null, lineNumber: null, firstName: '', lastName: '', phone: '',
      included: true, confirmed: true, duplicateAction: null, status: null, issues: [], confirmable: true,
      duplicateMember: null, duplicateOfLine: null, read: null, dirty: true,
    }]);
    queueMicrotask(() => document.querySelector<HTMLInputElement>('.imp-row:last-of-type .f-first')?.focus());
  }

  /** Enregistre les corrections ; le serveur recalcule statuts et doublons. */
  protected save(thenContinue: boolean): void {
    const preview = this.preview();
    if (!preview || this.busy()) return;
    if (this.rows().length === 0) {
      this.error.set('La liste est vide : ajoutez au moins une ligne.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const updates: ImportRowUpdate[] = this.rows().map((row) => ({
      id: row.id, firstName: row.firstName.trim() || null, lastName: row.lastName.trim() || null,
      phone: row.phone.trim() || null, included: row.included, confirmed: row.confirmed,
      duplicateAction: row.duplicateAction,
    }));
    this.importService.updateRows(preview.id, updates).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.load(updated);
        if (!thenContinue) return;
        if (updated.counters.toCheck > 0) {
          this.tab.set('verifier');
          this.error.set(updated.counters.toCheck === 1
            ? '1 ligne est encore à vérifier : corrigez-la (ou cochez « Ligne vérifiée » si elle le propose), ou décochez-la.'
            : `${updated.counters.toCheck} lignes sont encore à vérifier : corrigez-les (ou cochez « Ligne vérifiée » quand elles le proposent), ou décochez-les.`);
          return;
        }
        if (updated.counters.toCreate === 0) {
          this.error.set('Aucun membre à créer : toutes les lignes sont exclues ou ignorées.');
          return;
        }
        this.acknowledged = !updated.alreadyImportedAt;
        this.enroll = false;
        this.step.set(5);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(apiErrorMessage(error, 'Les corrections n’ont pas pu être enregistrées.'));
      },
    });
  }

  // ---- Étapes 5 et 6 : validation, résultat --------------------------------------------------

  protected validate(): void {
    const preview = this.preview();
    if (!preview || this.busy() || !this.acknowledged) return;
    this.busy.set(true);
    this.error.set(null);
    this.importService.validate(preview.id, this.enroll && !!preview.currentCampaignName).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.result.set(result);
        this.step.set(6);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(apiErrorMessage(error, 'L’import n’a pas pu être validé. Réessayez.'));
      },
    });
  }

  protected viewMembers(): void {
    const result = this.result();
    if (result) this.showMembers.emit(result.id);
  }

  protected restart(): void {
    this.replacePages([]);
    this.preview.set(null);
    this.rows.set([]);
    this.result.set(null);
    this.error.set(null);
    this.notice.set(null);
    this.step.set(this.fundLocked() ? 2 : 1);
  }

  protected back(): void {
    this.error.set(null);
    const step = this.step();
    if (step === 5) this.step.set(4);
    else if (step === 4) this.abandon(() => this.step.set(2));
    else if (step === 2) this.step.set(1);
  }

  /** Fermeture (×, Échap, fond) : un brouillon en cours est annulé après confirmation. */
  protected requestClose(): void {
    if (this.step() === 6 || this.busy()) {
      if (!this.busy()) this.closed.emit();
      return;
    }
    this.abandon(() => this.closed.emit());
  }

  private abandon(then: () => void): void {
    const preview = this.preview();
    if (!preview) {
      then();
      return;
    }
    if (!confirm('Abandonner cet import ? Les lignes lues seront supprimées et aucun membre ne sera créé.')) return;
    this.importService.cancel(preview.id).subscribe({ error: () => undefined });
    this.preview.set(null);
    this.rows.set([]);
    then();
  }

  protected setTab(tab: Tab): void {
    this.tab.set(tab);
  }

  protected duplicateLabel(row: EditableRow): string {
    const member = row.duplicateMember;
    if (member?.visible) return `Déjà membre : ${member.firstName} ${member.lastName}${member.fundName ? ' (' + member.fundName + ')' : ''}`;
    if (member) return 'Déjà membre de l’association (autre caisse)';
    return row.duplicateOfLine ? `Même personne que la ligne ${row.duplicateOfLine}` : '';
  }

  /** Problèmes affichés, sans répéter le libellé du doublon (affiché à part avec son choix). */
  protected otherIssues(row: EditableRow): string[] {
    return row.issues.filter((issue) => !issue.startsWith('Déjà membre') && !issue.startsWith('Même personne'));
  }
}
