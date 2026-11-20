import { Component, computed, input } from '@angular/core';
import { archiveStatus } from '../admin-labels';
import { AdminStatus } from '../admin.model';

/** Statut d'une association ou d'une caisse : pastille + icône + libellé (jamais la couleur seule). */
@Component({
  selector: 'app-admin-status-pill',
  standalone: true,
  template: `<span [class]="'adm-pill ' + view().css"><span class="adm-pill-icon" aria-hidden="true">{{ view().icon }}</span>{{ view().label }}</span>`,
  styleUrls: ['../admin-common.scss'],
  styles: [':host { display: inline-block; }'],
})
export class AdminStatusPillComponent {
  readonly status = input.required<AdminStatus>();
  protected readonly view = computed(() => archiveStatus(this.status()));
}
