import { Directive, OnDestroy, WritableSignal, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { apiErrorMessage } from '../../shared/api-error';
import { AdminListFilters, AdminPage } from '../admin.model';

const SEARCH_DELAY_MS = 300;

/**
 * Base des listes de l'espace administrateur : recherche différée, filtres, tri et pagination côté serveur.
 * Chaque écran fournit ses filtres par défaut et l'appel à l'API.
 */
@Directive()
export abstract class AdminListPage<F extends AdminListFilters, T> implements OnDestroy {
  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  readonly filters: WritableSignal<F>;
  readonly result = signal<AdminPage<T> | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly filtersOpen = signal(false);

  private readonly defaults: F;
  private readonly errorMessage: string;

  protected constructor(defaults: F, errorMessage: string) {
    this.defaults = defaults;
    this.errorMessage = errorMessage;
    this.filters = signal<F>({ ...defaults });
  }

  protected abstract fetch(filters: F): Observable<AdminPage<T>>;

  ngOnDestroy(): void {
    clearTimeout(this.searchTimer);
  }

  load(): void {
    this.loading.set(true);
    this.fetch(this.filters()).subscribe({
      next: (page) => {
        this.result.set(page);
        this.loading.set(false);
        this.error.set(null);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(error, this.errorMessage));
      },
    });
  }

  /** Recherche : relancée après une courte pause de frappe. */
  search(value: string): void {
    this.filters.update((f) => ({ ...f, q: value, page: 0 }));
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(), SEARCH_DELAY_MS);
  }

  setFilter(key: keyof F, value: string): void {
    this.filters.update((f) => ({ ...f, [key]: value, page: 0 }));
    this.load();
  }

  sortBy(sort: F['sort']): void {
    this.filters.update((f) => ({
      ...f, sort, direction: f.sort === sort && f.direction === 'asc' ? 'desc' : 'asc', page: 0,
    }));
    this.load();
  }

  sortIndicator(sort: F['sort']): string {
    const f = this.filters();
    if (f.sort !== sort) return '';
    return f.direction === 'asc' ? '▲' : '▼';
  }

  clearChip(key: keyof F): void {
    this.filters.update((f) => ({ ...f, [key]: this.defaults[key], page: 0 }));
    this.load();
  }

  reset(): void {
    this.filters.set({ ...this.defaults });
    this.load();
  }

  goTo(page: number): void {
    this.filters.update((f) => ({ ...f, page }));
    this.load();
  }
}
