import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AssociationService } from '../associations/association.service';
import { FundService } from '../funds/fund.service';
import { DataRefreshService } from '../shared/data-refresh';
import { MemberTracking } from './member.model';
import { MemberService } from './member.service';
import { MemberTableComponent } from './member-table.component';

function member(id: string, lastName: string, importId: string | null): MemberTracking {
  return { id, firstName: 'Awa', lastName, phone: null, localFundId: null, fundName: 'Caisse principale',
    active: true, importId, createdAt: '2026-11-16T10:00:00Z', enrollments: [] };
}

describe('MemberTableComponent — filtre « Membres importés »', () => {
  let fixture: ComponentFixture<MemberTableComponent>;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let c: any;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MemberTableComponent],
      providers: [
        { provide: MemberService, useValue: { tracking: () => of([member('a', 'Diallo', 'imp-1'), member('b', 'Sow', null)]) } },
        { provide: FundService, useValue: { listCampaigns: () => of([]) } },
        { provide: AssociationService, useValue: { activeAssociationId: signal('asso') } },
        { provide: DataRefreshService, useValue: { version: signal(0) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(MemberTableComponent);
    fixture.detectChanges();
    c = fixture.componentInstance;
  });

  it('shows only the members of the import, with a removable chip', () => {
    expect(c.memberCount()).toBe(2);
    fixture.componentRef.setInput('importFilter', 'imp-1');
    fixture.detectChanges();
    expect(c.memberCount()).toBe(1);
    expect(c.chips().map((chip: { label: string }) => chip.label)).toContain('Membres importés');

    c.chips().find((chip: { key: string }) => chip.key === 'import').clear();
    expect(c.importFilter()).toBeNull();
    expect(c.memberCount()).toBe(2);
  });

  it('clears the import filter with the other filters, and asks the parent to open the import', () => {
    c.importFilter.set('imp-1');
    c.resetFilters();
    expect(c.importFilter()).toBeNull();

    const opened = vi.fn();
    c.importList.subscribe(opened);
    [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) => b.textContent?.includes('Importer')).click();
    expect(opened).toHaveBeenCalled();
  });
});
