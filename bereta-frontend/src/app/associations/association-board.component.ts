import { Component, DestroyRef, EventEmitter, Output, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, of, switchMap, map, catchError } from 'rxjs';
import { AssociationService } from './association.service';
import { Association } from './association.model';
import { FundService } from '../funds/fund.service';
import { AccountLookup, LocalFund, TreasurerSummary } from '../funds/fund.model';
import { apiErrorMessage } from '../shared/api-error';
import { DataRefreshService } from '../shared/data-refresh';

interface AssociationCard {
  association: Association;
  primaryFundId: string | null;
  localFunds: LocalFund[];
}

/** Parcours unique : étape 1 la caisse locale, étape 2 son trésorier. */
interface Wizard {
  card: AssociationCard;
  step: 'fund' | 'treasurer';
  fund: LocalFund | null;
  replace: TreasurerSummary | null;
}

/**
 * Tableau de bord du responsable : ses associations (cartes), leurs caisses locales, la création
 * d'une caisse locale (bouton global ou depuis une carte) et la désignation du trésorier
 * (compte existant retrouvé par téléphone ou e-mail exact, ou nouveau compte avec mot de passe provisoire).
 */
@Component({
  selector: 'app-association-board',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './association-board.component.html',
  styleUrl: './association-board.component.scss',
})
export class AssociationBoardComponent {
  private readonly associationService = inject(AssociationService);
  private readonly fundService = inject(FundService);
  private readonly refresh = inject(DataRefreshService);

  /** Demande à l'écran parent d'ouvrir le formulaire « Nouvelle association ». */
  @Output() readonly createAssociation = new EventEmitter<void>();

  protected readonly cards = signal<AssociationCard[]>([]);
  protected readonly loading = signal(true);
  protected readonly highlightId = signal<string | null>(null);
  protected readonly expandedId = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly managed = computed(() =>
    this.associationService.associations().filter((item) => item.currentUserResponsible && item.status === 'ACTIVE'));
  /** Carte de l'association active : c'est là que « ＋ Caisse locale » crée la caisse. */
  protected readonly activeCard = computed(() => {
    const cards = this.cards();
    return cards.find((card) => card.association.id === this.associationService.activeAssociationId()) ?? cards[0] ?? null;
  });

  protected readonly wizard = signal<Wizard | null>(null);
  protected readonly busy = signal(false);
  protected readonly wizardError = signal<string | null>(null);
  // Étape 1
  protected fundName = '';
  protected fundLocation = '';
  // Étape 2
  protected identifier = '';
  protected readonly lookup = signal<AccountLookup | null>(null);
  protected firstName = '';
  protected lastName = '';
  protected otherContact = '';
  protected readonly issued = signal<{ name: string; identifier: string; password: string } | null>(null);
  protected readonly copied = signal(false);

