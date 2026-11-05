import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AdminAssociation, AdminAssociationRequest, AdminAuditEntry, AdminOverview, AdminPage, AdminUser } from './admin.model';

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/admin';

  overview(): Observable<AdminOverview> { return this.http.get<AdminOverview>(`${this.endpoint}/overview`); }
  users(query = '', page = 0): Observable<AdminPage<AdminUser>> {
    return this.http.get<AdminPage<AdminUser>>(`${this.endpoint}/users`, { params: new HttpParams().set('q', query).set('page', page) });
  }
  createUser(request: { email: string; displayName: string; password: string; role: string }): Observable<AdminUser> {
    return this.http.post<AdminUser>(`${this.endpoint}/users`, request);
  }
  updateUser(id: string, request: { email: string; displayName: string; role: string }): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${this.endpoint}/users/${id}`, request);
  }
  /** Mot de passe provisoire (affiché une seule fois), à changer à la prochaine connexion. */
  resetUserPassword(id: string): Observable<{ temporaryPassword: string }> {
    return this.http.post<{ temporaryPassword: string }>(`${this.endpoint}/users/${id}/password`, {});
  }
  setUserEnabled(id: string, enabled: boolean): Observable<AdminUser> {
    return this.http.patch<AdminUser>(`${this.endpoint}/users/${id}/activation`, { enabled });
  }
  associations(query = '', page = 0): Observable<AdminPage<AdminAssociation>> {
    return this.http.get<AdminPage<AdminAssociation>>(`${this.endpoint}/associations`, { params: new HttpParams().set('q', query).set('page', page) });
  }
  createAssociation(request: AdminAssociationRequest): Observable<AdminAssociation> {
    return this.http.post<AdminAssociation>(`${this.endpoint}/associations`, request);
  }
  updateAssociation(id: string, request: Omit<AdminAssociationRequest, 'responsibleEmail'> & { status: 'ACTIVE' | 'ARCHIVED' }): Observable<AdminAssociation> {
    return this.http.put<AdminAssociation>(`${this.endpoint}/associations/${id}`, request);
  }
  archiveAssociation(id: string): Observable<AdminAssociation> {
    return this.http.post<AdminAssociation>(`${this.endpoint}/associations/${id}/archive`, {});
  }
  addResponsible(id: string, email: string): Observable<unknown> {
    return this.http.post(`${this.endpoint}/associations/${id}/responsables`, { email });
  }
  audit(page = 0): Observable<AdminPage<AdminAuditEntry>> {
    return this.http.get<AdminPage<AdminAuditEntry>>(`${this.endpoint}/audit`, { params: new HttpParams().set('page', page) });
  }
}
