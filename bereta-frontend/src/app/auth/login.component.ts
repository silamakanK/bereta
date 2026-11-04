import { Component, EventEmitter, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthUser } from './auth.model';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  @Output() readonly signedIn = new EventEmitter<AuthUser>();

  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly location = inject(Location);
  private readonly router = inject(Router);
  protected readonly invitationToken = this.route.snapshot.queryParamMap.get('invitation');
  protected email = this.route.snapshot.queryParamMap.get('email') ?? '';
  protected displayName = '';
  protected password = '';
  protected confirmPassword = '';
  protected readonly isSubmitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly showPassword = signal(false);
  protected readonly showConfirmPassword = signal(false);
  protected readonly attemptedSubmit = signal(false);

  protected submit(): void {
    if (this.isSubmitting()) return;
    this.attemptedSubmit.set(true);
    if (!this.email.trim() || !/^\S+@\S+\.\S+$/.test(this.email.trim())) {
      this.errorMessage.set('Saisissez une adresse e-mail valide.');
      return;
    }
    if (!this.password) {
      this.errorMessage.set('Saisissez votre mot de passe.');
      return;
    }
    if (this.invitationToken && this.password !== this.confirmPassword) {
      this.errorMessage.set('Les deux mots de passe ne correspondent pas.');
      return;
    }
    if (this.invitationToken && (this.displayName.trim().length < 2 || this.password.length < 6)) {
      this.errorMessage.set('Renseignez votre nom et choisissez un mot de passe de 6 caractères minimum.');
      return;
    }
    this.isSubmitting.set(true);
    this.errorMessage.set(null);
    const operation = this.invitationToken
      ? this.authService.acceptInvitation({
          token: this.invitationToken,
          email: this.email.trim(),
          displayName: this.displayName.trim(),
          password: this.password,
        })
      : this.authService.login(this.email.trim(), this.password);
    operation.subscribe({
      next: (user) => {
        this.password = '';
        this.isSubmitting.set(false);
        if (this.invitationToken) this.location.replaceState('/');
        this.signedIn.emit(user);
        void this.router.navigateByUrl(user.role === 'ADMIN' ? '/admin' : '/');
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage.set(error.status === 401
          ? 'Adresse e-mail ou mot de passe incorrect.'
          : error.status === 404 || error.status === 410
            ? 'Cette invitation est invalide ou expirée. Demandez un nouveau lien au responsable.'
            : error.status === 409
              ? 'Un compte existe déjà avec cette adresse. Connectez-vous ou contactez le responsable.'
          : error.status === 0
            ? 'Le serveur est injoignable. Vérifiez que le backend est démarré.'
            : 'La connexion a échoué. Réessayez dans un instant.');
        this.isSubmitting.set(false);
      },
    });
  }

  protected emailError(): boolean {
    return this.attemptedSubmit() && !/^\S+@\S+\.\S+$/.test(this.email.trim());
  }

  protected passwordError(): boolean {
    return this.attemptedSubmit() && !this.password;
  }
}
