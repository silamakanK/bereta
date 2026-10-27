import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, switchMap } from 'rxjs';
import { AuthUser, CsrfTokenResponse } from './auth.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/auth';

  me(): Observable<AuthUser> {
    return this.http.get<AuthUser>(`${this.endpoint}/me`);
  }

  login(email: string, password: string): Observable<AuthUser> {
    const form = new HttpParams().set('username', email).set('password', password);
    return this.csrf().pipe(
      switchMap(() => this.http.post<AuthUser>(`${this.endpoint}/login`, form)),
      switchMap((user) => this.csrf().pipe(map(() => user))),
    );
  }

  logout(): Observable<void> {
    return this.csrf().pipe(
      switchMap(() => this.http.post<void>(`${this.endpoint}/logout`, {})),
      switchMap(() => this.csrf().pipe(map(() => undefined))),
    );
  }

  private csrf(): Observable<CsrfTokenResponse> {
    return this.http.get<CsrfTokenResponse>(`${this.endpoint}/csrf`);
  }
}
