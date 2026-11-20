import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AdminAssociationDetail, AdminAssociationFilters, AdminAssociationSummary, AdminAuditEntry, AdminAuditFilters,
  AdminFundDetail, AdminFundFilters, AdminFundSummary, AdminPage, AdminPasswordReset, AdminUserActionResult,
  AdminUserDetail, AdminUserFilters, AdminUserSummary,
} from './admin.model';

/** Paramètres de requête sans les valeurs vides. */
export function queryParams(values: object): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== '' && value !== null && value !== undefined) params = params.set(key, String(value));
  }
  return params;
}

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/admin';

  users(filters: AdminUserFilters): Observable<AdminPage<AdminUserSummary>> {
    return this.http.get<AdminPage<AdminUserSummary>>(`${this.endpoint}/users`, { params: queryParams(filters) });
  }

  user(id: string): Observable<AdminUserDetail> {
    return this.http.get<AdminUserDetail>(`${this.endpoint}/users/${id}`);
  }

  disableUser(id: string, reason: string): Observable<AdminUserActionResult> {
    return this.http.post<AdminUserActionResult>(`${this.endpoint}/users/${id}/disable`, { reason });
  }

  enableUser(id: string, reason: string): Observable<AdminUserActionResult> {
    return this.http.post<AdminUserActionResult>(`${this.endpoint}/users/${id}/enable`, { reason });
  }

  resetPassword(id: string, reason: string): Observable<AdminPasswordReset> {
    return this.http.post<AdminPasswordReset>(`${this.endpoint}/users/${id}/reset-password`, { reason });
  }

  associations(filters: AdminAssociationFilters): Observable<AdminPage<AdminAssociationSummary>> {
    return this.http.get<AdminPage<AdminAssociationSummary>>(`${this.endpoint}/associations`, { params: queryParams(filters) });
  }

  association(id: string): Observable<AdminAssociationDetail> {
    return this.http.get<AdminAssociationDetail>(`${this.endpoint}/associations/${id}`);
  }

  /** Archivage ou restauration d'une association, avec motif. */
  setAssociationArchived(id: string, archived: boolean, reason: string): Observable<AdminAssociationDetail> {
    const action = archived ? 'archive' : 'restore';
    return this.http.post<AdminAssociationDetail>(`${this.endpoint}/associations/${id}/${action}`, { reason });
  }

  transferAssociation(id: string, fromUserId: string, toUserId: string, reason: string): Observable<AdminAssociationDetail> {
    return this.http.post<AdminAssociationDetail>(`${this.endpoint}/associations/${id}/transfer`, { fromUserId, toUserId, reason });
  }

  funds(filters: AdminFundFilters): Observable<AdminPage<AdminFundSummary>> {
    return this.http.get<AdminPage<AdminFundSummary>>(`${this.endpoint}/funds`, { params: queryParams(filters) });
  }

  fund(id: string): Observable<AdminFundDetail> {
    return this.http.get<AdminFundDetail>(`${this.endpoint}/funds/${id}`);
  }

  setFundArchived(id: string, archived: boolean, reason: string): Observable<AdminFundDetail> {
    const action = archived ? 'archive' : 'restore';
    return this.http.post<AdminFundDetail>(`${this.endpoint}/funds/${id}/${action}`, { reason });
  }

  /** replaceUserId vide : le trésorier est ajouté sans en remplacer un autre. */
  reassignTreasurer(id: string, toUserId: string, replaceUserId: string | null, reason: string): Observable<AdminFundDetail> {
    return this.http.post<AdminFundDetail>(`${this.endpoint}/funds/${id}/reassign-treasurer`, { toUserId, replaceUserId, reason });
  }

  auditLogs(filters: AdminAuditFilters): Observable<AdminPage<AdminAuditEntry>> {
    return this.http.get<AdminPage<AdminAuditEntry>>(`${this.endpoint}/audit-logs`, { params: queryParams(filters) });
  }

  exportAuditLogs(filters: AdminAuditFilters): Observable<Blob> {
    // L'export reprend les filtres, sans la pagination.
    const params = queryParams({ ...filters, page: '', size: '' });
    return this.http.get(`${this.endpoint}/audit-logs/export`, { params, responseType: 'blob' });
  }
}
