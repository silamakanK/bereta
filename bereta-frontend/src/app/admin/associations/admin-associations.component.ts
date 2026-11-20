import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { AdminApiService } from '../admin-api.service';
import { AdminAssociationFilters, AdminAssociationSummary, AdminPage } from '../admin.model';
import { AdminListPage } from '../shared/admin-list-page';
import { AdminPagerComponent } from '../shared/admin-pager.component';
import { AdminStatusPillComponent } from '../shared/admin-status-pill.component';

interface Chip { key: keyof AdminAssociationFilters; label: string; }

const DEFAULT_FILTERS: AdminAssociationFilters = { q: '', status: '', sort: 'createdAt', direction: 'desc', page: 0, size: 20 };

/** Associations de toute la plateforme : recherche, statut, tri et pagination côté serveur. */
@Component({
  selector: 'app-admin-associations',
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink, AdminPagerComponent, AdminStatusPillComponent],
  templateUrl: './admin-associations.component.html',
  styleUrls: ['../admin-common.scss'],
})
export class AdminAssociationsComponent extends AdminListPage<AdminAssociationFilters, AdminAssociationSummary> {
  private readonly api = inject(AdminApiService);

  protected readonly chips = computed<Chip[]>(() => {
    const f = this.filters();
    const chips: Chip[] = [];
    if (f.q.trim()) chips.push({ key: 'q', label: `« ${f.q.trim()} »` });
    if (f.status) chips.push({ key: 'status', label: f.status === 'ACTIVE' ? 'Actives' : 'Archivées' });
    return chips;
  });

  constructor() {
    super(DEFAULT_FILTERS, 'Les associations n’ont pas pu être chargées.');
    this.load();
  }

  protected fetch(filters: AdminAssociationFilters): Observable<AdminPage<AdminAssociationSummary>> {
    return this.api.associations(filters);
  }
}
