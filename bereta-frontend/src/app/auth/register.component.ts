import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService, RegisteredAccountLoginError } from './auth.service';
import { passwordValidationMessage } from './register-validation';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './login.component.scss',
})
export class RegisterComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected firstName = '';
  protected lastName = '';
  protected email = '';
  protected password = '';
  protected confirmPassword = '';
  protected readonly showPassword = signal(false);
  protected readonly showConfirmPassword = signal(false);
  protected readonly attemptedSubmit = signal(false);
  protected readonly isSubmitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected submit(): void {
    if (this.isSubmitting()) return;
    this.attemptedSubmit.set(true);
    this.errorMessage.set(null);
    if (!this.firstName.trim() || !this.lastName.trim()) {
      this.errorMessage.set('Renseignez votre prénom et votre nom.');
      return;
    }
    if (`${this.firstName.trim()} ${this.lastName.trim()}`.length > 120) {
      this.errorMessage.set('Le prénom et le nom ne peuvent pas dépasser 120 caractères au total.');
      return;
    }
    if (!/^\S+@\S+\.\S+$/.test(this.email.trim())) {
      this.errorMessage.set('Saisissez une adresse e-mail valide.');
      return;
    }
    const passwordError = passwordValidationMessage(this.password, this.confirmPassword);
    if (passwordError) {
      this.errorMessage.set(passwordError);
      return;
    }

    this.isSubmitting.set(true);
    this.authService.register({
      firstName: this.firstName.trim(),
      lastName: this.lastName.trim(),
      email: this.email.trim(),
      password: this.password,
    }).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        void this.router.navigateByUrl('/');
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage.set(error instanceof RegisteredAccountLoginError
          ? 'Votre compte a été créé, mais la connexion automatique a échoué. Connectez-vous avec votre adresse et votre mot de passe.'
          : error.status === 409
            ? 'Cette adresse e-mail est déjà utilisée. Connectez-vous ou choisissez une autre adresse.'
            : error.status === 400
              ? 'Vérifiez les champs et les exigences du mot de passe.'
              : error.status === 404
                ? 'La route d’inscription est introuvable. Redémarrez le backend avec la dernière version.'
                : error.status === 403
                  ? 'La vérification de sécurité a expiré. Rechargez la page puis réessayez.'
                  : error.status === 0
                    ? 'Le serveur est injoignable. Vérifiez que le backend est démarré.'
                    : 'Le serveur n’a pas pu créer le compte. Vérifiez les logs du backend et que ses migrations sont appliquées.');
        this.isSubmitting.set(false);
      },
    });
  }

  protected nameError(value: string): boolean {
    return this.attemptedSubmit() && !value.trim();
  }

  protected emailError(): boolean {
    return this.attemptedSubmit() && !/^\S+@\S+\.\S+$/.test(this.email.trim());
  }

  protected passwordError(): boolean {
    return this.attemptedSubmit() && (this.password.length < 6 || new TextEncoder().encode(this.password).length > 72);
  }
}
