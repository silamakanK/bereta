import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from './auth.service';
import { CodeSent } from './auth.model';
import { OtpStepComponent } from './otp-step.component';
import { PasswordStrengthComponent } from './password-strength.component';
import { passwordValidationMessage } from './password-rules';
import { apiErrorMessage } from '../shared/api-error';

/** Maquette « inscription_responsable_bereta », puis vérification du téléphone par SMS. */
@Component({
  selector: 'app-register',
  standalone: true,
  imports: [FormsModule, RouterLink, OtpStepComponent, PasswordStrengthComponent],
  templateUrl: './register.component.html',
  styleUrl: './login.component.scss',
})
export class RegisterComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected firstName = '';
  protected lastName = '';
  protected phone = '';
  protected email = '';
  protected password = '';
  protected confirmPassword = '';
  protected acceptTerms = false;
  protected readonly phoneChallenge = signal<CodeSent | null>(null);
  protected readonly isSubmitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly showPassword = signal(false);
  protected readonly showConfirmPassword = signal(false);
  protected readonly attemptedSubmit = signal(false);
  private emailVerificationSent = false;

  protected submit(): void {
    if (this.isSubmitting()) return;
    this.attemptedSubmit.set(true);
    this.errorMessage.set(null);
    if (!this.firstName.trim() || !this.lastName.trim()) {
      this.errorMessage.set('Renseignez votre prénom et votre nom.');
      return;
    }
    if (this.phoneError()) {
      this.errorMessage.set('Saisissez votre numéro de téléphone portable.');
      return;
    }
    if (this.emailError()) {
      this.errorMessage.set('L’adresse e-mail n’est pas valide. Vous pouvez aussi laisser le champ vide.');
      return;
    }
    const passwordError = passwordValidationMessage(this.password, this.confirmPassword);
    if (passwordError) {
      this.errorMessage.set(passwordError);
      return;
    }
    if (!this.acceptTerms) {
      this.errorMessage.set('Acceptez les conditions d’utilisation pour continuer.');
      return;
    }
    this.isSubmitting.set(true);
    this.authService.register({
      firstName: this.firstName.trim(),
      lastName: this.lastName.trim(),
      phone: this.phone.trim(),
      email: this.email.trim() || null,
      password: this.password,
      acceptTerms: this.acceptTerms,
    }).subscribe({
      next: (response) => {
        this.isSubmitting.set(false);
        this.emailVerificationSent = response.emailVerificationSent;
        this.password = '';
        this.confirmPassword = '';
        this.phoneChallenge.set(response.phoneVerification);
      },
      error: (error: unknown) => {
        this.isSubmitting.set(false);
        this.errorMessage.set(apiErrorMessage(error, 'Le compte n’a pas pu être créé. Vérifiez les champs puis réessayez.'));
      },
    });
  }

  protected verifyPhone(code: string): void {
    const challenge = this.phoneChallenge();
    if (this.isSubmitting() || !challenge) return;
    this.isSubmitting.set(true);
    this.errorMessage.set(null);
    this.authService.verifyRegistration(challenge.challengeId, code, this.emailVerificationSent).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        void this.router.navigateByUrl('/');
      },
      error: (error: unknown) => {
        this.isSubmitting.set(false);
        this.errorMessage.set(apiErrorMessage(error, 'Le code n’a pas pu être vérifié. Réessayez.'));
      },
    });
  }

  protected renewChallenge(challengeId: string): void {
    this.phoneChallenge.update((current) => (current ? { ...current, challengeId } : current));
  }

  protected nameError(value: string): boolean {
    return this.attemptedSubmit() && !value.trim();
  }

  protected phoneError(): boolean {
    return this.attemptedSubmit() && this.phone.replace(/\D/g, '').length < 8;
  }

  protected emailError(): boolean {
    return this.attemptedSubmit() && !!this.email.trim() && !/^\S+@\S+\.\S+$/.test(this.email.trim());
  }

  protected passwordError(): boolean {
    return this.attemptedSubmit() && passwordValidationMessage(this.password, this.password) !== null;
  }
}
