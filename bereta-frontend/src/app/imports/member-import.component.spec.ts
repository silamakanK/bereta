import { registerLocaleData } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LocalFund } from '../funds/fund.model';
import { ImportPreview, ImportRow, ImportValidation } from './import.model';
import { ImportService } from './import.service';
import { MemberImportComponent } from './member-import.component';

registerLocaleData(localeFr, 'fr-FR');

const FUNDS = [
  { id: 'kayes', name: 'Caisse Kayes' },
  { id: 'bamako', name: 'Caisse Bamako' },
] as LocalFund[];

function row(overrides: Partial<ImportRow> = {}): ImportRow {
  return {
    id: 'r1', lineNumber: 1, readFirstName: 'fatou', readLastName: 'KAMISSOKO', readPhone: '06 12 34 56 78',
    firstName: 'Fatou', lastName: 'Kamissoko', phone: '06 12 34 56 78', phoneE164: '+33612345678',
    confidence: null, remark: null, status: 'PRETE', issues: [], confirmable: true, included: true, confirmed: false,
    duplicateMember: null, duplicateOfLine: null, duplicateAction: null, ...overrides,
  };
}

function preview(rows: ImportRow[], counters: Partial<ImportPreview['counters']> = {}, extra: Partial<ImportPreview> = {}): ImportPreview {
  return {
    id: 'imp-1', status: 'BROUILLON', sourceType: 'CSV',
    fund: { primaryFundId: 'p', localFundId: null, name: 'Caisse principale', primary: true },
    createdAt: '2026-11-16T10:00:00Z', alreadyImportedAt: null, currentCampaignName: 'Cotisation 2026',
    counters: { total: rows.length, ready: rows.length, toCheck: 0, duplicates: 0, excluded: 0, toCreate: rows.length, ignored: 0, ...counters },
    rows, ...extra,
  };
}

const VALIDATION: ImportValidation = {
  id: 'imp-1', created: 2, ignored: 1, enrolled: 2, campaignName: 'Cotisation 2026',
  fund: { primaryFundId: 'p', localFundId: null, name: 'Caisse principale', primary: true },
};

function fileEvent(...files: File[]): Event {
  return { target: { files, value: 'x' } } as unknown as Event;
}

