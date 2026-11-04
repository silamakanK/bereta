import { Component, inject, signal } from '@angular/core';
import { AuthService } from './auth.service';
import { OtpInputComponent } from './otp-input.component';
import { apiErrorMessage } from '../shared/api-error';

/** Vérification facultative de l'e-mail saisi à l'inscription (code envoyé par e-mail). */
@Component({
  selector: 'app-email-verification',
  standalone: true,
  imports: [OtpInputComponent],
  styleUrl: './login.component.scss',
  template: `
    <main class="login-page">
      <section class="login-card" aria-labelledby="email-title">
        <p class="login-eyebrow">VÉRIFICATION DE L’E-MAIL</p>
        <h1 id="email-title">Confirmez votre adresse e-mail</h1>
        <p class="login-intro">Saisissez le code envoyé à <strong>{{ auth.currentUser()?.email }}</strong>.
          Elle servira à recevoir vos bilans et un lien en cas d’oubli du mot de passe.</p>
        @if (errorMessage()) { <p class="login-error" role="alert">{{ errorMessage() }}</p> }
        @if (info()) { <p class="login-help" role="status">{{ info() }}</p> }
        <app-otp-input #otp label="Code reçu par e-mail" [disabled]="busy()" (codeChange)="code = $event"
                       (completed)="verify($event)" />
        <button class="login-submit" type="button" [disabled]="busy() || code.length !== 6" (click)="verify(code)">
          {{ busy() ? 'Vérification…' : 'Vérifier mon e-mail' }}</button>
        <button class="login-secondary" type="button" [disabled]="busy()" (click)="resend()">Recevoir un nouveau code</button>
        <button class="login-secondary" type="button" (click)="later()">Plus tard</button>
      </section>
    </main>
  `,
})
export class EmailVerificationComponent {
  protected readonly auth = inject(AuthService);
  protected code = '';
  protected readonly busy = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);

  protected verify(code: string): void {
    if (this.busy() || code.length !== 6) return;
    this.busy.set(true);
    this.errorMessage.set(null);
    this.auth.verifyEmail(code).subscribe({
      next: () => this.busy.set(false),
      error: (error: unknown) => {
        this.busy.set(false);
        this.errorMessage.set(apiErrorMessage(error, 'Le code n’a pas pu être vérifié.'));
      },
    });
  }

  protected resend(): void {
    this.busy.set(true);
    this.errorMessage.set(null);
    this.auth.sendEmailCode().subscribe({
      next: () => {
        this.busy.set(false);
        this.info.set('Un nouveau code a été envoyé. L’ancien ne fonctionne plus.');
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.errorMessage.set(apiErrorMessage(error, 'Le code n’a pas pu être envoyé.'));
      },
    });
  }

  protected later(): void {
    this.auth.pendingEmailVerification.set(false);
  }
}
