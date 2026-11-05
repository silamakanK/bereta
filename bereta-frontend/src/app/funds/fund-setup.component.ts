import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { Campaign, CampaignType, PrimaryFund } from './fund.model';
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
  /** Responsable de l'association : peut créer la caisse principale si elle manque (anciennes associations). */
  @Input() manager = false;

  private readonly fundService = inject(FundService);

  protected readonly primaryFunds = signal<PrimaryFund[]>([]);
  protected readonly campaigns = signal<Campaign[]>([]);
  protected readonly remittances = signal<Remittance[]>([]);
  protected readonly remittanceSummary = signal<CashRemittanceSummary | null>(null);
  protected readonly canManageSelectedFund = signal(false);
  protected readonly isLoading = signal(true);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly campaignFormOpen = signal(false);
  protected readonly remittanceFormOpen = signal(false);
  protected readonly cancellingRemittanceId = signal<string | null>(null);

  protected primaryObjective = '';
  protected selectedPrimaryFundId = '';
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

  protected createPrimaryFund(): void {
    if (this.saving()) return;
    this.startSave();
    this.fundService.createPrimaryFund({
      name: 'Caisse principale',
      objective: this.primaryObjective.trim(),
      description: null,
    }).subscribe({
      next: (fund) => {
        this.primaryFunds.set([fund]);
        this.selectedPrimaryFundId = fund.id;
        this.primaryObjective = '';
        this.loadMemberships();
        this.loadRemittances();
        this.saved('Caisse principale créée.');
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
        this.saved(`Campagne créée, ${campaign.enrolledCount} membre${campaign.enrolledCount > 1 ? 's' : ''} inscrit${campaign.enrolledCount > 1 ? 's' : ''}.`);
      },
      error: (error: HttpErrorResponse) => this.failed(error),
    });
  }

  /** Clôture : plus aucune inscription ; les restes dus peuvent encore être encaissés. */
  protected closeCampaign(campaign: Campaign): void {
    if (this.saving() || !this.canManageSelectedFund() || campaign.status !== 'OUVERTE') return;
    if (!confirm(`Clôturer « ${campaign.name} » ? Plus aucun membre ne pourra y être inscrit.`)) return;
    this.startSave();
    this.fundService.closeCampaign(campaign.id).subscribe({
      next: (closed) => {
        this.campaigns.update((items) => items.map((item) => item.id === closed.id ? closed : item));
        this.saved(`Campagne « ${closed.name} » clôturée.`);
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

  private loadMemberships(): void {
    if (!this.selectedPrimaryFundId) {
      this.canManageSelectedFund.set(false);
      return;
    }
    this.fundService.listFundAccess(this.selectedPrimaryFundId).subscribe({
      next: () => {
        this.canManageSelectedFund.set(true);
      },
      error: (error: HttpErrorResponse) => {
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
