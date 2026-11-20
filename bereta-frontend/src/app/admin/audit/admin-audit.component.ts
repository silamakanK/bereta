import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiErrorMessage } from '../../shared/api-error';
import { AdminApiService } from '../admin-api.service';
import { ACTION_LABELS, actionLabel, describeValues } from '../admin-labels';
import { AdminAuditEntry, AdminAuditFilters, AdminPage } from '../admin.model';

interface Chip { key: keyof AdminAuditFilters; label: string; }

const DEFAULT_FILTERS: AdminAuditFilters = { action: '', targetType: '', targetId: '', associationId: '', from: '', to: '', page: 0, size: 25 };
const TARGET_TYPES: Record<string, string> = { USER: 'Compte', ASSOCIATION: 'Association', LOCAL_FUND: 'Caisse locale' };

/** Journal des actions : en lecture seule, filtres côté serveur, export CSV des résultats filtrés. */
@Component({
  selector: 'app-admin-audit',
  standalone: true,
  imports: [FormsModule, DatePipe],
  templateUrl: './admin-audit.component.html',
  styleUrls: ['../admin-common.scss', './admin-audit.component.scss'],
})
export class AdminAuditComponent {
  private readonly api = inject(AdminApiService);

  protected readonly actions = Object.entries(ACTION_LABELS);
  protected readonly targetTypes = Object.entries(TARGET_TYPES);
  protected readonly label = actionLabel;
  protected readonly values = describeValues;
  protected readonly filters = signal<AdminAuditFilters>({ ...DEFAULT_FILTERS });
  protected readonly result = signal<AdminPage<AdminAuditEntry> | null>(null);
  protected readonly loading = signal(true);
  protected readonly exporting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly filtersOpen = signal(false);

  protected readonly chips = computed<Chip[]>(() => {
    const f = this.filters();
    const chips: Chip[] = [];
    if (f.action) chips.push({ key: 'action', label: actionLabel(f.action) });
    if (f.targetType) chips.push({ key: 'targetType', label: TARGET_TYPES[f.targetType] ?? f.targetType });
    if (f.targetId) chips.push({ key: 'targetId', label: 'Cible précise' });
    if (f.associationId) chips.push({ key: 'associationId', label: 'Association précise' });
    if (f.from) chips.push({ key: 'from', label: `Depuis le ${f.from}` });
    if (f.to) chips.push({ key: 'to', label: `Jusqu’au ${f.to}` });
    return chips;
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api.auditLogs(this.filters()).subscribe({
      next: (page) => {
        this.result.set(page);
        this.loading.set(false);
        this.error.set(null);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(error, 'Le journal n’a pas pu être chargé.'));
      },
    });
  }

  protected setFilter(key: keyof AdminAuditFilters, value: string): void {
    this.filters.update((f) => ({ ...f, [key]: value, page: 0 }));
    this.load();
  }

  protected clearChip(key: keyof AdminAuditFilters): void {
    this.setFilter(key, '');
  }

  protected reset(): void {
    this.filters.set({ ...DEFAULT_FILTERS });
    this.load();
  }

  protected goTo(page: number): void {
    this.filters.update((f) => ({ ...f, page }));
    this.load();
  }

  protected export(): void {
    this.exporting.set(true);
    this.api.exportAuditLogs(this.filters()).subscribe({
      next: (blob) => {
        this.exporting.set(false);
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'journal-administration.csv';
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (error: unknown) => {
        this.exporting.set(false);
        this.error.set(apiErrorMessage(error, 'L’export a échoué.'));
      },
    });
  }

  protected targetLabel(type: string): string {
    return TARGET_TYPES[type] ?? type;
  }
}
