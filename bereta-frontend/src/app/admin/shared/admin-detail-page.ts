import { Directive, OnInit, computed, input, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { apiErrorMessage } from '../../shared/api-error';
import { AdminUserSummary } from '../admin.model';

export interface ActionConfig { title: string; confirmLabel: string; danger: boolean; impact: string[]; }

/**
 * Base des fiches de l'espace administrateur : chargement par identifiant de route, action en attente de
 * confirmation (motif obligatoire), compte cible éventuel, puis rechargement avec le résultat renvoyé.
 */
@Directive()
export abstract class AdminDetailPage<T, A> implements OnInit {
  /** Identifiant de la route (liaison des paramètres de route aux inputs). */
  readonly id = input.required<string>();

  readonly item = signal<T | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly action = signal<A | null>(null);
  /** Compte choisi dans le sélecteur (transfert, réaffectation). */
  readonly target = signal<AdminUserSummary | null>(null);
  readonly busy = signal(false);
  readonly actionError = signal<string | null>(null);
  readonly ready = computed(() => !this.needsTarget(this.action()) || !!this.target());

  protected constructor(private readonly loadError: string) {
  }

  protected abstract fetch(id: string): Observable<T>;

  /** Vrai si l'action exige de choisir un compte avant de confirmer. */
  protected abstract needsTarget(action: A | null): boolean;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.fetch(this.id()).subscribe({
      next: (item) => {
        this.item.set(item);
        this.loading.set(false);
        this.error.set(null);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(error, this.loadError));
      },
    });
  }

  ask(action: A): void {
    this.message.set(null);
    this.actionError.set(null);
    this.target.set(null);
    this.action.set(action);
  }

  cancel(): void {
    this.action.set(null);
  }

  /** Exécute l'action confirmée ; la fiche renvoyée par le serveur remplace l'affichage. */
  protected run(call: Observable<T>, done: string): void {
    this.busy.set(true);
    this.actionError.set(null);
    call.subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.action.set(null);
        this.item.set(updated);
        this.message.set(done);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.actionError.set(apiErrorMessage(error, 'L’action a échoué. Réessayez.'));
      },
    });
  }
}
