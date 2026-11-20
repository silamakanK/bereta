import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AdminAuditEntry, AdminAuditFilters, AdminPage, AdminPasswordReset, AdminUserActionResult, AdminUserDetail,
  AdminUserFilters, AdminUserSummary,
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

  auditLogs(filters: AdminAuditFilters): Observable<AdminPage<AdminAuditEntry>> {
    return this.http.get<AdminPage<AdminAuditEntry>>(`${this.endpoint}/audit-logs`, { params: queryParams(filters) });
  }

  exportAuditLogs(filters: AdminAuditFilters): Observable<Blob> {
    // L'export reprend les filtres, sans la pagination.
    const params = queryParams({ ...filters, page: '', size: '' });
    return this.http.get(`${this.endpoint}/audit-logs/export`, { params, responseType: 'blob' });
  }
}
