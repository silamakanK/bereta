import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { apiErrorMessage } from '../../shared/api-error';
import { AdminApiService } from '../admin-api.service';
import { ROLE_LABELS, accountStatus } from '../admin-labels';
import { AdminUserDetail } from '../admin.model';
import { AdminConfirmDialogComponent } from '../confirm/admin-confirm-dialog.component';

type Action = 'disable' | 'enable' | 'reset';

interface ActionConfig { title: string; confirmLabel: string; danger: boolean; impact: string[]; }

/** Fiche d'un compte : coordonnées complètes, rattachements, sessions ; actions motivées et confirmées. */
@Component({
  selector: 'app-admin-user-detail',
  standalone: true,
  imports: [DatePipe, RouterLink, AdminConfirmDialogComponent],
  templateUrl: './admin-user-detail.component.html',
  styleUrls: ['../admin-common.scss', './admin-user-detail.component.scss'],
})
export class AdminUserDetailComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly auth = inject(AuthService);

  /** Identifiant de la route /admin/comptes/:id (liaison des paramètres de route aux inputs). */
  readonly id = input.required<string>();

  protected readonly roles = ROLE_LABELS;
  protected readonly status = accountStatus;
  protected readonly user = signal<AdminUserDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly action = signal<Action | null>(null);
  protected readonly busy = signal(false);
  protected readonly actionError = signal<string | null>(null);
  /** Mot de passe provisoire : affiché une seule fois, effacé dès que l'on quitte le panneau. */
  protected readonly temporaryPassword = signal<string | null>(null);
  protected readonly copied = signal(false);

  protected readonly isSelf = computed(() => this.user()?.id === this.auth.currentUser()?.id);
  protected readonly config = computed<ActionConfig | null>(() => {
    const user = this.user();
    const action = this.action();
    if (!user || !action) return null;
    const sessions = this.sessionsLine(user.activeSessions);
    switch (action) {
      case 'disable':
        return { title: `Désactiver ${this.name(user)}`, confirmLabel: 'Désactiver le compte', danger: true,
          impact: ['La connexion sera refusée immédiatement.', sessions, 'L’historique et les données restent intacts.'] };
      case 'enable':
        return { title: `Réactiver ${this.name(user)}`, confirmLabel: 'Réactiver le compte', danger: false,
          impact: ['La personne pourra de nouveau se connecter avec son mot de passe.'] };
      default:
        return { title: `Mot de passe provisoire pour ${this.name(user)}`, confirmLabel: 'Générer le mot de passe', danger: true,
          impact: ['Le mot de passe actuel ne fonctionnera plus.', sessions,
            'Le nouveau mot de passe s’affichera une seule fois ; il devra être changé à la connexion.'] };
    }
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api.user(this.id()).subscribe({
      next: (user) => {
        this.user.set(user);
        this.loading.set(false);
        this.error.set(null);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(error, 'Ce compte n’a pas pu être chargé.'));
      },
    });
  }

  protected ask(action: Action): void {
    this.message.set(null);
    this.actionError.set(null);
    this.temporaryPassword.set(null);
    this.action.set(action);
  }

  protected cancel(): void {
    this.action.set(null);
  }

  protected confirm(reason: string): void {
    const user = this.user();
    const action = this.action();
    if (!user || !action) return;
    this.busy.set(true);
    this.actionError.set(null);
    const done = (text: string) => {
      this.busy.set(false);
      this.action.set(null);
      this.message.set(text);
      this.load();
    };
    const fail = (error: unknown) => {
      this.busy.set(false);
      this.actionError.set(apiErrorMessage(error, 'L’action a échoué. Réessayez.'));
    };
    if (action === 'reset') {
      this.api.resetPassword(user.id, reason).subscribe({
        next: (result) => {
          this.temporaryPassword.set(result.temporaryPassword);
          done(`Mot de passe provisoire généré. ${this.sessionsLine(result.closedSessions)}`);
        },
        error: fail,
      });
      return;
    }
    const call: Observable<{ closedSessions: number }> = action === 'disable'
      ? this.api.disableUser(user.id, reason) : this.api.enableUser(user.id, reason);
    call.subscribe({
      next: (result) => done(action === 'disable'
        ? `Compte désactivé. ${this.sessionsLine(result.closedSessions)}` : 'Compte réactivé.'),
      error: fail,
    });
  }

  protected async copyPassword(): Promise<void> {
    const password = this.temporaryPassword();
    if (!password) return;
    try {
      await navigator.clipboard.writeText(password);
      this.copied.set(true);
    } catch {
      this.copied.set(false);
    }
  }

  protected forgetPassword(): void {
    this.temporaryPassword.set(null);
    this.copied.set(false);
  }

  protected name(user: AdminUserDetail): string {
    return user.displayName || user.email || user.phone || 'ce compte';
  }

  private sessionsLine(count: number): string {
    if (count === 0) return 'Aucune session ouverte.';
    return count === 1 ? '1 session sera fermée.' : `${count} sessions seront fermées.`;
  }
}
