import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Campaign,
  CreateCampaignRequest,
  CreatePrimaryFundRequest,
  FundMembership,
  LocalFund,
  PrimaryFund,
  DesignateTreasurerRequest,
  TemporaryPassword,
  TreasurerDesignation,
} from './fund.model';
import { CashRemittanceSummary, CreateRemittanceRequest, Remittance } from './remittance.model';

@Injectable({ providedIn: 'root' })
export class FundService {
  private readonly http = inject(HttpClient);
  private readonly primaryFundsEndpoint = '/api/caisses-principales';
  private readonly campaignsEndpoint = '/api/campagnes';

  listPrimaryFunds(): Observable<PrimaryFund[]> {
    return this.http.get<PrimaryFund[]>(this.primaryFundsEndpoint);
  }

  createPrimaryFund(request: CreatePrimaryFundRequest): Observable<PrimaryFund> {
    return this.http.post<PrimaryFund>(this.primaryFundsEndpoint, request);
  }

  listLocalFunds(primaryFundId: string): Observable<LocalFund[]> {
    return this.http.get<LocalFund[]>(`${this.primaryFundsEndpoint}/${primaryFundId}/caisses-locales`);
  }

  createLocalFund(primaryFundId: string, name: string): Observable<LocalFund> {
    return this.http.post<LocalFund>(
      `${this.primaryFundsEndpoint}/${primaryFundId}/caisses-locales`,
      { name },
    );
  }

  listCampaigns(): Observable<Campaign[]> {
    return this.http.get<Campaign[]>(this.campaignsEndpoint);
  }

  createCampaign(request: CreateCampaignRequest): Observable<Campaign> {
    return this.http.post<Campaign>(this.campaignsEndpoint, request);
  }

  listFundAccess(primaryFundId: string): Observable<FundMembership[]> {
    return this.http.get<FundMembership[]>(`${this.primaryFundsEndpoint}/${primaryFundId}/acces`);
  }

  designateTreasurer(primaryFundId: string, request: DesignateTreasurerRequest): Observable<TreasurerDesignation> {
    return this.http.post<TreasurerDesignation>(`${this.primaryFundsEndpoint}/${primaryFundId}/acces`, request);
  }

  issueTemporaryPassword(primaryFundId: string, membershipId: string): Observable<TemporaryPassword> {
    return this.http.post<TemporaryPassword>(
      `${this.primaryFundsEndpoint}/${primaryFundId}/acces/${membershipId}/mot-de-passe-provisoire`, {});
  }

  deactivateFundAccess(primaryFundId: string, membershipId: string): Observable<void> {
    return this.http.delete<void>(`${this.primaryFundsEndpoint}/${primaryFundId}/acces/${membershipId}`);
  }

  listRemittances(primaryFundId: string): Observable<Remittance[]> {
    return this.http.get<Remittance[]>(`${this.primaryFundsEndpoint}/${primaryFundId}/remises`);
  }

  remittanceSummary(primaryFundId: string): Observable<CashRemittanceSummary> {
    return this.http.get<CashRemittanceSummary>(`${this.primaryFundsEndpoint}/${primaryFundId}/remises/synthese`);
  }

  declareRemittance(localFundId: string, request: CreateRemittanceRequest): Observable<Remittance> {
    return this.http.post<Remittance>(`/api/caisses-locales/${localFundId}/remises`, request);
  }

  confirmRemittance(primaryFundId: string, remittanceId: string): Observable<Remittance> {
    return this.http.post<Remittance>(`${this.primaryFundsEndpoint}/${primaryFundId}/remises/${remittanceId}/confirmer`, {});
  }

  cancelRemittance(primaryFundId: string, remittanceId: string, reason: string): Observable<Remittance> {
    return this.http.post<Remittance>(`${this.primaryFundsEndpoint}/${primaryFundId}/remises/${remittanceId}/annuler`, { reason });
  }
}
