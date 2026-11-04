import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, of } from 'rxjs';
import { AuthService } from './auth.service';

/** Exige une session ; au premier chargement, tente de la restaurer via le cookie. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.currentUser()) return true;
  return auth.restoreSession().pipe(map((user) => (user ? true : router.createUrlTree(['/connexion']))));
};

/** Pages publiques (connexion, inscription…) : inutiles une fois connecté. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return of(auth.currentUser() ? inject(Router).createUrlTree(['/']) : true);
};
