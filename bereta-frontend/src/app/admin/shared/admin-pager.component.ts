import { Component, input, output } from '@angular/core';
import { AdminPage } from '../admin.model';

/** Pagination des listes : affichée seulement s'il y a plusieurs pages. */
@Component({
  selector: 'app-admin-pager',
  standalone: true,
  template: `
    @if (page().totalPages > 1) {
      <nav class="adm-pages" aria-label="Pagination">
        <span>Page {{ page().page + 1 }} / {{ page().totalPages }}</span>
        <div><button type="button" class="adm-btn" [disabled]="page().page === 0" (click)="go.emit(page().page - 1)">← Précédent</button>
          <button type="button" class="adm-btn" [disabled]="page().page + 1 >= page().totalPages" (click)="go.emit(page().page + 1)">Suivant →</button></div>
      </nav>
    }`,
  styleUrls: ['../admin-common.scss'],
})
export class AdminPagerComponent {
  readonly page = input.required<AdminPage<unknown>>();
  readonly go = output<number>();
}
