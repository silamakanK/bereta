import { Routes } from '@angular/router';
import { AdminShellComponent } from './admin-shell.component';

/** Espace administrateur, chargé à la demande (route /admin protégée par le rôle ADMIN). */
export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminShellComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'comptes' },
      { path: 'comptes', title: 'Comptes · Administration BERETA',
        loadComponent: () => import('./users/admin-users.component').then((m) => m.AdminUsersComponent) },
      { path: 'comptes/:id', title: 'Compte · Administration BERETA',
        loadComponent: () => import('./users/admin-user-detail.component').then((m) => m.AdminUserDetailComponent) },
      { path: 'journal', title: 'Journal · Administration BERETA',
        loadComponent: () => import('./audit/admin-audit.component').then((m) => m.AdminAuditComponent) },
      { path: '**', redirectTo: 'comptes' },
    ],
  },
];
