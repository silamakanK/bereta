import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { Campaign, CampaignType, FundMembership, LocalFund, PrimaryFund } from './fund.model';
import { FundService } from './fund.service';
import { CashRemittanceSummary, Remittance, RemittanceStatus } from './remittance.model';

@Component({
  selector: 'app-fund-setup',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, FormsModule],
  templateUrl: './fund-setup.component.html',
  styleUrl: './fund-setup.component.scss',
})
export class FundSetupComponent {
  @Output() readonly close = new EventEmitter<void>();

  private readonly fundService = inject(FundService);

  protected readonly primaryFunds = signal<PrimaryFund[]>([]);
  protected readonly localFunds = signal<LocalFund[]>([]);
  protected readonly campaigns = signal<Campaign[]>([]);
  protected readonly memberships = signal<FundMembership[]>([]);
  protected readonly remittances = signal<Remittance[]>([]);
  protected readonly remittanceSummary = signal<CashRemittanceSummary | null>(null);
  protected readonly canManageSelectedFund = signal(false);
  /** Mot de passe provisoire à remettre au trésorier : affiché une seule fois. */
  protected readonly issuedPassword = signal<{ name: string; password: string } | null>(null);
  protected readonly isLoading = signal(true);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly primaryFormOpen = signal(true);
  protected readonly localFormOpen = signal(false);
  protected readonly campaignFormOpen = signal(false);
  protected readonly remittanceFormOpen = signal(false);
  protected readonly cancellingRemittanceId = signal<string | null>(null);

  protected primaryName = '';
  protected primaryObjective = '';
  protected primaryDescription = '';
  protected selectedPrimaryFundId = '';
  protected localFundName = '';
  protected treasurerPhone = '';
  protected treasurerFirstName = '';
  protected treasurerLastName = '';
  protected selectedTreasurerLocalFundId = '';
  protected campaignName = '';
  protected campaignType: CampaignType = 'ANNUAL';
  protected campaignYear = new Date().getFullYear();
  protected campaignAmount = '';
  protected campaignDueDate = '';
  protected remittanceLocalFundId = '';
  protected remittanceAmount = '';
  protected remittanceDate = this.localDateToday();
  protected remittanceMethod: 'ESPECES' | 'VIREMENT' | 'CHEQUE' | 'AUTRE' = 'ESPECES';
  protected remittanceNote = '';
  protected cancellationReason = '';

  constructor() {
    this.loadConfiguration();
  }

  protected loadConfiguration(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    forkJoin({
      funds: this.fundService.listPrimaryFunds(),
      campaigns: this.fundService.listCampaigns(),
    }).subscribe({
      next: ({ funds, campaigns }) => {
        this.primaryFunds.set(funds);
        this.campaigns.set(campaigns);
        if (!funds.some((fund) => fund.id === this.selectedPrimaryFundId)) {
          this.selectedPrimaryFundId = funds[0]?.id ?? '';
        }
        this.loadLocalFunds();
        this.loadMemberships();
        this.loadRemittances();
        this.isLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage.set(this.describeError(error));
        this.isLoading.set(false);
      },
    });
  }

  protected onPrimaryFundChange(id: string): void {
    this.selectedPrimaryFundId = id;
    this.issuedPassword.set(null);
    this.loadLocalFunds();
    this.loadMemberships();
    this.loadRemittances();
  }

  protected createPrimaryFund(): void {
    if (this.saving()) return;
    this.startSave();
    this.fundService.createPrimaryFund({
      name: this.primaryName.trim(),
      objective: this.primaryObjective.trim(),
      description: this.primaryDescription.trim() || null,
    }).subscribe({
      next: (fund) => {
        this.primaryFunds.update((funds) => [...funds, fund].sort((a, b) => a.name.localeCompare(b.name, 'fr')));
        this.selectedPrimaryFundId = fund.id;
        this.primaryName = '';
        this.primaryObjective = '';
        this.primaryDescription = '';
        this.primaryFormOpen.set(false);
        this.loadLocalFunds();
        this.loadMemberships();
        this.loadRemittances();
        this.saved('Caisse principale créée.');
      },
      error: (error: HttpErrorResponse) => this.failed(error),
    });
  }

  protected createLocalFund(): void {
    if (this.saving() || !this.selectedPrimaryFundId || !this.canManageSelectedFund()) return;
    this.startSave();
    this.fundService.createLocalFund(this.selectedPrimaryFundId, this.localFundName.trim()).subscribe({
      next: (fund) => {
        this.localFunds.update((funds) => [...funds, fund].sort((a, b) => a.name.localeCompare(b.name, 'fr')));
        this.localFundName = '';
        this.localFormOpen.set(false);
        this.saved('Caisse locale créée.');
      },
      error: (error: HttpErrorResponse) => this.failed(error),
    });
  }

