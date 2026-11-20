import { Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ModalDialogDirective } from '../../shared/modal-dialog.directive';

export const MIN_REASON_LENGTH = 10;

/**
 * Confirmation d'une action sensible : résumé de l'impact, motif obligatoire (10 caractères minimum),
 * bouton rouge pour les actions destructives. Un choix complémentaire peut être projeté avant le motif. Le parent garde la main sur l'appel et l'état « en cours ».
 */
@Component({
  selector: 'app-admin-confirm-dialog',
  standalone: true,
  imports: [FormsModule, ModalDialogDirective],
  templateUrl: './admin-confirm-dialog.component.html',
  styleUrl: './admin-confirm-dialog.component.scss',
})
export class AdminConfirmDialogComponent {
  readonly title = input.required<string>();
  readonly impact = input<string[]>([]);
  readonly confirmLabel = input('Confirmer');
  readonly danger = input(false);
  readonly busy = input(false);
  readonly error = input<string | null>(null);
  /** Faux tant qu'un choix complémentaire (contenu projeté, ex. : le compte cible) manque. */
  readonly ready = input(true);

  readonly confirmed = output<string>();
  readonly cancelled = output<void>();

  protected readonly minLength = MIN_REASON_LENGTH;
  protected readonly reason = signal('');
  protected readonly remaining = computed(() => Math.max(0, MIN_REASON_LENGTH - this.reason().trim().length));

  protected submit(): void {
    if (this.remaining() > 0 || this.busy() || !this.ready()) return;
    this.confirmed.emit(this.reason().trim());
  }

  protected cancel(): void {
    if (!this.busy()) this.cancelled.emit();
  }
}
