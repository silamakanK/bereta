import { Routes } from '@angular/router';
import { LoginComponent } from './auth/login.component';
import { RegisterComponent } from './auth/register.component';
import { ForgotPasswordComponent } from './auth/forgot-password.component';
import { ResetPasswordComponent } from './auth/reset-password.component';
import { AdminComponent } from './admin/admin.component';
import { adminGuard } from './admin/admin.guard';
import { authGuard, guestGuard } from './auth/auth.guard';

// Une fois connecté, App affiche le tableau de bord ; ces routes servent les pages publiques et /admin.
export const routes: Routes = [
  { path: '', component: LoginComponent, canActivate: [guestGuard] },
  { path: 'connexion', component: LoginComponent, canActivate: [guestGuard] },
  { path: 'inscription', component: RegisterComponent, canActivate: [guestGuard] },
  { path: 'mot-de-passe-oublie', component: ForgotPasswordComponent, canActivate: [guestGuard] },
  { path: 'reinitialiser-mot-de-passe', component: ResetPasswordComponent },
  { path: 'admin', component: AdminComponent, canActivate: [authGuard, adminGuard] },
  { path: '**', redirectTo: '' },
];
