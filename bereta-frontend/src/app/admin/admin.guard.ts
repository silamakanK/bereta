import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { catchError, map, of } from 'rxjs';
import { AuthService } from '../auth/auth.service';

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const current = auth.currentUser();
  if (current) return current.role === 'ADMIN' ? true : router.createUrlTree(['/']);
  return auth.me().pipe(
    map((user) => user.role === 'ADMIN' ? true : router.createUrlTree(['/'])),
    catchError(() => of(router.createUrlTree(['/connexion']))),
  );
};
