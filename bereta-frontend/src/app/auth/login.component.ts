import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from './auth.service';
import { apiErrorMessage } from '../shared/api-error';

/** Maquette « connexion_bereta » : téléphone ou e-mail + mot de passe, session ouverte directement. */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected identifier = '';
  protected password = '';
  protected readonly isSubmitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly showPassword = signal(false);
  protected readonly attemptedSubmit = signal(false);

  protected submit(): void {
    if (this.isSubmitting()) return;
    this.attemptedSubmit.set(true);
    if (this.identifierError() || this.passwordError()) {
      this.errorMessage.set('Saisissez votre téléphone (ou e-mail) et votre mot de passe.');
      return;
    }
    this.isSubmitting.set(true);
    this.errorMessage.set(null);
    this.authService.login(this.identifier.trim(), this.password).subscribe({
      next: (user) => {
        this.isSubmitting.set(false);
        this.password = '';
        void this.router.navigateByUrl(user.role === 'ADMIN' ? '/admin' : '/');
      },
      error: (error: unknown) => {
        this.isSubmitting.set(false);
        this.errorMessage.set(apiErrorMessage(error, 'Identifiants incorrects.'));
      },
    });
  }

  protected identifierError(): boolean {
    return this.attemptedSubmit() && this.identifier.trim().length < 6;
  }

  protected passwordError(): boolean {
    return this.attemptedSubmit() && !this.password;
  }
}
