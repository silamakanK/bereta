import { Component, DestroyRef, EventEmitter, OnInit, Output, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, of, switchMap, map, catchError } from 'rxjs';
import { AssociationService } from './association.service';
import { Association } from './association.model';
import { FundService } from '../funds/fund.service';
import { AccountLookup, LocalFund, TreasurerSummary } from '../funds/fund.model';
import { apiErrorMessage } from '../shared/api-error';

interface AssociationCard {
  association: Association;
  primaryFundId: string | null;
  localFunds: LocalFund[];
}

interface DesignationTarget {
  card: AssociationCard;
  fund: LocalFund;
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
export class AssociationBoardComponent implements OnInit {
  private readonly associationService = inject(AssociationService);
  private readonly fundService = inject(FundService);

  /** Demande à l'écran parent d'ouvrir le formulaire « Nouvelle association ». */
  @Output() readonly createAssociation = new EventEmitter<void>();
  /** Une caisse ou un trésorier a changé : le tableau de bord recharge ses données. */
  @Output() readonly changed = new EventEmitter<void>();

  protected readonly cards = signal<AssociationCard[]>([]);
  protected readonly loading = signal(true);
  protected readonly highlightId = signal<string | null>(null);
  protected readonly expandedId = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly managed = computed(() =>
    this.associationService.associations().filter((item) => item.currentUserResponsible && item.status === 'ACTIVE'));

  // Création d'une caisse locale
  protected readonly fundDialogOpen = signal(false);
  protected fundAssociationId = '';
  protected fundName = '';
  protected fundLocation = '';
  protected fundDescription = '';
  protected readonly savingFund = signal(false);
  protected readonly fundError = signal<string | null>(null);

  // Désignation du trésorier
  protected readonly designation = signal<DesignationTarget | null>(null);
  protected identifier = '';
  protected readonly lookup = signal<AccountLookup | null>(null);
  protected firstName = '';
  protected lastName = '';
  protected otherContact = '';
  protected readonly designating = signal(false);
  protected readonly designationError = signal<string | null>(null);
  protected readonly issued = signal<{ name: string; identifier: string; password: string } | null>(null);
  protected readonly copied = signal(false);

  private highlightTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => { if (this.highlightTimer) clearTimeout(this.highlightTimer); });
    // Nouvelle association créée depuis le tableau de bord : on recharge et on la met en évidence.
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

  ngOnInit(): void {
    this.reload();
  }

