import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from './auth.service';
import { ForgotPasswordResponse } from './auth.model';
import { OtpStepComponent } from './otp-step.component';
import { PasswordStrengthComponent } from './password-strength.component';
import { passwordValidationMessage } from './password-rules';
import { apiErrorMessage } from '../shared/api-error';

/**
 * Maquette « mot_de_passe_oubli_bereta » : un trésorier peut aussi demander à son
 * responsable ; sinon, code SMS (téléphone) ou lien (e-mail). La réponse ne révèle
 * jamais si un compte existe.
 */
@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [FormsModule, RouterLink, OtpStepComponent, PasswordStrengthComponent],
  templateUrl: './forgot-password.component.html',
  styleUrl: './login.component.scss',
})
export class ForgotPasswordComponent {
  private readonly authService = inject(AuthService);

  protected identifier = '';
  protected password = '';
  protected confirmPassword = '';
  protected readonly request = signal<ForgotPasswordResponse | null>(null);
  protected readonly done = signal<string | null>(null);
  protected readonly isSubmitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly showPassword = signal(false);

  protected submitIdentifier(): void {
    if (this.isSubmitting()) return;
    if (this.identifier.trim().length < 6) {
      this.errorMessage.set('Saisissez votre numéro de téléphone ou votre adresse e-mail.');
      return;
    }
    this.isSubmitting.set(true);
    this.errorMessage.set(null);
    this.authService.forgotPassword(this.identifier.trim()).subscribe({
      next: (response) => {
        this.isSubmitting.set(false);
        this.request.set(response);
      },
      error: (error: unknown) => {
        this.isSubmitting.set(false);
        this.errorMessage.set(apiErrorMessage(error, 'La demande n’a pas pu être envoyée. Réessayez.'));
      },
    });
  }

  protected submitCode(code: string): void {
    const challengeId = this.request()?.challengeId;
    if (this.isSubmitting() || !challengeId) return;
    const passwordError = passwordValidationMessage(this.password, this.confirmPassword);
    if (passwordError) {
      this.errorMessage.set(passwordError);
      return;
    }
    this.isSubmitting.set(true);
    this.errorMessage.set(null);
    this.authService.resetPasswordWithCode(challengeId, code, this.password).subscribe({
      next: (result) => {
        this.isSubmitting.set(false);
        this.password = '';
        this.confirmPassword = '';
        this.done.set(result.message);
      },
      error: (error: unknown) => {
        this.isSubmitting.set(false);
        this.errorMessage.set(apiErrorMessage(error, 'Le mot de passe n’a pas pu être modifié. Réessayez.'));
      },
    });
  }

  protected renewChallenge(challengeId: string): void {
    this.request.update((current) => (current ? { ...current, challengeId } : current));
  }

  protected restart(): void {
    this.request.set(null);
    this.errorMessage.set(null);
  }
}
