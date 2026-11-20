import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { AdminApiService } from '../admin-api.service';
import { ROLE_LABELS } from '../admin-labels';
import { AdminAssociationDetail, AdminPerson } from '../admin.model';
import { AdminConfirmDialogComponent } from '../confirm/admin-confirm-dialog.component';
import { AdminAccountPickerComponent } from '../shared/admin-account-picker.component';
import { ActionConfig, AdminDetailPage } from '../shared/admin-detail-page';
import { AdminStatusPillComponent } from '../shared/admin-status-pill.component';

type Action = { kind: 'archive' | 'restore' } | { kind: 'transfer'; from: AdminPerson };

/** Fiche d'une association : responsables, caisses, chiffres et soldes ; archivage, restauration, transfert. */
@Component({
  selector: 'app-admin-association-detail',
  standalone: true,
  imports: [DatePipe, CurrencyPipe, RouterLink, AdminConfirmDialogComponent, AdminAccountPickerComponent, AdminStatusPillComponent],
  templateUrl: './admin-association-detail.component.html',
  styleUrls: ['../admin-common.scss', '../admin-detail.scss'],
})
export class AdminAssociationDetailComponent extends AdminDetailPage<AdminAssociationDetail, Action> {
  private readonly api = inject(AdminApiService);

  protected readonly roles = ROLE_LABELS;
  protected readonly association = this.item;

  /** Responsable remplacé, pendant un transfert. */
  protected readonly transferFrom = computed(() => {
    const action = this.action();
    return action?.kind === 'transfer' ? action.from : null;
  });

  protected readonly config = computed<ActionConfig | null>(() => {
    const association = this.association();
    const action = this.action();
    if (!association || !action) return null;
    switch (action.kind) {
      case 'archive':
        return { title: `Archiver ${association.name}`, confirmLabel: 'Archiver l’association', danger: true,
          impact: [`${this.people(association.responsibles.length, 'responsable')} et les trésoriers garderont la consultation et les exports.`,
            'Plus aucune opération ne sera possible (membres, paiements, remises, campagnes).',
            'Rien n’est supprimé : l’association peut être restaurée.'] };
      case 'restore':
        return { title: `Restaurer ${association.name}`, confirmLabel: 'Restaurer l’association', danger: false,
          impact: ['Ses responsables et trésoriers pourront de nouveau enregistrer des opérations.'] };
      default:
        return { title: `Transférer ${association.name}`, confirmLabel: 'Transférer', danger: true,
          impact: [`${this.name(action.from)} perdra immédiatement ses droits de responsable sur cette association.`,
            'Le nouveau responsable doit être un compte responsable actif ; il obtiendra tous les droits de gestion.'] };
    }
  });

  constructor() {
    super('Cette association n’a pas pu être chargée.');
  }

  protected fetch(id: string): Observable<AdminAssociationDetail> {
    return this.api.association(id);
  }

  protected needsTarget(action: Action | null): boolean {
    return action?.kind === 'transfer';
  }

  protected confirm(reason: string): void {
    const association = this.association();
    const action = this.action();
    if (!association || !action) return;
    if (action.kind === 'transfer') {
      const target = this.target();
      if (!target) return;
      this.run(this.api.transferAssociation(association.id, action.from.userId, target.id, reason),
        `Association transférée à ${target.displayName || 'ce compte'}.`);
      return;
    }
    const archived = action.kind === 'archive';
    this.run(this.api.setAssociationArchived(association.id, archived, reason),
      archived ? 'Association archivée : elle est en lecture seule.' : 'Association restaurée.');
  }

  protected responsibleIds(association: AdminAssociationDetail): string[] {
    return association.responsibles.map((person) => person.userId);
  }

  protected name(person: AdminPerson): string {
    return person.displayName || person.email || person.phone || 'Ce compte';
  }

  protected treasurerNames(people: AdminPerson[]): string {
    return people.length ? people.map((person) => this.name(person)).join(', ') : 'Sans trésorier';
  }

  private people(count: number, noun: string): string {
    return count > 1 ? `Les ${count} ${noun}s` : `Le ${noun}`;
  }
}
