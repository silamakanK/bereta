import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from './auth.service';
import { PasswordStrengthComponent } from './password-strength.component';
import { passwordValidationMessage } from './password-rules';
import { apiErrorMessage } from '../shared/api-error';

/** Maquette « premi_re_connexion_tr_sorier_bereta » : remplacer le mot de passe provisoire. */
@Component({
  selector: 'app-first-login',
  standalone: true,
  imports: [FormsModule, PasswordStrengthComponent],
  styleUrl: './login.component.scss',
  template: `
    <main class="login-page">
      <section class="login-card" aria-labelledby="first-login-title">
        <p class="login-eyebrow">ESPACE RESPONSABLE DE CAISSE</p>
        <h1 id="first-login-title">Bienvenue sur BERETA</h1>
        <p class="login-intro">Bonjour {{ auth.currentUser()?.displayName }}, remplacez le mot de passe provisoire
          fourni par votre responsable par un mot de passe que vous seul connaissez.</p>
        @if (errorMessage()) { <p class="login-error" role="alert">{{ errorMessage() }}</p> }
        <form novalidate (ngSubmit)="submit()">
          <label for="first-current">Mot de passe provisoire reçu</label>
          <input id="first-current" name="currentPassword" type="password" [(ngModel)]="currentPassword"
                 autocomplete="current-password" required autofocus />
          <label for="first-new">Nouveau mot de passe personnel</label>
          <div class="login-password-control"><input id="first-new" name="newPassword" [type]="show() ? 'text' : 'password'"
                 [(ngModel)]="newPassword" autocomplete="new-password" required maxlength="72" /><button class="password-toggle"
                 type="button" (click)="show.set(!show())" [attr.aria-pressed]="show()">{{ show() ? 'Masquer' : 'Afficher' }}</button></div>
          <app-password-strength [password]="newPassword" />
          <label for="first-confirm">Confirmer le nouveau mot de passe</label>
          <input id="first-confirm" name="confirmPassword" [type]="show() ? 'text' : 'password'" [(ngModel)]="confirmPassword"
                 autocomplete="new-password" required />
          <p class="login-help"><strong>Rappel de responsabilité :</strong> ne partagez jamais votre mot de passe.
            Chaque encaissement sera enregistré sous votre nom.</p>
          <button class="login-submit" type="submit" [disabled]="isSubmitting()">
            {{ isSubmitting() ? 'Enregistrement…' : 'Enregistrer mon mot de passe et continuer' }}</button>
        </form>
        <button class="login-secondary" type="button" (click)="logout()">Se déconnecter</button>
      </section>
    </main>
  `,
})
export class FirstLoginComponent {
  protected readonly auth = inject(AuthService);
  protected currentPassword = '';
  protected newPassword = '';
  protected confirmPassword = '';
  protected readonly show = signal(false);
  protected readonly isSubmitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected submit(): void {
    if (this.isSubmitting()) return;
    const error = !this.currentPassword
      ? 'Saisissez le mot de passe provisoire reçu.'
      : passwordValidationMessage(this.newPassword, this.confirmPassword);
    if (error) {
      this.errorMessage.set(error);
      return;
    }
    this.isSubmitting.set(true);
    this.errorMessage.set(null);
    this.auth.changePassword(this.currentPassword, this.newPassword).subscribe({
      next: () => this.isSubmitting.set(false),
      error: (failure: unknown) => {
        this.isSubmitting.set(false);
        this.errorMessage.set(apiErrorMessage(failure, 'Le mot de passe n’a pas pu être modifié. Réessayez.'));
      },
    });
  }

  protected logout(): void {
    this.auth.logout().subscribe({ error: () => undefined });
  }
}
