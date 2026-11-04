import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, tap } from 'rxjs';
import {
  AuthMessage, AuthUser, CodeSent, ForgotPasswordResponse, LoginStep, RegisterRequest, RegistrationResponse,
  TokenResponse,
} from './auth.model';

/** En-tête exigé par le backend sur les routes qui lisent le cookie de rafraîchissement. */
const REFRESH_HEADERS = new HttpHeaders({ 'X-Bereta-Refresh': '1' });

/**
 * L'access token (15 min) reste en mémoire, jamais dans localStorage. Le refresh token est un
 * cookie HttpOnly que le navigateur envoie seul à /api/auth : au rechargement de la page,
 * restoreSession() redemande un access token.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/auth';
  private accessToken: string | null = null;
  private refreshInFlight: Observable<TokenResponse> | null = null;

  readonly currentUser = signal<AuthUser | null>(null);
  /** Après l'inscription : proposer de vérifier l'e-mail saisi. */
  readonly pendingEmailVerification = signal(false);

  token(): string | null {
    return this.accessToken;
  }

  me(): Observable<AuthUser> {
    return this.http.get<AuthUser>(`${this.endpoint}/me`).pipe(tap((user) => this.currentUser.set(user)));
  }

  restoreSession(): Observable<AuthUser | null> {
    return this.refresh().pipe(map((response) => response.user), catchError(() => of(null)));
  }

  /** Un seul rafraîchissement à la fois, partagé par toutes les requêtes en 401. */
  refresh(): Observable<TokenResponse> {
    this.refreshInFlight ??= this.http
      .post<TokenResponse>(`${this.endpoint}/refresh`, {}, { headers: REFRESH_HEADERS, withCredentials: true })
      .pipe(
        tap({ next: (response) => this.apply(response), error: () => this.clearSession() }),
        finalize(() => (this.refreshInFlight = null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    return this.refreshInFlight;
  }

  login(identifier: string, password: string): Observable<LoginStep> {
    return this.http.post<LoginStep>(`${this.endpoint}/login`, { identifier, password });
  }

  enrollPhone(enrollmentToken: string, phone: string): Observable<LoginStep> {
    return this.http.post<LoginStep>(`${this.endpoint}/login/phone`, { enrollmentToken, phone });
  }

  verifyLogin(challengeId: string, code: string): Observable<AuthUser> {
    return this.http.post<TokenResponse>(`${this.endpoint}/login/verify`, { challengeId, code })
      .pipe(tap((response) => this.apply(response)), map((response) => response.user));
  }

  register(request: RegisterRequest): Observable<RegistrationResponse> {
    return this.http.post<RegistrationResponse>(`${this.endpoint}/register`, request);
  }

  verifyRegistration(challengeId: string, code: string, emailToVerify: boolean): Observable<AuthUser> {
    return this.http.post<TokenResponse>(`${this.endpoint}/register/verify`, { challengeId, code }).pipe(
      tap((response) => {
        this.pendingEmailVerification.set(emailToVerify && !!response.user.email && !response.user.emailVerified);
        this.apply(response);
      }),
      map((response) => response.user),
    );
  }

  resendCode(challengeId: string): Observable<CodeSent> {
    return this.http.post<CodeSent>(`${this.endpoint}/otp/resend`, { challengeId });
  }

  forgotPassword(identifier: string): Observable<ForgotPasswordResponse> {
    return this.http.post<ForgotPasswordResponse>(`${this.endpoint}/password/forgot`, { identifier });
  }

  resetPasswordWithCode(challengeId: string, code: string, password: string): Observable<AuthMessage> {
    return this.http.post<AuthMessage>(`${this.endpoint}/password/reset/code`, { challengeId, code, password });
  }

  resetPasswordWithLink(token: string, password: string): Observable<AuthMessage> {
    return this.http.post<AuthMessage>(`${this.endpoint}/password/reset`, { token, password });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<AuthUser> {
    return this.http.post<TokenResponse>(`${this.endpoint}/password/change`, { currentPassword, newPassword })
      .pipe(tap((response) => this.apply(response)), map((response) => response.user));
  }

  sendEmailCode(): Observable<CodeSent> {
    return this.http.post<CodeSent>(`${this.endpoint}/email/send-code`, {});
  }

  verifyEmail(code: string): Observable<AuthUser> {
    return this.http.post<AuthUser>(`${this.endpoint}/email/verify`, { code }).pipe(tap((user) => {
      this.currentUser.set(user);
      this.pendingEmailVerification.set(false);
    }));
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.endpoint}/logout`, {}, { headers: REFRESH_HEADERS, withCredentials: true })
      .pipe(finalize(() => this.clearSession()));
  }

  logoutEverywhere(): Observable<void> {
    return this.http.post<void>(`${this.endpoint}/logout-all`, {}, { withCredentials: true })
      .pipe(finalize(() => this.clearSession()));
  }

  clearSession(): void {
    this.accessToken = null;
    this.currentUser.set(null);
    this.pendingEmailVerification.set(false);
  }

  private apply(response: TokenResponse): void {
    this.accessToken = response.accessToken;
    this.currentUser.set(response.user);
  }
}
