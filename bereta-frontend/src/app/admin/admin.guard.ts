import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { map } from 'rxjs';
import { AuthService } from '../auth/auth.service';

/**
 * Réservé au rôle ADMIN. Au rechargement d'une page /admin, la session est restaurée par le même
 * rafraîchissement partagé que authGuard (un appel concurrent sans jeton échouait en 401 et figeait la page).
 */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const current = auth.currentUser();
  if (current) return current.role === 'ADMIN' ? true : router.createUrlTree(['/']);
  return auth.restoreSession().pipe(map((user) => {
    if (!user) return router.createUrlTree(['/connexion']);
    return user.role === 'ADMIN' ? true : router.createUrlTree(['/']);
  }));
};
