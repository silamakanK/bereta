import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AdminApiService, queryParams } from './admin-api.service';
import { accountStatus, actionLabel, archiveStatus, describeValues } from './admin-labels';

describe('AdminApiService', () => {
  let api: AdminApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(AdminApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('drops empty filters from the query string', () => {
    expect(queryParams({ q: '', role: 'ADMIN', page: 0, enabled: null }).toString()).toBe('role=ADMIN&page=0');
  });

  it('lists, reads and acts on accounts with a reason', () => {
    api.users({ q: 'awa', role: '', enabled: 'true', sort: 'name', direction: 'asc', page: 1, size: 20 }).subscribe();
    expect(http.expectOne((r) => r.url === '/api/admin/users').request.params.toString())
      .toBe('q=awa&enabled=true&sort=name&direction=asc&page=1&size=20');

    api.user('u1').subscribe();
    http.expectOne('/api/admin/users/u1').flush({});

    const calls: [() => { subscribe: () => unknown }, string][] = [
      [() => api.disableUser('u1', 'Motif suffisant'), 'disable'],
      [() => api.enableUser('u1', 'Motif suffisant'), 'enable'],
      [() => api.resetPassword('u1', 'Motif suffisant'), 'reset-password'],
    ];
    for (const [call, path] of calls) {
      call().subscribe();
      const request = http.expectOne(`/api/admin/users/u1/${path}`);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ reason: 'Motif suffisant' });
      request.flush({});
    }
  });

  it('lists associations and funds and acts on them with a reason', () => {
    api.associations({ q: 'sig', status: 'ARCHIVED', sort: 'name', direction: 'asc', page: 0, size: 20 }).subscribe();
    expect(http.expectOne((r) => r.url === '/api/admin/associations').request.params.get('status')).toBe('ARCHIVED');
    api.association('a1').subscribe();
    http.expectOne('/api/admin/associations/a1').flush({});
    api.funds({ q: '', status: '', associationId: 'a1', sort: 'createdAt', direction: 'desc', page: 0, size: 20 }).subscribe();
    expect(http.expectOne((r) => r.url === '/api/admin/funds').request.params.get('associationId')).toBe('a1');
    api.fund('f1').subscribe();
    http.expectOne('/api/admin/funds/f1').flush({});

    const calls: [() => { subscribe: () => unknown }, string, object][] = [
      [() => api.setAssociationArchived('a1', true, 'Motif suffisant'), '/api/admin/associations/a1/archive', { reason: 'Motif suffisant' }],
      [() => api.setAssociationArchived('a1', false, 'Motif suffisant'), '/api/admin/associations/a1/restore', { reason: 'Motif suffisant' }],
      [() => api.transferAssociation('a1', 'u1', 'u2', 'Motif suffisant'), '/api/admin/associations/a1/transfer',
        { fromUserId: 'u1', toUserId: 'u2', reason: 'Motif suffisant' }],
      [() => api.setFundArchived('f1', true, 'Motif suffisant'), '/api/admin/funds/f1/archive', { reason: 'Motif suffisant' }],
      [() => api.setFundArchived('f1', false, 'Motif suffisant'), '/api/admin/funds/f1/restore', { reason: 'Motif suffisant' }],
      [() => api.reassignTreasurer('f1', 'u2', null, 'Motif suffisant'), '/api/admin/funds/f1/reassign-treasurer',
        { toUserId: 'u2', replaceUserId: null, reason: 'Motif suffisant' }],
    ];
    for (const [call, url, body] of calls) {
      call().subscribe();
      const request = http.expectOne(url);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual(body);
      request.flush({});
    }
    expect(archiveStatus('ACTIVE').label).toBe('Active');
    expect(archiveStatus('ARCHIVED')).toEqual({ css: 'off', icon: '▪', label: 'Archivée' });
    expect(actionLabel('TREASURER_REASSIGNED')).toBe('Trésorier réaffecté');
  });

  it('reads and exports the audit log without pagination in the export', () => {
    const filters = { action: 'USER_DISABLED', targetType: '', targetId: '', associationId: '', from: '2026-11-01', to: '', page: 2, size: 25 };
    api.auditLogs(filters).subscribe();
    expect(http.expectOne((r) => r.url === '/api/admin/audit-logs').request.params.get('page')).toBe('2');
    api.exportAuditLogs(filters).subscribe();
    const exported = http.expectOne((r) => r.url === '/api/admin/audit-logs/export');
    expect(exported.request.params.toString()).toBe('action=USER_DISABLED&from=2026-11-01');
    expect(exported.request.responseType).toBe('blob');
  });
});

describe('admin labels', () => {
  it('describes statuses, actions and values', () => {
    expect(accountStatus(true).label).toBe('Actif');
    expect(accountStatus(false).css).toBe('off');
    expect(actionLabel('USER_DISABLED')).toBe('Compte désactivé');
    expect(actionLabel('INCONNUE')).toBe('INCONNUE');
    expect(describeValues({ enabled: false, closedSessions: 2 })).toBe('enabled : false, closedSessions : 2');
    expect(describeValues({})).toBe('—');
    expect(describeValues(null)).toBe('—');
  });
});
