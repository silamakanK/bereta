import { Component, HostListener, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { AssociationService } from '../associations/association.service';
import { Campaign, LocalFund } from '../funds/fund.model';
import { FundService } from '../funds/fund.service';
import { PaymentEnrollment } from '../payments/payment.model';
import { apiErrorMessage } from '../shared/api-error';
import { DataRefreshService } from '../shared/data-refresh';
import { MemberTracking } from './member.model';
import { MemberService } from './member.service';

type PaymentStatus = PaymentEnrollment['status'] | 'NON_INSCRIT';
type SortKey = 'nom' | 'reste' | 'statut';

/** Une ligne : un membre et son inscription à la campagne filtrée (ou à chaque campagne avec « Toutes »). */
interface Row {
  key: string;
  member: MemberTracking;
  enrollment: PaymentEnrollment | null;
  status: PaymentStatus;
}

interface Chip { key: string; label: string; clear: () => void; }

/** Valeur du filtre « Caisse » pour les membres rattachés directement à la caisse principale. */
const PRIMARY = 'PRINCIPALE';
const PAGE_SIZE = 20;
const STATUS_ORDER: PaymentStatus[] = ['EN_RETARD', 'A_PAYER', 'PARTIELLEMENT_PAYE', 'NON_INSCRIT', 'MONTANT_A_DEFINIR', 'PAYE'];
const STATUS_LABELS: Record<PaymentStatus, { icon: string; label: string }> = {
  PAYE: { icon: '✓', label: 'Payé' },
  PARTIELLEMENT_PAYE: { icon: '◐', label: 'Partiellement payé' },
  A_PAYER: { icon: '○', label: 'À payer' },
  EN_RETARD: { icon: '!', label: 'En retard' },
  MONTANT_A_DEFINIR: { icon: '?', label: 'Montant à définir' },
  NON_INSCRIT: { icon: '–', label: 'Non inscrit' },
};
const MONEY = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 0, maximumFractionDigits: 2 });

/**
 * Tableau des membres et de leurs cotisations : colonnes Membre, Caisse, Campagne, Payé / Dû, Statut, Actions ;
 * filtres (recherche, caisse, campagne, statut, inscription), pastilles, tri et pagination côté navigateur.
 * Sur mobile : cartes et filtres dans une feuille du bas.
 */
@Component({
  selector: 'app-member-table',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './member-table.component.html',
  styleUrl: './member-table.component.scss',
})
export class MemberTableComponent {
  private readonly memberService = inject(MemberService);
  private readonly fundService = inject(FundService);
  private readonly associationService = inject(AssociationService);
  private readonly refresh = inject(DataRefreshService);

  /** Responsable de l'association : voit la caisse principale et toutes les caisses locales. */
  readonly responsible = input(false);
  readonly localFunds = input<LocalFund[]>([]);

  readonly add = output<void>();
  readonly edit = output<MemberTracking>();
  readonly history = output<MemberTracking>();
  readonly pay = output<string>();

