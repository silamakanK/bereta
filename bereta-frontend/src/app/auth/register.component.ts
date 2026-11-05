import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from './auth.service';
import { PasswordStrengthComponent } from './password-strength.component';
import { passwordValidationMessage } from './password-rules';
import { apiErrorMessage } from '../shared/api-error';

/**
 * Inscription d'un responsable : téléphone + mot de passe + CGU, compte actif et session
 * ouverte immédiatement ; prénom, nom et e-mail ensuite (« Complète ton profil »).
 */
@Component({
  selector: 'app-register',
  standalone: true,
  imports: [FormsModule, RouterLink, PasswordStrengthComponent],
  templateUrl: './register.component.html',
  styleUrl: './login.component.scss',
})
export class RegisterComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected phone = '';
  protected password = '';
  protected confirmPassword = '';
  protected acceptTerms = false;
  protected readonly isSubmitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly showPassword = signal(false);
  protected readonly attemptedSubmit = signal(false);

  protected submit(): void {
    if (this.isSubmitting()) return;
    this.attemptedSubmit.set(true);
    this.errorMessage.set(null);
    if (this.phoneError()) {
      this.errorMessage.set('Saisissez votre numéro de téléphone portable.');
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
    this.authService.register({ phone: this.phone.trim(), password: this.password, acceptTerms: true }).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.password = '';
        this.confirmPassword = '';
        void this.router.navigateByUrl('/completer-profil');
      },
      error: (error: unknown) => {
        this.isSubmitting.set(false);
        this.errorMessage.set(apiErrorMessage(error, 'Le compte n’a pas pu être créé. Réessayez.'));
      },
    });
  }

  protected phoneError(): boolean {
    return this.attemptedSubmit() && this.phone.replace(/\D/g, '').length < 8;
  }

  protected passwordError(): boolean {
    return this.attemptedSubmit() && passwordValidationMessage(this.password, this.password) !== null;
  }

  protected confirmError(): boolean {
    return this.attemptedSubmit() && this.confirmPassword !== this.password;
  }
}
