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
      { path: 'associations', title: 'Associations · Administration BERETA',
        loadComponent: () => import('./associations/admin-associations.component').then((m) => m.AdminAssociationsComponent) },
      { path: 'associations/:id', title: 'Association · Administration BERETA',
        loadComponent: () => import('./associations/admin-association-detail.component')
          .then((m) => m.AdminAssociationDetailComponent) },
      { path: 'caisses', title: 'Caisses · Administration BERETA',
        loadComponent: () => import('./funds/admin-funds.component').then((m) => m.AdminFundsComponent) },
      { path: 'caisses/:id', title: 'Caisse · Administration BERETA',
        loadComponent: () => import('./funds/admin-fund-detail.component').then((m) => m.AdminFundDetailComponent) },
      { path: 'journal', title: 'Journal · Administration BERETA',
        loadComponent: () => import('./audit/admin-audit.component').then((m) => m.AdminAuditComponent) },
      { path: '**', redirectTo: 'comptes' },
    ],
  },
];