  private highlightTimer: ReturnType<typeof setTimeout> | null = null;
  private loadSequence = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => { if (this.highlightTimer) clearTimeout(this.highlightTimer); });
    // Rechargement unique : associations gérées modifiées, ou écriture réussie n'importe où (DataRefreshService).
    effect(() => {
      this.managed();
      this.refresh.version();
      untracked(() => this.reload());
    });
    // Nouvelle association créée depuis le tableau de bord : on la met en évidence.
    effect(() => {
      const created = this.associationService.lastCreatedId();
      if (!created) return;
      untracked(() => this.reload(() => {
        const name = this.cards().find((card) => card.association.id === created)?.association.name ?? 'L’association';
        this.flash(created, `« ${name} » est créée, avec sa caisse principale.`);
        this.associationService.lastCreatedId.set(null);
      }));
    });
  }

  protected reload(after?: () => void): void {
    const associations = this.managed();
    const sequence = ++this.loadSequence;
    this.loading.set(true);
    if (associations.length === 0) {
      this.cards.set([]);
      this.loading.set(false);
      after?.();
      return;
    }
    forkJoin(associations.map((association) => this.fundService.listPrimaryFunds(association.id).pipe(
      switchMap((funds) => {
        const primary = funds[0]; // unique par association (V22)
        if (!primary) return of<AssociationCard>({ association, primaryFundId: null, localFunds: [] });
        return this.fundService.listLocalFunds(primary.id, association.id).pipe(
          map((localFunds) => ({ association, primaryFundId: primary.id, localFunds })));
      }),
      catchError(() => of<AssociationCard>({ association, primaryFundId: null, localFunds: [] })),
    ))).subscribe((cards) => {
      if (sequence !== this.loadSequence) { after?.(); return; } // une réponse plus récente arrive
      this.cards.set(cards);
      this.loading.set(false);
      after?.();
    });
  }

  protected withoutTreasurer(card: AssociationCard): number {
    return card.localFunds.filter((fund) => fund.treasurers.length === 0).length;
  }

  protected toggleDetail(card: AssociationCard): void {
    this.expandedId.set(this.expandedId() === card.association.id ? null : card.association.id);
  }

  // ---- Parcours : ouverture ----------------------------------------------------------------

  /** « ＋ Caisse locale » : étape 1, dans l'association active. */
  protected openNewFund(): void {
    const card = this.activeCard();
    if (!card) return;
    this.fundName = '';
    this.fundLocation = '';
    this.resetTreasurerStep();
    this.wizard.set({ card, step: 'fund', fund: null, replace: null });
  }

  /** « Désigner » / « Changer » sur une caisse existante : directement l'étape 2. */
  protected openDesignation(card: AssociationCard, fund: LocalFund, replace: TreasurerSummary | null = null): void {
    this.resetTreasurerStep();
    this.wizard.set({ card, step: 'treasurer', fund, replace });
  }

  protected closeWizard(): void {
    this.wizard.set(null);
    this.issued.set(null);
  }

  private resetTreasurerStep(): void {
    this.identifier = '';
    this.firstName = '';
    this.lastName = '';
    this.otherContact = '';
    this.lookup.set(null);
    this.issued.set(null);
    this.copied.set(false);
    this.wizardError.set(null);
  }

  // ---- Étape 1 : caisse locale -------------------------------------------------------------

  protected saveFund(): void {
    const current = this.wizard();
    if (!current || this.busy()) return;
    if (!this.fundName.trim()) { this.wizardError.set('Indiquez le nom de la caisse locale.'); return; }
    if (!current.card.primaryFundId) { this.wizardError.set('Cette association n’a pas encore de caisse principale.'); return; }
    this.busy.set(true);
    this.wizardError.set(null);
    this.fundService.createLocalFund(current.card.primaryFundId, {
      name: this.fundName.trim(), location: this.fundLocation.trim() || null, description: null,
    }, current.card.association.id).subscribe({
      next: (fund) => {
        this.busy.set(false);
        this.expandedId.set(current.card.association.id);
        this.wizard.set({ ...current, step: 'treasurer', fund });
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.wizardError.set(apiErrorMessage(error, 'La caisse n’a pas pu être créée.'));
      },
    });
  }

  // ---- Étape 2 : trésorier -----------------------------------------------------------------

  /** « Plus tard » : la caisse reste « Sans responsable ». */
  protected skipTreasurer(): void {
    const current = this.wizard();
    this.closeWizard();
    if (current?.fund) this.flash(current.card.association.id, `Caisse « ${current.fund.name} » créée. Désignez son trésorier quand vous voulez.`);
  }

  protected check(): void {
    const current = this.wizard();
    if (!current?.card.primaryFundId || this.busy()) return;
    if (this.identifier.trim().length < 6) {
      this.wizardError.set('Saisissez le téléphone ou l’e-mail du trésorier.');
      return;
    }
    this.busy.set(true);
    this.wizardError.set(null);
    this.fundService.lookupAccount(current.card.primaryFundId, this.identifier.trim(), current.card.association.id).subscribe({
      next: (result) => { this.busy.set(false); this.lookup.set(result); },
      error: (error: unknown) => {
        this.busy.set(false);
        this.wizardError.set(apiErrorMessage(error, 'La vérification a échoué.'));
      },
    });
  }

  protected designate(): void {
    const current = this.wizard();
    const found = this.lookup();
    if (!current?.card.primaryFundId || !current.fund || !found || this.busy()) return;
    if (!found.exists && (!this.firstName.trim() || !this.lastName.trim())) {
      this.wizardError.set('Indiquez le prénom et le nom du trésorier.');
      return;
    }
    const fund = current.fund;
    const value = this.identifier.trim();
    const other = this.otherContact.trim();
    const isEmail = value.includes('@');
    this.busy.set(true);
    this.wizardError.set(null);
    this.fundService.designateTreasurer(current.card.primaryFundId, {
      localFundId: fund.id,
      phone: isEmail ? (found.exists ? null : other || null) : value,
      email: isEmail ? value : (found.exists ? null : other || null),
      firstName: found.exists ? undefined : this.firstName.trim(),
      lastName: found.exists ? undefined : this.lastName.trim(),
      replaceMembershipId: current.replace?.membershipId ?? null,
    }, current.card.association.id).subscribe({
      next: (result) => {
        this.busy.set(false);
        const name = result.membership.displayName || value;
        if (result.temporaryPassword) {
          this.issued.set({ name, identifier: value, password: result.temporaryPassword });
        } else {
          this.closeWizard();
          this.flash(current.card.association.id, `${name} est maintenant trésorier de « ${fund.name} ».`);
        }
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.wizardError.set(apiErrorMessage(error, 'Le trésorier n’a pas pu être désigné.'));
      },
    });
  }

  protected copyPassword(): void {
    const password = this.issued()?.password;
    if (!password) return;
    navigator.clipboard?.writeText(password).then(() => this.copied.set(true), () => this.copied.set(false));
  }

  /** Trésorier qui a perdu son mot de passe : nouveau mot de passe provisoire. */
  protected resetPassword(card: AssociationCard, fund: LocalFund, treasurer: TreasurerSummary): void {
    if (!card.primaryFundId) return;
    const name = treasurer.displayName || treasurer.phone || treasurer.email || 'le trésorier';
    if (!confirm(`Générer un nouveau mot de passe provisoire pour ${name} ? Ses appareils seront déconnectés.`)) return;
    this.error.set(null);
    this.fundService.issueTemporaryPassword(card.primaryFundId, treasurer.membershipId, card.association.id).subscribe({
      next: (result) => {
        this.resetTreasurerStep();
        this.wizard.set({ card, step: 'treasurer', fund, replace: null });
        this.issued.set({ name, identifier: treasurer.phone || treasurer.email || '', password: result.temporaryPassword });
      },
      error: (error: unknown) => this.error.set(apiErrorMessage(error, 'Le mot de passe n’a pas pu être réinitialisé.')),
    });
  }

  protected removeTreasurer(card: AssociationCard, fund: LocalFund, treasurer: TreasurerSummary): void {
    if (!card.primaryFundId) return;
    if (!confirm(`Retirer ${treasurer.displayName || 'ce trésorier'} de « ${fund.name} » ? Il n’aura plus accès à cette caisse.`)) return;
    this.error.set(null);
    this.fundService.deactivateFundAccess(card.primaryFundId, treasurer.membershipId, card.association.id).subscribe({
      next: () => this.success.set(`${treasurer.displayName || 'Le trésorier'} n’a plus accès à « ${fund.name} ».`),
      error: (error: unknown) => this.error.set(apiErrorMessage(error, 'Le trésorier n’a pas pu être retiré.')),
    });
  }

  private flash(associationId: string, message: string): void {
    this.success.set(message);
    this.highlightId.set(associationId);
    if (this.highlightTimer) clearTimeout(this.highlightTimer);
    this.highlightTimer = setTimeout(() => this.highlightId.set(null), 4000);
  }
}
