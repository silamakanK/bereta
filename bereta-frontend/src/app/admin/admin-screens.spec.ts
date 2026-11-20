import { registerLocaleData } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../auth/auth.service';
import { AdminApiService } from './admin-api.service';
import { AdminAuditEntry, AdminPage, AdminUserDetail, AdminUserSummary } from './admin.model';
import { AdminShellComponent } from './admin-shell.component';
import { AdminAuditComponent } from './audit/admin-audit.component';
import { AdminUserDetailComponent } from './users/admin-user-detail.component';
import { AdminUsersComponent } from './users/admin-users.component';

registerLocaleData(localeFr, 'fr-FR');

function page<T>(items: T[], extra: Partial<AdminPage<T>> = {}): AdminPage<T> {
  return { items, page: 0, size: 20, totalItems: items.length, totalPages: 1, ...extra };
}

const SUMMARY: AdminUserSummary = {
  id: 'u1', displayName: 'Awa Diallo', phone: '06 •• •• •• 78', email: 'aw•••@mail.fr', role: 'TRESORIER',
  enabled: true, mustChangePassword: true, createdAt: '2026-11-01T10:00:00Z',
};

const DETAIL: AdminUserDetail = {
  id: 'u1', displayName: 'Awa Diallo', phone: '06 12 34 56 78', email: 'awa@mail.fr', role: 'TRESORIER',
  enabled: true, mustChangePassword: false, createdAt: '2026-11-01T10:00:00Z', lastLoginAt: '2026-11-19T08:00:00Z',
  activeSessions: 2, associations: [{ id: 'a1', name: 'Sigida', status: 'ACTIVE' }],
  funds: [{ membershipId: 'm1', role: 'TRESORIER', associationId: 'a1', associationName: 'Sigida', primaryFundName: 'Principale', localFundId: 'l1', localFundName: 'Kayes' }],
};

