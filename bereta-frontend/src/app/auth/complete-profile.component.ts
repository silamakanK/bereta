import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { apiErrorMessage } from '../shared/api-error';

/**
 * « Complète ton profil », proposé après l'inscription (téléphone + mot de passe seulement).
 * Tous les champs sont facultatifs et l'écran peut être passé.
 */
@Component({
  selector: 'app-complete-profile',
  standalone: true,
  imports: [FormsModule],
  styleUrl: './login.component.scss',
  template: `
    <main class="login-page">
      <section class="login-card" aria-labelledby="profile-title">
        <h1 id="profile-title">Complétez votre profil</h1>
        <p class="login-intro">Votre compte est créé. Ces informations sont facultatives.</p>
        @if (errorMessage()) { <p class="login-error" role="alert">{{ errorMessage() }}</p> }
        <form novalidate (ngSubmit)="save()">
          <label for="profile-first-name">Prénom</label>
          <input id="profile-first-name" name="firstName" [(ngModel)]="firstName" autocomplete="given-name" maxlength="100" />

          <label for="profile-last-name">Nom</label>
          <input id="profile-last-name" name="lastName" [(ngModel)]="lastName" autocomplete="family-name" maxlength="100" />

          <label for="profile-email">E-mail</label>
          <input id="profile-email" name="email" type="email" [(ngModel)]="email" autocomplete="email" maxlength="320"
                 placeholder="nom@exemple.org" [attr.aria-invalid]="emailInvalid()" aria-describedby="profile-email-help" />
          <span class="field-help" id="profile-email-help">Vous pourrez aussi vous connecter avec.</span>
          @if (emailInvalid()) { <span class="field-error">L’adresse e-mail n’est pas valide.</span> }

          <button class="login-submit" type="submit" [disabled]="busy()">{{ busy() ? 'Enregistrement…' : 'Enregistrer' }}</button>
        </form>
        <button class="login-secondary" type="button" [disabled]="busy()" (click)="skip()">Plus tard</button>
      </section>
    </main>
  `,
})
export class CompleteProfileComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected firstName = '';
  protected lastName = '';
  protected email = this.auth.currentUser()?.email ?? '';
  protected readonly busy = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly attempted = signal(false);

  constructor() {
    const [first = '', ...rest] = (this.auth.currentUser()?.displayName ?? '').split(' ');
    this.firstName = first;
    this.lastName = rest.join(' ');
  }

  protected emailInvalid(): boolean {
    const value = this.email.trim();
    return this.attempted() && !!value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
  }

  protected save(): void {
    if (this.busy()) return;
    this.attempted.set(true);
    if (this.emailInvalid()) return;
    const before = this.auth.currentUser();
    const email = this.email.trim();
    this.busy.set(true);
    this.errorMessage.set(null);
    this.auth.updateProfile({
      firstName: this.firstName.trim(),
      lastName: this.lastName.trim(),
      // Ne pas effacer un e-mail existant si le champ est laissé vide.
      email: email || (before?.email ? null : ''),
    }).subscribe({
      next: () => {
        this.busy.set(false);
        void this.router.navigateByUrl('/');
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.errorMessage.set(apiErrorMessage(error, 'Le profil n’a pas pu être enregistré. Réessayez.'));
      },
    });
  }

  protected skip(): void {
    void this.router.navigateByUrl('/');
  }
}
