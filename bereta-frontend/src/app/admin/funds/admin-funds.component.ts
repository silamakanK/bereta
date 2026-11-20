import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { AdminApiService } from '../admin-api.service';
import { AdminFundFilters, AdminFundSummary, AdminPage } from '../admin.model';
import { AdminListPage } from '../shared/admin-list-page';
import { AdminPagerComponent } from '../shared/admin-pager.component';
import { AdminStatusPillComponent } from '../shared/admin-status-pill.component';

interface Chip { key: keyof AdminFundFilters; label: string; }

const DEFAULT_FILTERS: AdminFundFilters = {
  q: '', status: '', associationId: '', sort: 'createdAt', direction: 'desc', page: 0, size: 20,
};

/** Caisses locales de toute la plateforme : recherche, association, statut, tri et pagination côté serveur. */
@Component({
  selector: 'app-admin-funds',
  standalone: true,
  imports: [FormsModule, CurrencyPipe, RouterLink, AdminPagerComponent, AdminStatusPillComponent],
  templateUrl: './admin-funds.component.html',
  styleUrls: ['../admin-common.scss'],
})
export class AdminFundsComponent extends AdminListPage<AdminFundFilters, AdminFundSummary> implements OnInit {
  private readonly api = inject(AdminApiService);

  /** Paramètre ?associationId= (lien « Voir dans la liste » depuis une association). */
  readonly associationId = input<string>();

  protected readonly chips = computed<Chip[]>(() => {
    const f = this.filters();
    const chips: Chip[] = [];
    if (f.q.trim()) chips.push({ key: 'q', label: `« ${f.q.trim()} »` });
    if (f.status) chips.push({ key: 'status', label: f.status === 'ACTIVE' ? 'Actives' : 'Archivées' });
    if (f.associationId) chips.push({ key: 'associationId', label: 'Une association' });
    return chips;
  });

  constructor() {
    super(DEFAULT_FILTERS, 'Les caisses n’ont pas pu être chargées.');
  }

  ngOnInit(): void {
    const associationId = this.associationId();
    if (associationId) this.filters.update((f) => ({ ...f, associationId }));
    this.load();
  }

  protected fetch(filters: AdminFundFilters): Observable<AdminPage<AdminFundSummary>> {
    return this.api.funds(filters);
  }
}
