import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Routes publiques ou à cookie : pas de jeton, pas de nouvel essai sur 401. */
const NO_BEARER = /^\/api\/auth\/(login|register|otp|refresh|logout$|password\/(forgot|reset))/;

/**
 * Ajoute « Authorization: Bearer … » aux appels /api et, sur une 401 due à un access token
 * expiré, le renouvelle une seule fois via le cookie puis rejoue la requête. Si le
 * renouvellement échoue, la session est fermée et l'utilisateur renvoyé à la connexion.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/')) return next(request);
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token();
  const anonymous = NO_BEARER.test(request.url);
  const withToken = (value: string | null) =>
    value && !anonymous ? request.clone({ setHeaders: { Authorization: `Bearer ${value}` } }) : request;

  return next(withToken(token)).pipe(catchError((error: unknown) => {
    if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !token || anonymous) {
      return throwError(() => error);
    }
    return auth.refresh().pipe(
      switchMap((session) => next(withToken(session.accessToken))),
      catchError((refreshError: unknown) => {
        if (refreshError instanceof HttpErrorResponse && refreshError.url?.includes('/api/auth/refresh')) {
          void router.navigateByUrl('/connexion');
        }
        return throwError(() => (refreshError instanceof HttpErrorResponse ? refreshError : error));
      }),
    );
  }));
};
