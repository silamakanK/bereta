import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { AdminConfirmDialogComponent } from './admin-confirm-dialog.component';

describe('AdminConfirmDialogComponent', () => {
  async function create(busy = false) {
    await TestBed.configureTestingModule({ imports: [AdminConfirmDialogComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AdminConfirmDialogComponent);
    fixture.componentRef.setInput('title', 'Désactiver Awa');
    fixture.componentRef.setInput('impact', ['2 sessions seront fermées.']);
    fixture.componentRef.setInput('danger', true);
    fixture.componentRef.setInput('busy', busy);
    fixture.componentRef.setInput('error', 'Refusé par le serveur.');
    fixture.detectChanges();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return { fixture, c: fixture.componentInstance as any };
  }

  it('requires a 10-character reason before confirming', async () => {
    const { fixture, c } = await create();
    const confirmed = vi.fn();
    c.confirmed.subscribe(confirmed);
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('2 sessions seront fermées.');
    expect(text).toContain('Refusé par le serveur.');
    expect(fixture.nativeElement.querySelector('.acd-btn.primary.danger')).not.toBeNull();

    c.reason.set('court');
    expect(c.remaining()).toBe(5);
    c.submit();
    expect(confirmed).not.toHaveBeenCalled();

    c.reason.set('  Demande du responsable  ');
    c.submit();
    expect(confirmed).toHaveBeenCalledWith('Demande du responsable');

    const cancelled = vi.fn();
    c.cancelled.subscribe(cancelled);
    c.cancel();
    expect(cancelled).toHaveBeenCalled();
  });

  it('cannot be confirmed or closed while the action runs', async () => {
    const { c } = await create(true);
    const confirmed = vi.fn();
    const cancelled = vi.fn();
    c.confirmed.subscribe(confirmed);
    c.cancelled.subscribe(cancelled);
    c.reason.set('Motif largement suffisant');
    c.submit();
    c.cancel();
    expect(confirmed).not.toHaveBeenCalled();
    expect(cancelled).not.toHaveBeenCalled();
  });
});