  protected designateTreasurer(): void {
    if (this.saving() || !this.canManageSelectedFund() || !this.selectedTreasurerLocalFundId) return;
    if (!this.treasurerPhone.trim()) {
      this.errorMessage.set('Saisissez le numéro de téléphone portable du trésorier.');
      return;
    }
    this.startSave();
    this.issuedPassword.set(null);
    this.fundService.designateTreasurer(this.selectedPrimaryFundId, {
      localFundId: this.selectedTreasurerLocalFundId,
      phone: this.treasurerPhone.trim(),
      firstName: this.treasurerFirstName.trim() || undefined,
      lastName: this.treasurerLastName.trim() || undefined,
    }).subscribe({
      next: (designation) => {
        const membership = designation.membership;
        this.treasurerPhone = '';
        this.treasurerFirstName = '';
        this.treasurerLastName = '';
        this.memberships.update((items) => [...items.filter((item) => item.id !== membership.id), membership]);
        if (designation.temporaryPassword) {
          this.issuedPassword.set({ name: membership.displayName, password: designation.temporaryPassword });
        }
        this.saved(designation.accountCreated
          ? `Compte créé pour ${membership.displayName}. Remettez-lui le mot de passe provisoire ci-dessous.`
          : `${membership.displayName} utilise son compte existant et a accès à ${membership.localFundName}.`);
      },
      error: (error: HttpErrorResponse) => this.failed(error),
    });
  }

  protected issueTemporaryPassword(membership: FundMembership): void {
    if (this.saving() || membership.role !== 'TRESORIER') return;
    this.startSave();
    this.issuedPassword.set(null);
    this.fundService.issueTemporaryPassword(this.selectedPrimaryFundId, membership.id).subscribe({
      next: (result) => {
        this.issuedPassword.set({ name: membership.displayName, password: result.temporaryPassword });
        this.saved(`Nouveau mot de passe provisoire pour ${membership.displayName} : ses appareils sont déconnectés.`);
      },
      error: (error: HttpErrorResponse) => this.failed(error),
    });
  }

  protected deactivateTreasurer(membership: FundMembership): void {
    if (this.saving() || membership.role !== 'TRESORIER') return;
    this.startSave();
    this.fundService.deactivateFundAccess(this.selectedPrimaryFundId, membership.id).subscribe({
      next: () => {
        this.memberships.update((items) => items.filter((item) => item.id !== membership.id));
        this.saved(`Accès de ${membership.displayName} désactivé.`);
      },
      error: (error: HttpErrorResponse) => this.failed(error),
    });
  }

  protected createCampaign(): void {
    if (this.saving() || !this.selectedPrimaryFundId || !this.canManageSelectedFund()) return;
    this.startSave();
    const amount = this.campaignAmount.trim().replace(',', '.');
    this.fundService.createCampaign({
      primaryFundId: this.selectedPrimaryFundId,
      name: this.campaignName.trim(),
      campaignType: this.campaignType,
      campaignYear: this.campaignType === 'ANNUAL' ? this.campaignYear : null,
      expectedAmount: amount || null,
      dueDate: this.campaignDueDate || null,
    }).subscribe({
      next: (campaign) => {
        this.campaigns.update((campaigns) => [campaign, ...campaigns]);
        this.campaignName = '';
        this.campaignAmount = '';
        this.campaignDueDate = '';
        this.campaignFormOpen.set(false);
        this.saved('Campagne créée.');
      },
      error: (error: HttpErrorResponse) => this.failed(error),
    });
  }

  protected declareRemittance(): void {
    if (this.saving() || this.canManageSelectedFund() || !this.remittanceLocalFundId) return;
    const amount = Number(this.remittanceAmount.trim().replace(',', '.'));
    if (!Number.isFinite(amount) || amount <= 0) return;
    this.startSave();
    this.fundService.declareRemittance(this.remittanceLocalFundId, {
      amount,
      transferredOn: this.remittanceDate,
      method: this.remittanceMethod,
      note: this.remittanceNote.trim() || null,
    }).subscribe({
      next: (remittance) => {
        this.remittances.update((items) => [remittance, ...items]);
        this.remittanceAmount = '';
        this.remittanceNote = '';
        this.remittanceFormOpen.set(false);
        this.saved('Remise déclarée. Le montant est maintenant en cours de transfert.');
        this.loadRemittances();
      },
      error: (error: HttpErrorResponse) => this.failed(error),
    });
  }