const ENTRY: AdminAuditEntry = {
  id: 'e1', actorId: 'admin', actorName: 'Admin', action: 'USER_DISABLED', targetType: 'USER', targetId: 'u1',
  associationId: null, associationName: 'Sigida', reason: 'Demande écrite', details: null,
  before: { enabled: true }, after: { enabled: false }, ipAddress: '10.0.0.1', createdAt: '2026-11-19T08:00:00Z',
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Api = Record<string, ReturnType<typeof vi.fn>>;

describe('Espace administrateur — écrans', () => {
  let api: Api;
  const auth = { currentUser: signal<{ id: string; displayName: string | null; email: string | null; phone: string | null } | null>({ id: 'admin', displayName: 'Admin', email: null, phone: null }), logout: vi.fn(() => of(undefined)) };

  beforeEach(async () => {
    api = {
      users: vi.fn(() => of(page([SUMMARY], { totalPages: 3, totalItems: 41 }))),
      user: vi.fn(() => of(DETAIL)),
      disableUser: vi.fn(() => of({ user: SUMMARY, closedSessions: 2 })),
      enableUser: vi.fn(() => of({ user: SUMMARY, closedSessions: 0 })),
      resetPassword: vi.fn(() => of({ temporaryPassword: 'abcd2efghj', closedSessions: 1 })),
      auditLogs: vi.fn(() => of(page([ENTRY], { totalPages: 2, totalItems: 30 }))),
      exportAuditLogs: vi.fn(() => of(new Blob(['csv']))),
    };
    await TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AdminApiService, useValue: api }, { provide: AuthService, useValue: auth }],
    }).compileComponents();
  });

  afterEach(() => vi.useRealTimers());

  it('shows the permanent banner and logs out', async () => {
    const fixture = TestBed.createComponent(AdminShellComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Espace administrateur');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (fixture.componentInstance as any).logout();
    expect(navigate).toHaveBeenCalledWith('/connexion');
  });

  it('searches, filters, sorts and paginates accounts on the server', async () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(AdminUsersComponent);
    fixture.detectChanges();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const c = fixture.componentInstance as any;
    expect(fixture.nativeElement.textContent).toContain('06 •• •• •• 78');
    expect(fixture.nativeElement.textContent).toContain('Mot de passe provisoire');

    c.search('awa');
    vi.advanceTimersByTime(300);
    c.setFilter('role', 'TRESORIER');
    c.setFilter('enabled', 'false');
    expect(c.chips().map((chip: { label: string }) => chip.label)).toEqual(['« awa »', 'Rôle : Trésorier', 'Désactivés']);
    c.sortBy('name');
    expect(c.sortIndicator('name')).toBe('▲');
    c.sortBy('name');
    expect(c.sortIndicator('name')).toBe('▼');
    expect(c.sortIndicator('role')).toBe('');
    c.goTo(2);
    expect(api['users']).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'awa', role: 'TRESORIER', enabled: 'false', sort: 'name', direction: 'desc', page: 2 }));
    c.clearChip('role');
    expect(c.filters().role).toBe('');
    c.reset();
    expect(c.chips()).toEqual([]);
    expect(c.roleLabel('ADMIN')).toBe('Administrateur');

    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    c.open(SUMMARY);
    expect(navigate).toHaveBeenCalledWith(['/admin/comptes', 'u1']);

    api['users'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    c.load();
    expect(c.error()).toContain('n’ont pas pu être chargés');
    fixture.destroy();
  });

  it('shows an empty state when no account matches', async () => {
    api['users'].mockReturnValue(of(page([])));
    const fixture = TestBed.createComponent(AdminUsersComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Aucun compte ne correspond');
  });

  function detail() {
    const fixture = TestBed.createComponent(AdminUserDetailComponent);
    fixture.componentRef.setInput('id', 'u1');
    fixture.detectChanges();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return { fixture, c: fixture.componentInstance as any };
  }

  it('shows full contacts and disables an account after a reasoned confirmation', () => {
    const { fixture, c } = detail();
    expect(fixture.nativeElement.textContent).toContain('06 12 34 56 78');
    expect(fixture.nativeElement.textContent).toContain('Kayes');

    c.ask('disable');
    expect(c.config().impact).toContain('2 sessions seront fermées.');
    expect(c.config().danger).toBe(true);
    c.confirm('Demande écrite du responsable');
    expect(api['disableUser']).toHaveBeenCalledWith('u1', 'Demande écrite du responsable');
    expect(c.message()).toBe('Compte désactivé. 2 sessions seront fermées.');
    expect(c.action()).toBeNull();

    c.ask('enable');
    expect(c.config().danger).toBe(false);
    c.confirm('Réactivation demandée par le responsable');
    expect(c.message()).toBe('Compte réactivé.');

    api['disableUser'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { detail: 'Déjà désactivé.' } })));
    c.ask('disable');
    c.confirm('Encore une demande écrite');
    expect(c.actionError()).toBe('Déjà désactivé.');
    c.cancel();
    expect(c.config()).toBeNull();
  });

  it('shows the temporary password once, copies it and forgets it', async () => {
    const { fixture, c } = detail();
    const writeText = vi.fn(() => Promise.resolve());
    Object.assign(navigator, { clipboard: { writeText } });

    c.ask('reset');
    expect(c.config().impact).toContain('2 sessions seront fermées.');
    c.confirm('Accès perdu, identité vérifiée');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('abcd2efghj');
    expect(c.message()).toBe('Mot de passe provisoire généré. 1 session sera fermée.');
    await c.copyPassword();
    expect(writeText).toHaveBeenCalledWith('abcd2efghj');
    expect(c.copied()).toBe(true);

    c.forgetPassword();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('abcd2efghj');
    await c.copyPassword();
    expect(writeText).toHaveBeenCalledTimes(1);

    writeText.mockRejectedValueOnce(new Error('refusé'));
    c.temporaryPassword.set('x');
    await c.copyPassword();
    expect(c.copied()).toBe(false);

    api['resetPassword'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    c.ask('reset');
    c.confirm('Accès perdu, identité vérifiée');
    expect(c.actionError()).toContain('L’action a échoué');
  });

  it('hides actions on the administrator’s own account and reports loading errors', () => {
    api['user'].mockReturnValue(of({ ...DETAIL, id: 'admin', activeSessions: 0, lastLoginAt: null, displayName: null, email: null, phone: null, associations: [], funds: [] }));
    const { fixture, c } = detail();
    expect(c.isSelf()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('procédure interne');
    expect(c.name(c.user())).toBe('ce compte');
    c.ask('disable');
    expect(c.config().impact).toContain('Aucune session ouverte.');
    c.confirm('Ne devrait pas passer');
    expect(api['disableUser']).toHaveBeenCalled();

    api['user'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404, error: { detail: 'Compte utilisateur introuvable.' } })));
    c.load();
    expect(c.error()).toBe('Compte utilisateur introuvable.');
  });

  it('filters the audit log, paginates and exports it', () => {
    const fixture = TestBed.createComponent(AdminAuditComponent);
    fixture.detectChanges();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const c = fixture.componentInstance as any;
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Compte désactivé');
    expect(text).toContain('« Demande écrite »');
    expect(text).toContain('Avant : enabled : true');

    for (const [key, value] of [['action', 'USER_DISABLED'], ['targetType', 'USER'], ['targetId', 'u1'], ['associationId', 'a1'], ['from', '2026-11-01'], ['to', '2026-11-30']]) {
      c.setFilter(key, value);
    }
    expect(c.chips()).toHaveLength(6);
    c.clearChip('to');
    expect(c.chips()).toHaveLength(5);
    c.goTo(1);
    expect(api['auditLogs']).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, action: 'USER_DISABLED' }));
    expect(c.targetLabel('USER')).toBe('Compte');
    expect(c.targetLabel('AUTRE')).toBe('AUTRE');

    URL.createObjectURL = vi.fn(() => 'blob:csv');
    URL.revokeObjectURL = vi.fn();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    c.export();
    expect(click).toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:csv');

    api['exportAuditLogs'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    c.export();
    expect(c.error()).toContain('L’export a échoué');
    api['auditLogs'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    c.reset();
    expect(c.error()).toContain('n’a pas pu être chargé');

    api['auditLogs'].mockReturnValue(of(page([])));
    c.reset();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Aucune action enregistrée');
  });
});