  protected readonly primaryChoice = PRIMARY;
  protected readonly members = signal<MemberTracking[]>([]);
  protected readonly campaigns = signal<Campaign[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly busyKey = signal<string | null>(null);
  protected readonly menuFor = signal<string | null>(null);
  protected readonly filtersOpen = signal(false);

  // Filtres, tri, page
  protected readonly search = signal('');
  protected readonly fund = signal('');
  protected readonly campaign = signal('');
  protected readonly status = signal('');
  protected readonly enrolled = signal('');
  protected readonly showArchived = signal(false);
  protected readonly sort = signal<SortKey>('nom');
  protected readonly page = signal(1);
  private campaignChosen = false;

  protected readonly openCampaigns = computed(() => this.campaigns().filter((c) => c.status === 'OUVERTE'));
  /** Campagne affichée par défaut : la campagne ouverte la plus récente (ce n'est pas un filtre « actif »). */
  protected readonly defaultCampaignId = computed(() => this.openCampaigns()[0]?.id ?? '');
  protected readonly selectedCampaign = computed(() => this.campaigns().find((c) => c.id === this.campaign()) ?? null);

  protected readonly rows = computed<Row[]>(() => {
    const campaignId = this.campaign();
    const rows: Row[] = [];
    for (const member of this.members()) {
      if (!member.active && !this.showArchived()) continue;
      if (campaignId) {
        const enrollment = member.enrollments.find((item) => item.campaignId === campaignId) ?? null;
        rows.push(this.row(member, enrollment));
      } else if (member.enrollments.length === 0) {
        rows.push(this.row(member, null));
      } else {
        for (const enrollment of member.enrollments) rows.push(this.row(member, enrollment));
      }
    }
    return rows;
  });

  protected readonly filtered = computed(() => {
    const query = this.search().trim().toLocaleLowerCase('fr');
    const fund = this.fund();
    const status = this.status();
    const enrolled = this.enrolled();
    const result = this.rows().filter(({ member, enrollment, status: rowStatus }) => {
      if (query && !`${member.firstName} ${member.lastName} ${member.phone ?? ''}`.toLocaleLowerCase('fr').includes(query)) return false;
      if (fund === PRIMARY ? member.localFundId !== null : fund && member.localFundId !== fund) return false;
      if (status && rowStatus !== status) return false;
      if (enrolled === 'oui' && !enrollment) return false;
      if (enrolled === 'non' && enrollment) return false;
      return true;
    });
    const byName = (a: Row, b: Row) => a.member.lastName.localeCompare(b.member.lastName, 'fr')
      || a.member.firstName.localeCompare(b.member.firstName, 'fr');
    const sort = this.sort();
    return result.sort((a, b) => {
      if (sort === 'reste') return (b.enrollment?.remainingAmount ?? -1) - (a.enrollment?.remainingAmount ?? -1) || byName(a, b);
      if (sort === 'statut') return STATUS_ORDER.indexOf(a.status) - STATUS_ORDER.indexOf(b.status) || byName(a, b);
      return byName(a, b);
    });
  });

  protected readonly memberCount = computed(() => new Set(this.filtered().map((row) => row.member.id)).size);
  protected readonly pageCount = computed(() => Math.max(1, Math.ceil(this.filtered().length / PAGE_SIZE)));
  protected readonly pageRows = computed(() => {
    const page = Math.min(this.page(), this.pageCount());
    return this.filtered().slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  });

  protected readonly chips = computed<Chip[]>(() => {
    const chips: Chip[] = [];
    if (this.search().trim()) chips.push({ key: 'search', label: `« ${this.search().trim()} »`, clear: () => this.set(this.search, '') });
    if (this.fund()) chips.push({ key: 'fund', label: `Caisse : ${this.fundLabel(this.fund())}`, clear: () => this.set(this.fund, '') });
    if (this.campaign() !== this.defaultCampaignId()) {
      chips.push({ key: 'campaign', label: `Campagne : ${this.selectedCampaign()?.name ?? 'Toutes'}`,
        clear: () => { this.campaignChosen = false; this.set(this.campaign, this.defaultCampaignId()); } });
    }
    if (this.status()) chips.push({ key: 'status', label: `Statut : ${STATUS_LABELS[this.status() as PaymentStatus].label}`, clear: () => this.set(this.status, '') });
    if (this.enrolled()) chips.push({ key: 'enrolled', label: this.enrolled() === 'oui' ? 'Inscrits' : 'Non inscrits', clear: () => this.set(this.enrolled, '') });
    if (this.showArchived()) chips.push({ key: 'archived', label: 'Archivés inclus', clear: () => this.set(this.showArchived, false) });
    return chips;
  });

  constructor() {
    // Rechargement : changement d'association, ou écriture réussie n'importe où (DataRefreshService).
    effect(() => {
      this.associationService.activeAssociationId();
      this.refresh.version();
      untracked(() => this.load());
    });
  }

  @HostListener('document:click')
  protected closeMenu(): void {
    this.menuFor.set(null);
  }

  protected load(): void {
    forkJoin({ members: this.memberService.tracking(), campaigns: this.fundService.listCampaigns() }).subscribe({
      next: ({ members, campaigns }) => {
        this.members.set(members);
        this.campaigns.set(campaigns);
        // Par défaut : la campagne ouverte la plus récente (la liste arrive triée de la plus récente à la plus ancienne).
        if (!this.campaignChosen || !campaigns.some((c) => c.id === this.campaign())) {
          this.campaign.set(this.defaultCampaignId());
        }
        this.loading.set(false);
        this.error.set(null);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(error, 'Les membres n’ont pas pu être chargés.'));
      },
    });
  }

