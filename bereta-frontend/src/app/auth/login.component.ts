import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { LoginStep } from './auth.model';
import { OtpStepComponent } from './otp-step.component';
import { apiErrorMessage } from '../shared/api-error';

type Step = 'credentials' | 'phone' | 'code';

/** Maquette « connexion_bereta » : identifiant (téléphone ou e-mail) + mot de passe, puis code SMS. */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink, OtpStepComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly step = signal<Step>('credentials');
  protected identifier = '';
  protected password = '';
  protected phone = '';
  protected readonly challenge = signal<LoginStep | null>(null);
  protected readonly isSubmitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly showPassword = signal(false);
  protected readonly attemptedSubmit = signal(false);
  private enrollmentToken: string | null = null;

  protected submitCredentials(): void {
    if (this.isSubmitting()) return;
    this.attemptedSubmit.set(true);
    if (this.identifierError() || this.passwordError()) {
      this.errorMessage.set('Renseignez votre numéro de téléphone (ou e-mail) et votre mot de passe.');
      return;
    }
    this.run(this.authService.login(this.identifier.trim(), this.password), (step) => this.onStep(step),
      'La connexion a échoué. Réessayez dans un instant.');
  }

  protected submitPhone(): void {
    if (this.isSubmitting() || !this.enrollmentToken) return;
    if (!this.phone.trim()) {
      this.errorMessage.set('Saisissez votre numéro de téléphone portable.');
      return;
    }
    this.run(this.authService.enrollPhone(this.enrollmentToken, this.phone.trim()), (step) => this.onStep(step),
      'Le code n’a pas pu être envoyé. Vérifiez le numéro.');
  }

  protected submitCode(code: string): void {
    const challengeId = this.challenge()?.challengeId;
    if (this.isSubmitting() || !challengeId) return;
    this.run(this.authService.verifyLogin(challengeId, code), (user) => {
      this.password = '';
      void this.router.navigateByUrl(user.role === 'ADMIN' ? '/admin' : '/');
    }, 'Le code n’a pas pu être vérifié. Réessayez.');
  }

  protected renewChallenge(challengeId: string): void {
    this.challenge.update((current) => (current ? { ...current, challengeId } : current));
  }

  protected backToCredentials(): void {
    this.step.set('credentials');
    this.challenge.set(null);
    this.errorMessage.set(null);
  }

  protected identifierError(): boolean {
    return this.attemptedSubmit() && this.identifier.trim().length < 6;
  }

  protected passwordError(): boolean {
    return this.attemptedSubmit() && !this.password;
  }

  private onStep(step: LoginStep): void {
    if (step.step === 'PHONE_REQUIRED') {
      this.enrollmentToken = step.enrollmentToken;
      this.step.set('phone');
      return;
    }
    this.challenge.set(step);
    this.step.set('code');
  }

  private run<T>(operation: Observable<T>, onSuccess: (value: T) => void, fallback: string): void {
    this.isSubmitting.set(true);
    this.errorMessage.set(null);
    operation.subscribe({
      next: (value) => {
        this.isSubmitting.set(false);
        onSuccess(value);
      },
      error: (error: unknown) => {
        this.isSubmitting.set(false);
        this.errorMessage.set(apiErrorMessage(error, fallback));
      },
    });
  }
}
