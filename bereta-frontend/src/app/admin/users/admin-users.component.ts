import { DatePipe } from '@angular/common';
import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { apiErrorMessage } from '../../shared/api-error';
import { AdminApiService } from '../admin-api.service';
import { ROLE_LABELS, accountStatus } from '../admin-labels';
import { AdminPage, AdminRole, AdminUserFilters, AdminUserSummary } from '../admin.model';

interface Chip { key: keyof AdminUserFilters; label: string; }

const DEFAULT_FILTERS: AdminUserFilters = { q: '', role: '', enabled: '', sort: 'createdAt', direction: 'desc', page: 0, size: 20 };
const SEARCH_DELAY_MS = 300;

/** Comptes de toute la plateforme : recherche, filtres, tri et pagination côté serveur. */
@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [FormsModule, DatePipe],
  templateUrl: './admin-users.component.html',
  styleUrls: ['../admin-common.scss'],
})
export class AdminUsersComponent implements OnDestroy {
  private readonly api = inject(AdminApiService);
  private readonly router = inject(Router);
  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  protected readonly roles = ROLE_LABELS;
  protected readonly status = accountStatus;
  protected readonly filters = signal<AdminUserFilters>({ ...DEFAULT_FILTERS });
  protected readonly result = signal<AdminPage<AdminUserSummary> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly filtersOpen = signal(false);

  protected readonly chips = computed<Chip[]>(() => {
    const f = this.filters();
    const chips: Chip[] = [];
    if (f.q.trim()) chips.push({ key: 'q', label: `« ${f.q.trim()} »` });
    if (f.role) chips.push({ key: 'role', label: `Rôle : ${ROLE_LABELS[f.role]}` });
    if (f.enabled) chips.push({ key: 'enabled', label: f.enabled === 'true' ? 'Actifs' : 'Désactivés' });
    return chips;
  });

  constructor() {
    this.load();
  }

  ngOnDestroy(): void {
    clearTimeout(this.searchTimer);
  }

  protected load(): void {
    this.loading.set(true);
    this.api.users(this.filters()).subscribe({
      next: (page) => {
        this.result.set(page);
        this.loading.set(false);
        this.error.set(null);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(error, 'Les comptes n’ont pas pu être chargés.'));
      },
    });
  }

  /** Recherche : relancée après une courte pause de frappe. */
  protected search(value: string): void {
    this.filters.update((f) => ({ ...f, q: value, page: 0 }));
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(), SEARCH_DELAY_MS);
  }

  protected setFilter(key: 'role' | 'enabled', value: string): void {
    this.filters.update((f) => ({ ...f, [key]: value, page: 0 }));
    this.load();
  }

  protected sortBy(sort: AdminUserFilters['sort']): void {
    this.filters.update((f) => ({
      ...f, sort, direction: f.sort === sort && f.direction === 'asc' ? 'desc' : 'asc', page: 0,
    }));
    this.load();
  }

  protected sortIndicator(sort: AdminUserFilters['sort']): string {
    const f = this.filters();
    if (f.sort !== sort) return '';
    return f.direction === 'asc' ? '▲' : '▼';
  }

  protected clearChip(key: keyof AdminUserFilters): void {
    this.filters.update((f) => ({ ...f, [key]: DEFAULT_FILTERS[key], page: 0 }));
    this.load();
  }

  protected reset(): void {
    this.filters.set({ ...DEFAULT_FILTERS });
    this.load();
  }

  protected goTo(page: number): void {
    this.filters.update((f) => ({ ...f, page }));
    this.load();
  }

  protected open(user: AdminUserSummary): void {
    void this.router.navigate(['/admin/comptes', user.id]);
  }

  protected roleLabel(role: AdminRole): string {
    return ROLE_LABELS[role];
  }
}
