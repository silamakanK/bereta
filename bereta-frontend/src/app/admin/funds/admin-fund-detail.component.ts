import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { AdminApiService } from '../admin-api.service';
import { AdminFundDetail, AdminPerson } from '../admin.model';
import { AdminConfirmDialogComponent } from '../confirm/admin-confirm-dialog.component';
import { AdminAccountPickerComponent } from '../shared/admin-account-picker.component';
import { ActionConfig, AdminDetailPage } from '../shared/admin-detail-page';
import { AdminStatusPillComponent } from '../shared/admin-status-pill.component';

/** replaced vide : affectation d'un trésorier sans en remplacer un autre. */
type Action = { kind: 'archive' | 'restore' } | { kind: 'reassign'; replaced: AdminPerson | null };

/** Fiche d'une caisse locale : trésoriers, argent ; archivage, restauration, réaffectation du trésorier. */
@Component({
  selector: 'app-admin-fund-detail',
  standalone: true,
  imports: [DatePipe, CurrencyPipe, RouterLink, AdminConfirmDialogComponent, AdminAccountPickerComponent, AdminStatusPillComponent],
  templateUrl: './admin-fund-detail.component.html',
  styleUrls: ['../admin-common.scss', '../admin-detail.scss'],
})
export class AdminFundDetailComponent extends AdminDetailPage<AdminFundDetail, Action> {
  private readonly api = inject(AdminApiService);

  protected readonly fund = this.item;
  protected readonly reassigning = computed(() => this.action()?.kind === 'reassign');

  protected readonly config = computed<ActionConfig | null>(() => {
    const fund = this.fund();
    const action = this.action();
    if (!fund || !action) return null;
    switch (action.kind) {
      case 'archive':
        return { title: `Archiver ${fund.name}`, confirmLabel: 'Archiver la caisse', danger: true,
          impact: ['La caisse reste consultable, mais plus aucun membre, paiement ou remise n’y sera enregistré.',
            'Ses membres ne seront plus inscrits aux nouvelles campagnes.',
            'Refusé s’il reste des espèces sur place ou une remise à confirmer.'] };
      case 'restore':
        return { title: `Restaurer ${fund.name}`, confirmLabel: 'Restaurer la caisse', danger: false,
          impact: ['La caisse pourra de nouveau enregistrer des opérations.'] };
      default:
        return action.replaced
          ? { title: `Remplacer ${this.name(action.replaced)}`, confirmLabel: 'Remplacer le trésorier', danger: true,
            impact: [`${this.name(action.replaced)} perdra immédiatement l’accès à cette caisse.`,
              'Les encaissements passés gardent leur auteur et leur caisse d’origine.'] }
          : { title: `Affecter un trésorier à ${fund.name}`, confirmLabel: 'Affecter', danger: false,
            impact: ['Le compte choisi pourra enregistrer membres, paiements et remises dans cette caisse.'] };
    }
  });

  constructor() {
    super('Cette caisse n’a pas pu être chargée.');
  }

  protected fetch(id: string): Observable<AdminFundDetail> {
    return this.api.fund(id);
  }

  protected needsTarget(action: Action | null): boolean {
    return action?.kind === 'reassign';
  }

  protected confirm(reason: string): void {
    const fund = this.fund();
    const action = this.action();
    if (!fund || !action) return;
    if (action.kind === 'reassign') {
      const target = this.target();
      if (!target) return;
      this.run(this.api.reassignTreasurer(fund.id, target.id, action.replaced?.userId ?? null, reason),
        `${target.displayName || 'Ce compte'} est maintenant trésorier de la caisse.`);
      return;
    }
    const archived = action.kind === 'archive';
    this.run(this.api.setFundArchived(fund.id, archived, reason),
      archived ? 'Caisse archivée : elle est en lecture seule.' : 'Caisse restaurée.');
  }

  protected treasurerIds(fund: AdminFundDetail): string[] {
    return fund.treasurers.map((person) => person.userId);
  }

  protected name(person: AdminPerson): string {
    return person.displayName || person.email || person.phone || 'Ce compte';
  }
}
