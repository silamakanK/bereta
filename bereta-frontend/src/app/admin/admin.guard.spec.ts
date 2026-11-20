import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom, of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { AuthService } from '../auth/auth.service';
import { adminGuard } from './admin.guard';

function run(current: { role: string } | null, restored: { role: string } | null) {
  const auth = { currentUser: signal(current), restoreSession: vi.fn(() => of(restored)) };
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: auth }] });
  const result = TestBed.runInInjectionContext(() => adminGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
  return { result, auth, router: TestBed.inject(Router) };
}

describe('adminGuard', () => {
  it('lets an administrator in and sends other roles home', () => {
    expect(run({ role: 'ADMIN' }, null).result).toBe(true);
    TestBed.resetTestingModule();
    const { result, router } = run({ role: 'RESPONSABLE' }, null);
    expect(router.serializeUrl(result as UrlTree)).toBe('/');
  });

  it('restores the session through the shared refresh after a reload', async () => {
    const admin = run(null, { role: 'ADMIN' });
    expect(await firstValueFrom(admin.result as Observable<unknown>)).toBe(true);
    expect(admin.auth.restoreSession).toHaveBeenCalledOnce();
    TestBed.resetTestingModule();
    const manager = run(null, { role: 'TRESORIER' });
    expect(manager.router.serializeUrl(await firstValueFrom(manager.result as Observable<UrlTree>))).toBe('/');
    TestBed.resetTestingModule();
    const anonymous = run(null, null);
    expect(anonymous.router.serializeUrl(await firstValueFrom(anonymous.result as Observable<UrlTree>))).toBe('/connexion');
  });
});
