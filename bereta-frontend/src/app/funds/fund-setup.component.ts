import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { Campaign, CampaignType, LocalFund, PrimaryFund } from './fund.model';
import { FundService } from './fund.service';

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
  protected readonly isLoading = signal(true);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly primaryFormOpen = signal(true);
  protected readonly localFormOpen = signal(false);
  protected readonly campaignFormOpen = signal(false);

  protected primaryName = '';
  protected primaryObjective = '';
  protected primaryDescription = '';
  protected selectedPrimaryFundId = '';
  protected localFundName = '';
  protected campaignName = '';
  protected campaignType: CampaignType = 'ANNUAL';
  protected campaignYear = new Date().getFullYear();
  protected campaignAmount = '';
  protected campaignDueDate = '';

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
    this.loadLocalFunds();
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
        this.saved('Caisse principale créée.');
      },
      error: (error: HttpErrorResponse) => this.failed(error),
    });
  }

  protected createLocalFund(): void {
    if (this.saving() || !this.selectedPrimaryFundId) return;
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

  protected createCampaign(): void {
    if (this.saving() || !this.selectedPrimaryFundId) return;
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

  protected campaignTypeLabel(type: CampaignType): string {
    return type === 'ANNUAL' ? 'Annuelle' : 'Événement';
  }

  private loadLocalFunds(): void {
    if (!this.selectedPrimaryFundId) {
      this.localFunds.set([]);
      return;
    }
    this.fundService.listLocalFunds(this.selectedPrimaryFundId).subscribe({
      next: (funds) => this.localFunds.set(funds),
      error: (error: HttpErrorResponse) => this.errorMessage.set(this.describeError(error)),
    });
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
    if (error.status >= 500) return 'Le backend a rencontré une erreur. Consultez ses logs.';
    return `La requête a échoué (HTTP ${error.status}).`;
  }
}