  /** Changement d'un filtre : retour à la première page. */
  protected set<T>(target: { set: (value: T) => void }, value: T): void {
    target.set(value);
    this.page.set(1);
  }

  /** Campagne choisie à la main : elle n'est plus remplacée par la campagne ouverte par défaut. */
  protected setCampaign(value: string): void {
    this.campaignChosen = true;
    this.set(this.campaign, value);
  }

  protected resetFilters(): void {
    this.search.set('');
    this.fund.set('');
    this.status.set('');
    this.enrolled.set('');
    this.showArchived.set(false);
    this.campaignChosen = false;
    this.campaign.set(this.defaultCampaignId());
    this.page.set(1);
  }

  protected toggleMenu(key: string, event: Event): void {
    event.stopPropagation();
    this.menuFor.set(this.menuFor() === key ? null : key);
  }

  /** « Inscrire » : à la campagne filtrée si elle est ouverte, sinon ouverture de la fiche pour choisir. */
  protected enroll(row: Row): void {
    const campaign = this.selectedCampaign();
    if (!campaign || campaign.status !== 'OUVERTE') { this.history.emit(row.member); return; }
    this.busyKey.set(row.key);
    this.message.set(null);
    this.error.set(null);
    this.memberService.enroll(row.member.id, { campaignId: campaign.id }).subscribe({
      next: () => {
        this.busyKey.set(null);
        this.message.set(`${row.member.firstName} ${row.member.lastName} est inscrit(e) à « ${campaign.name} ».`);
      },
      error: (error: unknown) => {
        this.busyKey.set(null);
        this.error.set(apiErrorMessage(error, 'L’inscription a échoué.'));
      },
    });
  }

  protected toggleArchive(row: Row): void {
    const member = row.member;
    if (member.active && !confirm(`Archiver ${member.firstName} ${member.lastName} ? Il ne sera plus inscrit aux nouvelles campagnes.`)) return;
    this.menuFor.set(null);
    this.memberService.setActive(member.id, !member.active).subscribe({
      next: () => this.message.set(`${member.firstName} ${member.lastName} ${member.active ? 'est archivé(e)' : 'est réactivé(e)'}.`),
      error: (error: unknown) => this.error.set(apiErrorMessage(error, 'La modification a échoué.')),
    });
  }

  protected statusInfo(status: PaymentStatus) { return STATUS_LABELS[status]; }

  protected money(value: number | null | undefined): string {
    return value === null || value === undefined ? '—' : `${MONEY.format(value)} €`;
  }

  protected initials(member: MemberTracking): string {
    return `${member.firstName.charAt(0)}${member.lastName.charAt(0)}`.toLocaleUpperCase('fr');
  }

  protected fundLabel(value: string): string {
    if (value === PRIMARY) return 'Caisse principale';
    return this.localFunds().find((fund) => fund.id === value)?.name ?? 'Caisse';
  }

  protected campaignLabel(campaign: Campaign): string {
    return `${campaign.name}${campaign.campaignYear ? ' ' + campaign.campaignYear : ''}${campaign.status === 'CLOTUREE' ? ' (clôturée)' : ''}`;
  }

  /** « Non inscrit à la campagne en cours » quand la campagne filtrée est ouverte. */
  protected notEnrolledLabel(): string {
    return this.selectedCampaign()?.status === 'OUVERTE' ? 'Non inscrit à la campagne en cours' : 'Non inscrit';
  }

  private row(member: MemberTracking, enrollment: PaymentEnrollment | null): Row {
    return {
      key: `${member.id}:${enrollment?.id ?? 'none'}`,
      member,
      enrollment,
      status: enrollment ? enrollment.status : 'NON_INSCRIT',
    };
  }
}