  protected confirmRemittance(remittance: Remittance): void {
    if (this.saving() || !this.canManageSelectedFund() || remittance.status !== 'EN_ATTENTE') return;
    this.startSave();
    this.fundService.confirmRemittance(this.selectedPrimaryFundId, remittance.id).subscribe({
      next: () => {
        this.saved('Réception confirmée. Le montant est ajouté au solde central.');
        this.loadRemittances();
      },
      error: (error: HttpErrorResponse) => this.failed(error),
    });
  }

  protected beginCancelRemittance(remittanceId: string): void {
    this.cancellingRemittanceId.set(remittanceId);
    this.cancellationReason = '';
    this.errorMessage.set(null);
  }

  protected cancelRemittance(remittance: Remittance): void {
    if (this.saving() || !this.canManageSelectedFund() || !this.cancellationReason.trim()) return;
    this.startSave();
    this.fundService.cancelRemittance(
      this.selectedPrimaryFundId, remittance.id, this.cancellationReason.trim(),
    ).subscribe({
      next: () => {
        this.cancellingRemittanceId.set(null);
        this.cancellationReason = '';
        this.saved('Remise annulée. Le montant est de nouveau disponible dans la caisse locale.');
        this.loadRemittances();
      },
      error: (error: HttpErrorResponse) => this.failed(error),
    });
  }

  protected remittanceStatusLabel(status: RemittanceStatus): string {
    return ({ EN_ATTENTE: 'En cours', CONFIRMEE: 'Reçue', ANNULEE: 'Annulée' })[status];
  }

  protected campaignTypeLabel(type: CampaignType): string {
    return type === 'ANNUAL' ? 'Annuelle' : 'Événement';
  }

  private loadLocalFunds(): void {
    if (!this.selectedPrimaryFundId) {
      this.localFunds.set([]);
      return;
    }
    this.fundService.listLocalFunds(this.selectedPrimaryFundId).subscribe({
      next: (funds) => {
        this.localFunds.set(funds);
        if (!funds.some((fund) => fund.id === this.selectedTreasurerLocalFundId)) {
          this.selectedTreasurerLocalFundId = funds[0]?.id ?? '';
        }
      },
      error: (error: HttpErrorResponse) => this.errorMessage.set(this.describeError(error)),
    });
  }

  private loadMemberships(): void {
    if (!this.selectedPrimaryFundId) {
      this.memberships.set([]);
      this.canManageSelectedFund.set(false);
      return;
    }
    this.fundService.listFundAccess(this.selectedPrimaryFundId).subscribe({
      next: (memberships) => {
        this.memberships.set(memberships);
        this.canManageSelectedFund.set(true);
      },
      error: (error: HttpErrorResponse) => {
        this.memberships.set([]);
        this.canManageSelectedFund.set(false);
        if (error.status !== 403) this.errorMessage.set(this.describeError(error));
      },
    });
  }

  private loadRemittances(): void {
    if (!this.selectedPrimaryFundId) {
      this.remittances.set([]);
      this.remittanceSummary.set(null);
      return;
    }
    forkJoin({
      remittances: this.fundService.listRemittances(this.selectedPrimaryFundId),
      summary: this.fundService.remittanceSummary(this.selectedPrimaryFundId),
    }).subscribe({
      next: ({ remittances, summary }) => {
        this.remittances.set(remittances);
        this.remittanceSummary.set(summary);
        if (!summary.localFunds.some((fund) => fund.localFundId === this.remittanceLocalFundId)) {
          this.remittanceLocalFundId = summary.localFunds[0]?.localFundId ?? '';
        }
      },
      error: (error: HttpErrorResponse) => this.errorMessage.set(this.describeError(error)),
    });
  }

  private localDateToday(): string {
    const date = new Date();
    return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
  }

  private startSave(): void {
    this.saving.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  private saved(message: string): void {
    this.saving.set(false);
    this.successMessage.set(message);
  }

  private failed(error: HttpErrorResponse): void {
    this.saving.set(false);
    this.errorMessage.set(this.describeError(error));
  }

  private describeError(error: HttpErrorResponse): string {
    if (error.status === 0) return 'Le backend est injoignable sur le port 8081.';
    if (error.status === 400) return 'Certains champs sont invalides. Vérifiez le nom, l’année et le montant.';
    if (error.status === 404) return 'La caisse demandée est introuvable. Rechargez la page.';
    if (error.status === 403) return 'Votre rôle ne permet pas cette action pour cette caisse.';
    if (error.status === 409) return 'Un compte existe déjà pour cette adresse, ou cet accès ne peut pas être modifié.';
    if (error.status >= 500) return 'Le backend a rencontré une erreur. Consultez ses logs.';
    return `La requête a échoué (HTTP ${error.status}).`;
  }
}
