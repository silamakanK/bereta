import { Component, DestroyRef, EventEmitter, Input, OnChanges, Output, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from './auth.service';
import { OtpInputComponent } from './otp-input.component';
import { apiErrorMessage } from '../shared/api-error';

/**
 * Étape « saisir le code reçu » : numéro masqué, 6 cases, compte à rebours du renvoi.
 * Le parent reçoit le code complet via (verify) ; des champs supplémentaires (nouveau mot
 * de passe) peuvent être projetés avant le bouton.
 */
@Component({
  selector: 'app-otp-step',
  standalone: true,
  imports: [FormsModule, OtpInputComponent],
  template: `
    <form novalidate (ngSubmit)="submit()">
      <p class="otp-destination">
        Code envoyé {{ channel === 'EMAIL' ? 'à' : 'au' }} <strong>{{ destination || 'votre numéro' }}</strong>
      </p>
      <app-otp-input #otp [disabled]="busy" [invalid]="!!error" (codeChange)="code = $event"
                     (completed)="autoSubmit($event)" />
      <p class="login-help">Le code expire dans 5 minutes.</p>
      <ng-content />
      <button class="login-submit" type="submit" [disabled]="busy || code.length !== 6">
        {{ busy ? 'Vérification…' : submitLabel }}
      </button>
    </form>
    <div class="otp-resend">
      @if (secondsLeft() > 0) {
        <span>Renvoyer un code dans {{ secondsLeft() }} s</span>
      } @else {
        <button type="button" class="login-secondary" (click)="resend()" [disabled]="resending()">
          {{ resending() ? 'Envoi…' : 'Renvoyer un code' }}
        </button>
      }
      @if (resendMessage()) { <p class="login-help" role="status">{{ resendMessage() }}</p> }
    </div>
  `,
  styles: `
    .otp-destination { margin: 0 0 6px; font-size: 16px; }
    .otp-resend { margin-top: 14px; text-align: center; font-size: 15px; color: #475569; }
    .otp-destination { font-size: 16px; }
  `,
})
export class OtpStepComponent implements OnChanges {
  @Input({ required: true }) challengeId!: string;
  @Input() destination: string | null = null;
  @Input() channel: 'SMS' | 'EMAIL' | null = 'SMS';
  @Input() resendInSeconds = 60;
  @Input() submitLabel = 'Valider le code';
  @Input() busy = false;
  @Input() error: string | null = null;
  /** Désactive l'envoi automatique quand d'autres champs (mot de passe) doivent être remplis. */
  @Input() autoSubmitOnComplete = true;
  @Output() readonly verify = new EventEmitter<string>();
  @Output() readonly challengeRenewed = new EventEmitter<string>();
  @ViewChild('otp') private otpInput?: OtpInputComponent;

  private readonly auth = inject(AuthService);
  protected code = '';
  protected readonly secondsLeft = signal(0);
  protected readonly resending = signal(false);
  protected readonly resendMessage = signal<string | null>(null);
  private timer: ReturnType<typeof setInterval> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopTimer());
  }

  ngOnChanges(): void {
    if (this.timer === null) this.startTimer(this.resendInSeconds);
  }

  clear(): void {
    this.code = '';
    this.otpInput?.clear();
  }

  protected autoSubmit(code: string): void {
    this.code = code;
    if (this.autoSubmitOnComplete) this.submit();
  }

  protected submit(): void {
    if (this.busy || this.code.length !== 6) return;
    this.verify.emit(this.code);
  }

  protected resend(): void {
    this.resending.set(true);
    this.resendMessage.set(null);
    this.auth.resendCode(this.challengeId).subscribe({
      next: (sent) => {
        this.resending.set(false);
        this.resendMessage.set('Un nouveau code a été envoyé. L’ancien ne fonctionne plus.');
        this.clear();
        this.challengeRenewed.emit(sent.challengeId);
        this.startTimer(sent.resendAvailableInSeconds);
      },
      error: (error: unknown) => {
        this.resending.set(false);
        this.resendMessage.set(apiErrorMessage(error, 'Le code n’a pas pu être renvoyé. Réessayez.'));
        const retryAfter = (error as { error?: { retryAfterSeconds?: number } })?.error?.retryAfterSeconds;
        if (retryAfter) this.startTimer(retryAfter);
      },
    });
  }

  private startTimer(seconds: number): void {
    this.stopTimer();
    this.secondsLeft.set(Math.max(0, Math.ceil(seconds)));
    this.timer = setInterval(() => {
      const next = this.secondsLeft() - 1;
      this.secondsLeft.set(Math.max(0, next));
      if (next <= 0) this.stopTimer();
    }, 1000);
  }

  private stopTimer(): void {
    if (this.timer !== null) clearInterval(this.timer);
    this.timer = null;
  }
}
