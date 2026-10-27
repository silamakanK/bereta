import { Component, EventEmitter, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from './auth.service';
import { AuthUser } from './auth.model';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  @Output() readonly signedIn = new EventEmitter<AuthUser>();

  private readonly authService = inject(AuthService);
  protected email = '';
  protected password = '';
  protected readonly isSubmitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected submit(): void {
    if (this.isSubmitting()) return;
    this.isSubmitting.set(true);
    this.errorMessage.set(null);
    this.authService.login(this.email.trim(), this.password).subscribe({
      next: (user) => {
        this.password = '';
        this.isSubmitting.set(false);
        this.signedIn.emit(user);
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage.set(error.status === 401
          ? 'Adresse e-mail ou mot de passe incorrect.'
          : error.status === 0
            ? 'Le serveur est injoignable. Vérifiez que le backend est démarré.'
            : 'La connexion a échoué. Réessayez dans un instant.');
        this.isSubmitting.set(false);
      },
    });
  }
}
