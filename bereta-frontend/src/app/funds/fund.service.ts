import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AccountLookup,
  Campaign,
  CreateLocalFundRequest,
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

  listPrimaryFunds(associationId?: string): Observable<PrimaryFund[]> {
    return this.http.get<PrimaryFund[]>(this.primaryFundsEndpoint, scoped(associationId));
  }

  createPrimaryFund(request: CreatePrimaryFundRequest): Observable<PrimaryFund> {
    return this.http.post<PrimaryFund>(this.primaryFundsEndpoint, request);
  }

  listLocalFunds(primaryFundId: string, associationId?: string): Observable<LocalFund[]> {
    return this.http.get<LocalFund[]>(`${this.primaryFundsEndpoint}/${primaryFundId}/caisses-locales`, scoped(associationId));
  }

  createLocalFund(primaryFundId: string, request: string | CreateLocalFundRequest, associationId?: string): Observable<LocalFund> {
    const body = typeof request === 'string' ? { name: request } : request;
    return this.http.post<LocalFund>(`${this.primaryFundsEndpoint}/${primaryFundId}/caisses-locales`, body,
      scoped(associationId));
  }

  lookupAccount(primaryFundId: string, identifier: string, associationId?: string): Observable<AccountLookup> {
    return this.http.get<AccountLookup>(`${this.primaryFundsEndpoint}/${primaryFundId}/acces/compte`,
      { ...scoped(associationId), params: { identifiant: identifier } });
  }

  listCampaigns(): Observable<Campaign[]> {
    return this.http.get<Campaign[]>(this.campaignsEndpoint);
  }

  createCampaign(request: CreateCampaignRequest): Observable<Campaign> {
    return this.http.post<Campaign>(this.campaignsEndpoint, request);
  }

  /** Clôture (responsable) : plus d'inscription possible. */
  closeCampaign(id: string): Observable<Campaign> {
    return this.http.post<Campaign>(`${this.campaignsEndpoint}/${id}/cloture`, {});
  }

  listFundAccess(primaryFundId: string): Observable<FundMembership[]> {
    return this.http.get<FundMembership[]>(`${this.primaryFundsEndpoint}/${primaryFundId}/acces`);
  }

  designateTreasurer(primaryFundId: string, request: DesignateTreasurerRequest, associationId?: string):
    Observable<TreasurerDesignation> {
    return this.http.post<TreasurerDesignation>(`${this.primaryFundsEndpoint}/${primaryFundId}/acces`, request,
      scoped(associationId));
  }

  issueTemporaryPassword(primaryFundId: string, membershipId: string, associationId?: string): Observable<TemporaryPassword> {
    return this.http.post<TemporaryPassword>(
      `${this.primaryFundsEndpoint}/${primaryFundId}/acces/${membershipId}/mot-de-passe-provisoire`, {}, scoped(associationId));
  }

  deactivateFundAccess(primaryFundId: string, membershipId: string, associationId?: string): Observable<void> {
    return this.http.delete<void>(`${this.primaryFundsEndpoint}/${primaryFundId}/acces/${membershipId}`,
      scoped(associationId));
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

/** Cible explicitement une association (sinon l'intercepteur utilise l'association active). */
function scoped(associationId?: string): { headers?: HttpHeaders } {
  return associationId ? { headers: new HttpHeaders({ 'X-Association-Id': associationId }) } : {};
}
