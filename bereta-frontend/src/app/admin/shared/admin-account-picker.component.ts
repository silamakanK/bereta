import { Component, OnDestroy, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiErrorMessage } from '../../shared/api-error';
import { AdminApiService } from '../admin-api.service';
import { ROLE_LABELS } from '../admin-labels';
import { AdminRole, AdminUserSummary } from '../admin.model';

const SEARCH_DELAY_MS = 300;
const MIN_QUERY_LENGTH = 2;

/**
 * Choix d'un compte actif existant (transfert, réaffectation) : recherche par nom, téléphone ou e-mail,
 * coordonnées masquées. Le serveur revérifie l'éligibilité du compte choisi.
 */
@Component({
  selector: 'app-admin-account-picker',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './admin-account-picker.component.html',
  styleUrl: './admin-account-picker.component.scss',
})
export class AdminAccountPickerComponent implements OnDestroy {
  private readonly api = inject(AdminApiService);
  private timer: ReturnType<typeof setTimeout> | undefined;

  readonly label = input('Nouveau compte');
  /** Rôle exigé (vide : tous sauf administrateur). */
  readonly role = input<'' | AdminRole>('');
  /** Comptes à ne pas proposer (titulaire actuel). */
  readonly exclude = input<string[]>([]);
  readonly picked = output<AdminUserSummary | null>();

  protected readonly roles = ROLE_LABELS;
  protected readonly query = signal('');
  protected readonly results = signal<AdminUserSummary[]>([]);
  protected readonly searching = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly selected = signal<string | null>(null);

  ngOnDestroy(): void {
    clearTimeout(this.timer);
  }

  protected search(value: string): void {
    this.query.set(value);
    this.choose(null);
    clearTimeout(this.timer);
    if (value.trim().length < MIN_QUERY_LENGTH) {
      this.results.set([]);
      return;
    }
    this.timer = setTimeout(() => this.run(value.trim()), SEARCH_DELAY_MS);
  }

  protected choose(account: AdminUserSummary | null): void {
    this.selected.set(account?.id ?? null);
    this.picked.emit(account);
  }

  private run(q: string): void {
    this.searching.set(true);
    this.api.users({ q, role: this.role(), enabled: 'true', sort: 'name', direction: 'asc', page: 0, size: 8 }).subscribe({
      next: (page) => {
        const excluded = new Set(this.exclude());
        this.results.set(page.items.filter((user) => user.role !== 'ADMIN' && !excluded.has(user.id)));
        this.searching.set(false);
        this.error.set(null);
      },
      error: (error: unknown) => {
        this.searching.set(false);
        this.error.set(apiErrorMessage(error, 'La recherche a échoué.'));
      },
    });
  }
}
