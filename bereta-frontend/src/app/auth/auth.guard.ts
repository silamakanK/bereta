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

/**
 * Pages publiques (connexion, inscription…) : inutiles une fois connecté, on renvoie vers « / »
 * (où App affiche le tableau de bord). « / » lui-même est laissé passer : rediriger « / » vers
 * « / » ferait boucler le routeur à l'infini et figerait la page.
 */
export const guestGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (!auth.currentUser() || state.url === '/' || state.url.startsWith('/?') || state.url.startsWith('/#')) {
    return of(true);
  }
  return of(inject(Router).createUrlTree(['/']));
};
