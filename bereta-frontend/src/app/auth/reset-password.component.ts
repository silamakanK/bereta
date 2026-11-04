import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from './auth.service';
import { PasswordStrengthComponent } from './password-strength.component';
import { passwordValidationMessage } from './password-rules';
import { apiErrorMessage } from '../shared/api-error';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [FormsModule, RouterLink, PasswordStrengthComponent],
  templateUrl: './reset-password.component.html',
  styleUrl: './login.component.scss',
})
export class ResetPasswordComponent {
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  protected readonly token = this.route.snapshot.queryParamMap.get('token') ?? '';
  protected password = '';
  protected confirmPassword = '';
  protected readonly showPassword = signal(false);
  protected readonly showConfirmPassword = signal(false);
  protected readonly isSubmitting = signal(false);
  protected readonly attemptedSubmit = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);

  protected submit(): void {
    if (this.isSubmitting()) return;
    this.attemptedSubmit.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    if (!this.token) {
      this.errorMessage.set('Le lien est incomplet. Demandez un nouveau lien de réinitialisation.');
      return;
    }
    const passwordError = passwordValidationMessage(this.password, this.confirmPassword);
    if (passwordError) {
      this.errorMessage.set(passwordError);
      return;
    }
    this.isSubmitting.set(true);
    this.authService.resetPasswordWithLink(this.token, this.password).subscribe({
      next: (result) => {
        this.successMessage.set(result.message);
        this.password = '';
        this.confirmPassword = '';
        this.isSubmitting.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(apiErrorMessage(error, 'La réinitialisation a échoué. Demandez un nouveau lien.'));
        this.isSubmitting.set(false);
      },
    });
  }

  protected passwordError(): boolean {
    return this.attemptedSubmit() && passwordValidationMessage(this.password, this.password) !== null;
  }

  protected confirmationError(): boolean {
    return this.attemptedSubmit() && this.password !== this.confirmPassword;
  }
}
