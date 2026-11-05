import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { tap } from 'rxjs';

/**
 * Rafraîchissement unique des données : toute écriture réussie sur l'API (création, modification,
 * archivage, suppression) incrémente `version`. Les écrans qui affichent ces données
 * (tableau de bord, « Mes associations », compteurs) rechargent quand `version` change.
 */
@Injectable({ providedIn: 'root' })
export class DataRefreshService {
  readonly version = signal(0);

  notify(): void {
    this.version.update((value) => value + 1);
  }
}

const WRITE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export const dataRefreshInterceptor: HttpInterceptorFn = (request, next) => {
  const refresh = inject(DataRefreshService);
  const tracked = WRITE_METHODS.has(request.method)
    && request.url.startsWith('/api/') && !request.url.startsWith('/api/auth/');
  if (!tracked) return next(request);
  return next(request).pipe(tap((event) => {
    if (event instanceof HttpResponse && event.ok) refresh.notify();
  }));
};
