import { registerLocaleData } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminApiService } from './admin-api.service';
import {
  AdminAssociationDetail, AdminAssociationSummary, AdminFundDetail, AdminFundSummary, AdminPage, AdminPerson,
  AdminUserSummary,
} from './admin.model';
import { AdminAssociationDetailComponent } from './associations/admin-association-detail.component';
import { AdminAssociationsComponent } from './associations/admin-associations.component';
import { AdminAuditComponent } from './audit/admin-audit.component';
import { AdminFundDetailComponent } from './funds/admin-fund-detail.component';
import { AdminFundsComponent } from './funds/admin-funds.component';
import { AdminAccountPickerComponent } from './shared/admin-account-picker.component';

registerLocaleData(localeFr, 'fr-FR');

function page<T>(items: T[], extra: Partial<AdminPage<T>> = {}): AdminPage<T> {
  return { items, page: 0, size: 20, totalItems: items.length, totalPages: 1, ...extra };
}

const PERSON: AdminPerson = { userId: 'u1', displayName: 'Awa Diallo', phone: '06 12 34 56 78', email: 'awa@mail.fr', role: 'RESPONSABLE', enabled: true };
const OTHER: AdminUserSummary = {
  id: 'u2', displayName: 'Moussa Keita', phone: '06 •• •• •• 11', email: 'mo•••@mail.fr', role: 'RESPONSABLE',
  enabled: true, mustChangePassword: false, createdAt: '2026-11-01T10:00:00Z',
};
const BALANCE = { collected: 120, availableLocally: 20, inTransit: 0 };

const ASSOCIATION_ROW: AdminAssociationSummary = {
  id: 'a1', name: 'Sigida', status: 'ACTIVE', createdAt: '2026-11-01T10:00:00Z', responsibleName: 'Awa Diallo', localFundCount: 2, memberCount: 30,
};
const ASSOCIATION: AdminAssociationDetail = {
  id: 'a1', name: 'Sigida', description: null, contactEmail: null, contactPhone: null, address: null, status: 'ACTIVE',
  createdAt: '2026-11-01T10:00:00Z', responsibles: [PERSON], primaryFundId: 'p1', primaryFundName: 'Caisse principale',
  localFunds: [{ id: 'f1', name: 'Caisse Kayes', location: 'Kayes', status: 'ACTIVE', treasurers: [], memberCount: 1, balance: BALANCE },
    { id: 'f2', name: 'Caisse Bamako', location: null, status: 'ARCHIVED', treasurers: [{ ...PERSON, displayName: null, email: null, phone: null }], memberCount: 3, balance: BALANCE }],
  memberCount: 30, activeMemberCount: 28, campaignCount: 2, openCampaignCount: 1,
  balances: { totalCollected: 500, central: 400, inTransit: 50, availableLocally: 50 },
};
const FUND_ROW: AdminFundSummary = {
  id: 'f1', name: 'Caisse Kayes', location: null, status: 'ACTIVE', associationId: 'a1', associationName: 'Sigida',
  associationStatus: 'ACTIVE', treasurerNames: ['Awa Diallo'], balance: BALANCE, createdAt: '2026-11-01T10:00:00Z',
};
const FUND: AdminFundDetail = {
  id: 'f1', name: 'Caisse Kayes', location: 'Kayes', description: null, status: 'ACTIVE', associationId: 'a1',
  associationName: 'Sigida', associationStatus: 'ARCHIVED', primaryFundName: 'Caisse principale',
  createdAt: '2026-11-01T10:00:00Z', treasurers: [{ ...PERSON, role: 'TRESORIER' }], memberCount: 4, balance: BALANCE,
};

type Api = Record<string, ReturnType<typeof vi.fn>>;

