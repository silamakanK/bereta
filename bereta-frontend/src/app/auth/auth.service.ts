import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, map, switchMap, tap, throwError } from 'rxjs';
import { AcceptInvitationRequest, AuthMessage, AuthUser, CompletePasswordResetRequest, CsrfTokenResponse, PasswordResetRequest, RegisterRequest } from './auth.model';

export class RegisteredAccountLoginError extends Error {
  constructor(override readonly cause: HttpErrorResponse) {
    super('Le compte a été créé, mais la connexion automatique a échoué.');
    this.name = 'RegisteredAccountLoginError';
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/auth';
  readonly currentUser = signal<AuthUser | null>(null);

  me(): Observable<AuthUser> {
    return this.http.get<AuthUser>(`${this.endpoint}/me`).pipe(tap((user) => this.currentUser.set(user)));
  }

  login(email: string, password: string): Observable<AuthUser> {
    const form = new HttpParams().set('username', email).set('password', password);
    return this.csrf().pipe(
      switchMap(() => this.http.post<AuthUser>(`${this.endpoint}/login`, form)),
      switchMap((user) => this.csrf().pipe(map(() => user))),
      tap((user) => this.currentUser.set(user)),
    );
  }

  acceptInvitation(request: AcceptInvitationRequest): Observable<AuthUser> {
    return this.csrf().pipe(
      switchMap(() => this.http.post<AuthUser>(`${this.endpoint}/invitations/accept`, request)),
      switchMap(() => this.login(request.email, request.password)),
    );
  }

  register(request: RegisterRequest): Observable<AuthUser> {
    return this.csrf().pipe(
      switchMap(() => this.http.post(`${this.endpoint}/register`, request)),
      switchMap(() => this.login(request.email, request.password).pipe(
        catchError((error: HttpErrorResponse) => throwError(() => new RegisteredAccountLoginError(error))),
      )),
    );
  }

  requestPasswordReset(request: PasswordResetRequest): Observable<AuthMessage> {
    return this.csrf().pipe(
      switchMap(() => this.http.post<AuthMessage>(`${this.endpoint}/password/forgot`, request)),
    );
  }

  resetPassword(request: CompletePasswordResetRequest): Observable<AuthMessage> {
    return this.csrf().pipe(
      switchMap(() => this.http.post<AuthMessage>(`${this.endpoint}/password/reset`, request)),
    );
  }

  logout(): Observable<void> {
    return this.csrf().pipe(
      switchMap(() => this.http.post<void>(`${this.endpoint}/logout`, {})),
      switchMap(() => this.csrf().pipe(map(() => undefined))),
      tap(() => this.currentUser.set(null)),
    );
  }

  private csrf(): Observable<CsrfTokenResponse> {
    return this.http.get<CsrfTokenResponse>(`${this.endpoint}/csrf`);
  }
}