describe('MemberImportComponent', () => {
  let fixture: ComponentFixture<MemberImportComponent>;
  // Accès aux membres protégés du composant, testés comme le ferait le gabarit.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let c: any;
  let api: { analyse: ReturnType<typeof vi.fn>; updateRows: ReturnType<typeof vi.fn>; validate: ReturnType<typeof vi.fn>; cancel: ReturnType<typeof vi.fn> };

  async function create(responsible: boolean, funds = FUNDS, defaultFund: string | null = null): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [MemberImportComponent],
      providers: [{ provide: ImportService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(MemberImportComponent);
    fixture.componentRef.setInput('responsible', responsible);
    fixture.componentRef.setInput('localFunds', funds);
    fixture.componentRef.setInput('defaultLocalFundId', defaultFund);
    fixture.detectChanges();
    c = fixture.componentInstance;
  }

  async function analysed(result: ImportPreview): Promise<void> {
    api.analyse.mockReturnValue(of(result));
    c.goToDocument();
    await c.addFiles(fileEvent(new File(['prenom;nom'], 'liste.csv', { type: 'text/csv' })), false);
    c.analyse();
    fixture.detectChanges();
  }

  beforeEach(() => {
    api = { analyse: vi.fn(), updateRows: vi.fn(), validate: vi.fn(), cancel: vi.fn(() => of(undefined)) };
  });

  afterEach(() => vi.restoreAllMocks());

  it('lets a manager choose the primary fund or any local fund', async () => {
    await create(true);
    expect(c.fundChoice()).toBe('PRINCIPALE');
    expect(c.fundOptions().map((o: { name: string }) => o.name)).toEqual(['Caisse principale', 'Caisse Kayes', 'Caisse Bamako']);
    expect(c.fundLocked()).toBe(false);
    expect(fixture.nativeElement.querySelector('select')).not.toBeNull();
  });

  it('locks the fund of a treasurer with a single fund', async () => {
    await create(false, [FUNDS[0]]);
    expect(c.fundChoice()).toBe('kayes');
    expect(c.fundLocked()).toBe(true);
    expect(c.fundName()).toBe('Caisse Kayes');
    expect(fixture.nativeElement.querySelector('input[readonly]').value).toBe('Caisse Kayes');
  });

  it('starts a treasurer of several funds on the active one', async () => {
    await create(false, FUNDS, 'bamako');
    expect(c.fundChoice()).toBe('bamako');
    expect(c.fundLocked()).toBe(false);
  });

  it('refuses files that are too large, unsupported, or too many photos', async () => {
    await create(true);
    c.goToDocument();
    const big = new File(['x'], 'enorme.csv');
    Object.defineProperty(big, 'size', { value: 11 * 1024 * 1024 });
    await c.addFiles(fileEvent(big), false);
    expect(c.error()).toContain('dépasse 10 Mo');

    await c.addFiles(fileEvent(new File(['x'], 'notes.docx')), false);
    expect(c.error()).toContain('Format non pris en charge');

    const photos = Array.from({ length: 6 }, (_, i) => new File(['x'], `p${i}.jpg`, { type: 'image/jpeg' }));
    await c.addFiles(fileEvent(...photos), true);
    expect(c.error()).toContain('5 photos maximum');
    expect(c.pages()).toHaveLength(0);
  });

  it('explains when a photo cannot be read', async () => {
    await create(true);
    c.goToDocument();
    await c.addFiles(fileEvent(new File(['x'], 'IMG.HEIC', { type: 'image/heic' })), true);
    expect(c.error()).toContain('HEIC');
    await c.addFiles(fileEvent(new File(['x'], 'photo.jpg', { type: 'image/jpeg' })), true);
    expect(c.error()).toContain('n’a pas pu être lue');
    expect(c.preparing()).toBe(false);
  });

  it('keeps a single document, and removes it on demand', async () => {
    await create(true);
    c.goToDocument();
    await c.addFiles(fileEvent(new File(['a'], 'a.csv')), false);
    await c.addFiles(fileEvent(new File(['b'], 'b.xlsx')), false);
    expect(c.pages().map((p: { name: string }) => p.name)).toEqual(['b.xlsx']);
    expect(c.hasDocument()).toBe(true);
    c.removePage(c.pages()[0].key);
    expect(c.pages()).toHaveLength(0);
  });

  it('goes back to the document step with the server message when reading fails', async () => {
    await create(true);
    api.analyse.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 415, error: { detail: 'Format refusé.' } })));
    c.goToDocument();
    await c.addFiles(fileEvent(new File(['a'], 'a.csv')), false);
    c.analyse();
    expect(api.analyse).toHaveBeenCalledWith([expect.objectContaining({ name: 'a.csv' })], null);
    expect(c.step()).toBe(2);
    expect(c.error()).toBe('Format refusé.');
  });

  it('shows the rows to check first, with their statuses and duplicates', async () => {
    await create(true);
    await analysed(preview([
      row(),
      row({ id: 'r2', lineNumber: 2, status: 'A_VERIFIER', issues: ['Téléphone invalide'], confirmable: false }),
      row({ id: 'r3', lineNumber: 3, status: 'DOUBLON', duplicateAction: 'IGNORER',
        issues: ['Déjà membre : Awa Diallo'], duplicateMember: { id: 'm', firstName: 'Awa', lastName: 'Diallo', fundName: 'Caisse Kayes', visible: true } }),
      row({ id: 'r4', lineNumber: 4, status: 'DOUBLON', duplicateAction: 'IGNORER',
        duplicateMember: { id: null, firstName: null, lastName: null, fundName: null, visible: false } }),
      row({ id: 'r5', lineNumber: 5, status: 'DOUBLON', duplicateAction: 'IGNORER', duplicateOfLine: 1 }),
    ], { toCheck: 1, duplicates: 3 }));

    expect(c.step()).toBe(4);
    expect(c.tab()).toBe('verifier');
    expect(c.counts()).toEqual({ toutes: 5, verifier: 1, doublons: 3, exclues: 0 });
    expect(c.visibleRows()).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('.imp-fix')).not.toBeNull(); // pas de « Ligne vérifiée » sur un problème bloquant

    c.setTab('doublons');
    const [visible, hidden, sameFile] = c.visibleRows();
    expect(c.duplicateLabel(visible)).toBe('Déjà membre : Awa Diallo (Caisse Kayes)');
    expect(c.duplicateLabel(hidden)).toContain('autre caisse');
    expect(c.duplicateLabel(sameFile)).toBe('Même personne que la ligne 1');
    expect(c.otherIssues(visible)).toEqual([]);
    expect(c.statusInfo(visible).label).toBe('Doublon possible');
  });

  it('edits, adds and removes rows locally, then sends them to be checked again', async () => {
    await create(true);
    await analysed(preview([row(), row({ id: 'r2', lineNumber: 2 })]));
    const [first, second] = c.rows();
    c.change(first, { phone: '+223 66 12 34 56', included: false });
    c.removeRow(second);
    c.addRow();
    expect(c.hasUnsavedChanges()).toBe(true);
    expect(c.statusInfo(c.rows()[0]).label).toBe('Exclue');

    c.rows()[1].firstName = 'Kadiatou';
    c.rows()[1].lastName = 'Sow';
    api.updateRows.mockReturnValue(of(preview([row({ included: false }), row({ id: 'r9', lineNumber: 3, firstName: 'Kadiatou' })])));
    c.save(false);
    expect(api.updateRows).toHaveBeenCalledWith('imp-1', [
      expect.objectContaining({ id: 'r1', phone: '+223 66 12 34 56', included: false }),
      expect.objectContaining({ id: null, firstName: 'Kadiatou', lastName: 'Sow', phone: null, confirmed: true }),
    ]);
    expect(c.step()).toBe(4);
    expect(c.hasUnsavedChanges()).toBe(false);
  });

  it('stays on the preview while rows remain to check or nothing would be created', async () => {
    await create(true);
    await analysed(preview([row()]));
    api.updateRows.mockReturnValue(of(preview([row({ status: 'A_VERIFIER' })], { toCheck: 1 })));
    c.save(true);
    expect(c.step()).toBe(4);
    expect(c.error()).toContain('1 ligne est encore à vérifier');

    api.updateRows.mockReturnValue(of(preview([row(), row({ id: 'r2' })], { toCheck: 2 })));
    c.save(true);
    expect(c.error()).toContain('2 lignes sont encore à vérifier');

    api.updateRows.mockReturnValue(of(preview([row({ included: false })], { toCreate: 0 })));
    c.save(true);
    expect(c.error()).toContain('Aucun membre à créer');

    api.updateRows.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { detail: 'Import modifié.' } })));
    c.save(true);
    expect(c.error()).toBe('Import modifié.');

    c.rows.set([]);
    c.save(true);
    expect(c.error()).toContain('La liste est vide');
  });

  it('asks to acknowledge a document already imported, then validates with enrolment', async () => {
    await create(true);
    await analysed(preview([row()]));
    api.updateRows.mockReturnValue(of(preview([row()], {}, { alreadyImportedAt: '2026-11-01T09:00:00Z' })));
    c.save(true);
    expect(c.step()).toBe(5);
    expect(c.acknowledged).toBe(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('déjà avoir été importé');

    c.validate();
    expect(api.validate).not.toHaveBeenCalled();

    c.acknowledged = true;
    c.enroll = true;
    api.validate.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { detail: 'Lignes à vérifier.' } })));
    c.validate();
    expect(c.error()).toBe('Lignes à vérifier.');

    api.validate.mockReturnValue(of(VALIDATION));
    c.validate();
    expect(api.validate).toHaveBeenLastCalledWith('imp-1', true);
    expect(c.step()).toBe(6);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('2 membres ajoutés à la Caisse principale');

    const shown: string[] = [];
    c.showMembers.subscribe((id: string) => shown.push(id));
    c.viewMembers();
    expect(shown).toEqual(['imp-1']);

    const closed = vi.fn();
    c.closed.subscribe(closed);
    c.requestClose();
    expect(closed).toHaveBeenCalled();
    expect(api.cancel).not.toHaveBeenCalled();

    c.restart();
    expect(c.step()).toBe(1);
    expect(c.preview()).toBeNull();
  });

  it('moves back through the steps and cancels the draft when abandoning', async () => {
    await create(true);
    await analysed(preview([row()]));
    api.updateRows.mockReturnValue(of(preview([row()])));
    c.save(true);
    c.back();
    expect(c.step()).toBe(4);

    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    c.back();
    expect(c.step()).toBe(4); // abandon refusé
    confirm.mockReturnValue(true);
    c.back();
    expect(c.step()).toBe(2);
    expect(api.cancel).toHaveBeenCalledWith('imp-1');
    c.back();
    expect(c.step()).toBe(1);

    const closed = vi.fn();
    c.closed.subscribe(closed);
    c.requestClose(); // plus de brouillon : fermeture directe
    expect(closed).toHaveBeenCalledTimes(1);
  });
});
