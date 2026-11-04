import { HttpErrorResponse } from '@angular/common/http';

/**
 * Message à afficher pour une erreur d'API : le texte en français renvoyé par le backend
 * (ProblemDetail « detail ») quand il existe, sinon le message de repli fourni.
 */
export function apiErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse)) return fallback;
  if (error.status === 0) return 'Le serveur est injoignable. Vérifiez votre connexion puis réessayez.';
  const detail = error.error?.detail;
  if (typeof detail === 'string' && detail && detail !== 'Invalid request content.') return detail;
  if (error.status === 429) return 'Trop de tentatives. Patientez un moment avant de réessayer.';
  return fallback;
}

export function apiErrorCode(error: unknown): string | null {
  return error instanceof HttpErrorResponse && typeof error.error?.code === 'string' ? error.error.code : null;
}
