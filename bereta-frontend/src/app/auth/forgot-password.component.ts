import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './forgot-password.component.html',
  styleUrl: './login.component.scss',
})
export class ForgotPasswordComponent {
  private readonly authService = inject(AuthService);
  protected email = '';
  protected readonly isSubmitting = signal(false);
  protected readonly attemptedSubmit = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);

  protected submit(): void {
    if (this.isSubmitting()) return;
    this.attemptedSubmit.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    if (!/^\S+@\S+\.\S+$/.test(this.email.trim())) {
      this.errorMessage.set('Saisissez une adresse e-mail valide.');
      return;
    }
    this.isSubmitting.set(true);
    this.authService.requestPasswordReset({ email: this.email.trim() }).subscribe({
      next: (result) => {
        this.successMessage.set(result.message);
        this.isSubmitting.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage.set(error.status === 503
          ? (error.error?.detail ?? 'Le service e-mail n’est pas configuré. Aucun e-mail n’a été envoyé. Contactez le responsable de BERETA.')
          : error.status === 0
            ? 'Le serveur est injoignable. Vérifiez que le backend est démarré.'
            : 'La demande n’a pas abouti. Vérifiez l’adresse et réessayez.');
        this.isSubmitting.set(false);
      },
    });
  }

  protected emailError(): boolean {
    return this.attemptedSubmit() && !/^\S+@\S+\.\S+$/.test(this.email.trim());
  }
}
