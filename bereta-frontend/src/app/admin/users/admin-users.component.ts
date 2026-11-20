import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { AdminApiService } from '../admin-api.service';
import { ROLE_LABELS, accountStatus } from '../admin-labels';
import { AdminPage, AdminRole, AdminUserFilters, AdminUserSummary } from '../admin.model';
import { AdminListPage } from '../shared/admin-list-page';
import { AdminPagerComponent } from '../shared/admin-pager.component';

interface Chip { key: keyof AdminUserFilters; label: string; }

const DEFAULT_FILTERS: AdminUserFilters = { q: '', role: '', enabled: '', sort: 'createdAt', direction: 'desc', page: 0, size: 20 };

/** Comptes de toute la plateforme : recherche, filtres, tri et pagination côté serveur. */
@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [FormsModule, DatePipe, AdminPagerComponent],
  templateUrl: './admin-users.component.html',
  styleUrls: ['../admin-common.scss'],
})
export class AdminUsersComponent extends AdminListPage<AdminUserFilters, AdminUserSummary> {
  private readonly api = inject(AdminApiService);
  private readonly router = inject(Router);

  protected readonly roles = ROLE_LABELS;
  protected readonly status = accountStatus;

  protected readonly chips = computed<Chip[]>(() => {
    const f = this.filters();
    const chips: Chip[] = [];
    if (f.q.trim()) chips.push({ key: 'q', label: `« ${f.q.trim()} »` });
    if (f.role) chips.push({ key: 'role', label: `Rôle : ${ROLE_LABELS[f.role]}` });
    if (f.enabled) chips.push({ key: 'enabled', label: f.enabled === 'true' ? 'Actifs' : 'Désactivés' });
    return chips;
  });

  constructor() {
    super(DEFAULT_FILTERS, 'Les comptes n’ont pas pu être chargés.');
    this.load();
  }

  protected fetch(filters: AdminUserFilters): Observable<AdminPage<AdminUserSummary>> {
    return this.api.users(filters);
  }

  protected open(user: AdminUserSummary): void {
    void this.router.navigate(['/admin/comptes', user.id]);
  }

  protected roleLabel(role: AdminRole): string {
    return ROLE_LABELS[role];
  }
}