describe('Espace administrateur — associations et caisses', () => {
  let api: Api;

  beforeEach(async () => {
    api = {
      users: vi.fn(() => of(page([OTHER, { ...OTHER, id: 'u1' }, { ...OTHER, id: 'adm', role: 'ADMIN' }]))),
      associations: vi.fn(() => of(page([ASSOCIATION_ROW], { totalPages: 2, totalItems: 25 }))),
      association: vi.fn(() => of(ASSOCIATION)),
      setAssociationArchived: vi.fn((_id: string, archived: boolean) => of({ ...ASSOCIATION, status: archived ? 'ARCHIVED' : 'ACTIVE' })),
      transferAssociation: vi.fn(() => of(ASSOCIATION)),
      funds: vi.fn(() => of(page([FUND_ROW, { ...FUND_ROW, id: 'f2', treasurerNames: [], status: 'ARCHIVED' }]))),
      fund: vi.fn(() => of(FUND)),
      setFundArchived: vi.fn((_id: string, archived: boolean) => of({ ...FUND, status: archived ? 'ARCHIVED' : 'ACTIVE' })),
      reassignTreasurer: vi.fn(() => of(FUND)),
      auditLogs: vi.fn(() => of(page([]))),
    };
    await TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AdminApiService, useValue: api }],
    }).compileComponents();
  });

  afterEach(() => vi.useRealTimers());

  it('lists associations with filters, sort and server pagination', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(AdminAssociationsComponent);
    fixture.detectChanges();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const c = fixture.componentInstance as any;
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Sigida');
    expect(text).toContain('Awa Diallo');
    expect(text).toContain('Active');
    expect(text).toContain('Page 1 / 2');

    c.search('sig');
    vi.advanceTimersByTime(300);
    c.setFilter('status', 'ARCHIVED');
    expect(c.chips().map((chip: { label: string }) => chip.label)).toEqual(['« sig »', 'Archivées']);
    c.sortBy('name');
    c.goTo(1);
    expect(api['associations']).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'sig', status: 'ARCHIVED', sort: 'name', direction: 'asc', page: 1 }));
    c.setFilter('status', 'ACTIVE');
    expect(c.chips()[1].label).toBe('Actives');
    c.clearChip('status');
    c.reset();
    expect(c.chips()).toEqual([]);

    api['associations'].mockReturnValue(of(page([])));
    c.load();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Aucune association ne correspond');
    api['associations'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    c.load();
    expect(c.error()).toContain('n’ont pas pu être chargées');
    fixture.destroy();
  });

  function associationDetail() {
    const fixture = TestBed.createComponent(AdminAssociationDetailComponent);
    fixture.componentRef.setInput('id', 'a1');
    fixture.detectChanges();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return { fixture, c: fixture.componentInstance as any };
  }

  it('shows an association with its funds and balances, then archives and restores it with a reason', () => {
    const { fixture, c } = associationDetail();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Caisse Kayes');
    expect(text).toContain('Sans trésorier');
    expect(text).toContain('500,00');
    expect(text).toContain('28 / 30');
    expect(c.treasurerNames(ASSOCIATION.localFunds[1].treasurers)).toBe('Ce compte');

    c.ask({ kind: 'archive' });
    expect(c.config().danger).toBe(true);
    expect(c.config().impact[0]).toContain('Le responsable');
    c.confirm('Demande écrite du bureau');
    expect(api['setAssociationArchived']).toHaveBeenCalledWith('a1', true, 'Demande écrite du bureau');
    expect(c.message()).toContain('archivée');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Association archivée');

    c.ask({ kind: 'restore' });
    expect(c.config().danger).toBe(false);
    c.confirm('Erreur corrigée, restauration');
    expect(c.message()).toBe('Association restaurée.');

    api['setAssociationArchived'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { detail: 'Déjà active.' } })));
    c.ask({ kind: 'restore' });
    c.confirm('Encore une restauration');
    expect(c.actionError()).toBe('Déjà active.');
    c.cancel();
    expect(c.config()).toBeNull();
  });

  it('transfers an association only once a new responsible is chosen', () => {
    const { fixture, c } = associationDetail();
    c.ask({ kind: 'transfer', from: PERSON });
    fixture.detectChanges();
    expect(c.config().title).toBe('Transférer Sigida');
    expect(c.config().impact[0]).toContain('Awa Diallo perdra');
    expect(c.ready()).toBe(false);
    expect(c.responsibleIds(ASSOCIATION)).toEqual(['u1']);
    c.confirm('Transfert demandé par écrit');
    expect(api['transferAssociation']).not.toHaveBeenCalled();

    c.target.set(OTHER);
    expect(c.ready()).toBe(true);
    c.confirm('Transfert demandé par écrit');
    expect(api['transferAssociation']).toHaveBeenCalledWith('a1', 'u1', 'u2', 'Transfert demandé par écrit');
    expect(c.message()).toBe('Association transférée à Moussa Keita.');
    expect(c.name({ ...PERSON, displayName: null, email: null, phone: null })).toBe('Ce compte');
    ASSOCIATION.responsibles.push({ ...PERSON, userId: 'u3' });
    c.load();
    c.ask({ kind: 'archive' });
    expect(c.config().impact[0]).toContain('Les 2 responsables');
    ASSOCIATION.responsibles.pop();

    api['association'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404, error: { detail: 'Association introuvable.' } })));
    c.load();
    expect(c.error()).toBe('Association introuvable.');
  });

  it('lists funds, pre-filtered by association from the query string', () => {
    const fixture = TestBed.createComponent(AdminFundsComponent);
    fixture.componentRef.setInput('associationId', 'a1');
    fixture.detectChanges();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const c = fixture.componentInstance as any;
    expect(api['funds']).toHaveBeenCalledWith(expect.objectContaining({ associationId: 'a1' }));
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Caisse Kayes');
    expect(text).toContain('Sans trésorier');
    expect(text).toContain('Archivée');
    c.search('kay');
    c.setFilter('status', 'ACTIVE');
    expect(c.chips().map((chip: { label: string }) => chip.label)).toEqual(['« kay »', 'Actives', 'Une association']);
    c.setFilter('status', 'ARCHIVED');
    expect(c.chips()[1].label).toBe('Archivées');
    c.sortBy('association');
    expect(c.sortIndicator('association')).toBe('▲');
    c.reset();
    expect(c.filters().associationId).toBe('');
    fixture.destroy();
  });

  function fundDetail() {
    const fixture = TestBed.createComponent(AdminFundDetailComponent);
    fixture.componentRef.setInput('id', 'f1');
    fixture.detectChanges();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return { fixture, c: fixture.componentInstance as any };
  }

  it('archives, restores and reassigns the treasurer of a fund', () => {
    const { fixture, c } = fundDetail();
    expect(fixture.nativeElement.textContent).toContain('Awa Diallo');
    expect(fixture.nativeElement.textContent).toContain('Archivée');

    c.ask({ kind: 'archive' });
    expect(c.config().impact[2]).toContain('espèces');
    c.confirm('Caisse fermée par le bureau');
    expect(api['setFundArchived']).toHaveBeenCalledWith('f1', true, 'Caisse fermée par le bureau');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Caisse archivée');
    c.ask({ kind: 'restore' });
    expect(c.config().danger).toBe(false);
    c.confirm('Réouverture demandée');
    expect(c.message()).toBe('Caisse restaurée.');

    c.ask({ kind: 'reassign', replaced: FUND.treasurers[0] });
    fixture.detectChanges();
    expect(c.config().title).toBe('Remplacer Awa Diallo');
    expect(c.ready()).toBe(false);
    c.confirm('Trésorier parti, remplacement');
    expect(api['reassignTreasurer']).not.toHaveBeenCalled();
    c.target.set(OTHER);
    c.confirm('Trésorier parti, remplacement');
    expect(api['reassignTreasurer']).toHaveBeenCalledWith('f1', 'u2', 'u1', 'Trésorier parti, remplacement');
    expect(c.message()).toContain('Moussa Keita');

    c.ask({ kind: 'reassign', replaced: null });
    expect(c.config().title).toBe('Affecter un trésorier à Caisse Kayes');
    c.target.set({ ...OTHER, displayName: null });
    c.confirm('Nouvelle caisse sans trésorier');
    expect(api['reassignTreasurer']).toHaveBeenLastCalledWith('f1', 'u2', null, 'Nouvelle caisse sans trésorier');
    expect(c.treasurerIds(FUND)).toEqual(['u1']);
    expect(c.name({ ...PERSON, displayName: null })).toBe('awa@mail.fr');

    api['setFundArchived'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { detail: 'Il reste 20 €.' } })));
    c.ask({ kind: 'archive' });
    c.confirm('Caisse fermée par le bureau');
    expect(c.actionError()).toBe('Il reste 20 €.');
    c.cancel();
    api['fund'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    c.load();
    expect(c.error()).toContain('n’a pas pu être chargée');
  });

  it('searches active accounts, hides administrators and excluded ones, and emits the choice', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(AdminAccountPickerComponent);
    fixture.componentRef.setInput('role', 'RESPONSABLE');
    fixture.componentRef.setInput('exclude', ['u1']);
    const picked: (AdminUserSummary | null)[] = [];
    fixture.componentInstance.picked.subscribe((value) => picked.push(value));
    fixture.detectChanges();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const c = fixture.componentInstance as any;

    c.search('m');
    expect(c.results()).toEqual([]);
    c.search('mou');
    vi.advanceTimersByTime(300);
    expect(api['users']).toHaveBeenCalledWith(expect.objectContaining({ q: 'mou', role: 'RESPONSABLE', enabled: 'true', size: 8 }));
    expect(c.results().map((user: AdminUserSummary) => user.id)).toEqual(['u2']);
    fixture.detectChanges();
    const radio: HTMLInputElement = fixture.nativeElement.querySelector('input[type=radio]');
    radio.dispatchEvent(new Event('change'));
    expect(picked.at(-1)).toEqual(OTHER);
    expect(c.selected()).toBe('u2');

    api['users'].mockReturnValue(of(page([])));
    c.search('zzz');
    vi.advanceTimersByTime(300);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Aucun compte actif ne correspond');
    expect(picked.at(-1)).toBeNull();
    api['users'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    c.search('err');
    vi.advanceTimersByTime(300);
    expect(c.error()).toBe('La recherche a échoué.');
    fixture.destroy();
  });

  it('opens the audit log pre-filtered from a detail page link', () => {
    const fixture = TestBed.createComponent(AdminAuditComponent);
    fixture.componentRef.setInput('associationId', 'a1');
    fixture.componentRef.setInput('targetId', 'f1');
    fixture.detectChanges();
    expect(api['auditLogs']).toHaveBeenCalledWith(expect.objectContaining({ associationId: 'a1', targetId: 'f1' }));
  });
});
