import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Campaign,
  CreateCampaignRequest,
  CreatePrimaryFundRequest,
  LocalFund,
  PrimaryFund,
} from './fund.model';

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
}
