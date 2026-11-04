import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [FormsModule, RouterLink],
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
    if (this.password.length < 6) {
      this.errorMessage.set('Le mot de passe doit contenir au moins 6 caractères.');
      return;
    }
    if (new TextEncoder().encode(this.password).length > 72) {
      this.errorMessage.set('Le mot de passe ne peut pas dépasser 72 octets UTF-8.');
      return;
    }
    if (this.password !== this.confirmPassword) {
      this.errorMessage.set('Les deux mots de passe ne correspondent pas.');
      return;
    }
    this.isSubmitting.set(true);
    this.authService.resetPassword({ token: this.token, password: this.password }).subscribe({
      next: (result) => {
        this.successMessage.set(result.message);
        this.password = '';
        this.confirmPassword = '';
        this.isSubmitting.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage.set(error.status === 410
          ? 'Ce lien est invalide ou expiré. Demandez un nouveau lien.'
          : error.status === 400
            ? 'Le mot de passe ne respecte pas les exigences affichées.'
            : 'La réinitialisation a échoué. Réessayez ou demandez un nouveau lien.');
        this.isSubmitting.set(false);
      },
    });
  }

  protected passwordError(): boolean {
    return this.attemptedSubmit() && (this.password.length < 6 || new TextEncoder().encode(this.password).length > 72);
  }

  protected confirmationError(): boolean {
    return this.attemptedSubmit() && this.password !== this.confirmPassword;
  }
}