  protected reload(after?: () => void): void {
    const associations = this.managed();
    this.loading.set(true);
    if (associations.length === 0) {
      this.cards.set([]);
      this.loading.set(false);
      after?.();
      return;
    }
    forkJoin(associations.map((association) => this.fundService.listPrimaryFunds(association.id).pipe(
      switchMap((funds) => {
        const primary = funds[0];
        if (!primary) return of<AssociationCard>({ association, primaryFundId: null, localFunds: [] });
        return this.fundService.listLocalFunds(primary.id, association.id).pipe(
          map((localFunds) => ({ association, primaryFundId: primary.id, localFunds })));
      }),
      catchError(() => of<AssociationCard>({ association, primaryFundId: null, localFunds: [] })),
    ))).subscribe((cards) => {
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

  // ---- Caisse locale --------------------------------------------------------------------

  protected openFundDialog(associationId?: string): void {
    this.fundAssociationId = associationId ?? (this.cards().length === 1 ? this.cards()[0].association.id : '');
    this.fundName = '';
    this.fundLocation = '';
    this.fundDescription = '';
    this.fundError.set(null);
    this.fundDialogOpen.set(true);
  }

  protected saveFund(): void {
    const card = this.cards().find((item) => item.association.id === this.fundAssociationId);
    if (this.savingFund()) return;
    if (!card) { this.fundError.set('Choisissez l’association de rattachement.'); return; }
    if (!this.fundName.trim()) { this.fundError.set('Indiquez le nom de la caisse locale.'); return; }
    if (!card.primaryFundId) { this.fundError.set('Cette association n’a pas encore de caisse principale.'); return; }
    this.savingFund.set(true);
    this.fundError.set(null);
    this.fundService.createLocalFund(card.primaryFundId, {
      name: this.fundName.trim(), location: this.fundLocation.trim() || null, description: this.fundDescription.trim() || null,
    }, card.association.id).subscribe({
      next: (fund) => {
        this.savingFund.set(false);
        this.fundDialogOpen.set(false);
        this.expandedId.set(card.association.id);
        this.reload(() => this.flash(card.association.id, `Caisse « ${fund.name} » créée. Désignez son trésorier quand vous voulez.`));
        this.changed.emit();
      },
      error: (error: unknown) => {
        this.savingFund.set(false);
        this.fundError.set(apiErrorMessage(error, 'La caisse n’a pas pu être créée.'));
      },
    });
  }

  // ---- Trésorier ------------------------------------------------------------------------

  protected openDesignation(card: AssociationCard, fund: LocalFund, replace: TreasurerSummary | null = null): void {
    this.designation.set({ card, fund, replace });
    this.identifier = '';
    this.firstName = '';
    this.lastName = '';
    this.otherContact = '';
    this.lookup.set(null);
    this.issued.set(null);
    this.copied.set(false);
    this.designationError.set(null);
  }

  protected closeDesignation(): void {
    this.designation.set(null);
    this.issued.set(null);
  }

  protected check(): void {
    const target = this.designation();
    if (!target?.card.primaryFundId || this.designating()) return;
    if (this.identifier.trim().length < 6) {
      this.designationError.set('Saisissez le téléphone ou l’e-mail du trésorier.');
      return;
    }
    this.designating.set(true);
    this.designationError.set(null);
    this.fundService.lookupAccount(target.card.primaryFundId, this.identifier.trim(), target.card.association.id).subscribe({
      next: (result) => { this.designating.set(false); this.lookup.set(result); },
      error: (error: unknown) => {
        this.designating.set(false);
        this.designationError.set(apiErrorMessage(error, 'La vérification a échoué.'));
      },
    });
  }

  protected designate(): void {
    const target = this.designation();
    const found = this.lookup();
    if (!target?.card.primaryFundId || !found || this.designating()) return;
    if (!found.exists && (!this.firstName.trim() || !this.lastName.trim())) {
      this.designationError.set('Indiquez le prénom et le nom du trésorier.');
      return;
    }
    const value = this.identifier.trim();
    const other = this.otherContact.trim();
    const isEmail = value.includes('@');
    this.designating.set(true);
    this.designationError.set(null);
    this.fundService.designateTreasurer(target.card.primaryFundId, {
      localFundId: target.fund.id,
      phone: isEmail ? (found.exists ? null : other || null) : value,
      email: isEmail ? value : (found.exists ? null : other || null),
      firstName: found.exists ? undefined : this.firstName.trim(),
      lastName: found.exists ? undefined : this.lastName.trim(),
      replaceMembershipId: target.replace?.membershipId ?? null,
    }, target.card.association.id).subscribe({
      next: (result) => {
        this.designating.set(false);
        const name = result.membership.displayName || value;
        if (result.temporaryPassword) {
          this.issued.set({ name, identifier: value, password: result.temporaryPassword });
        } else {
          this.closeDesignation();
          this.success.set(`${name} est maintenant trésorier de « ${target.fund.name} ».`);
        }
        this.reload();
        this.changed.emit();
      },
      error: (error: unknown) => {
        this.designating.set(false);
        this.designationError.set(apiErrorMessage(error, 'Le trésorier n’a pas pu être désigné.'));
      },
    });
  }

  protected copyPassword(): void {
    const password = this.issued()?.password;
    if (!password) return;
    navigator.clipboard?.writeText(password).then(() => this.copied.set(true), () => this.copied.set(false));
  }

  protected removeTreasurer(card: AssociationCard, fund: LocalFund, treasurer: TreasurerSummary): void {
    if (!card.primaryFundId) return;
    if (!confirm(`Retirer ${treasurer.displayName || 'ce trésorier'} de « ${fund.name} » ? Il n’aura plus accès à cette caisse.`)) return;
    this.error.set(null);
    this.fundService.deactivateFundAccess(card.primaryFundId, treasurer.membershipId, card.association.id).subscribe({
      next: () => {
        this.success.set(`${treasurer.displayName || 'Le trésorier'} n’a plus accès à « ${fund.name} ».`);
        this.reload();
        this.changed.emit();
      },
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
