import { describe, expect, it } from 'vitest';
import { ADMIN_ROUTES } from './admin.routes';
import { AdminAssociationDetailComponent } from './associations/admin-association-detail.component';
import { AdminAssociationsComponent } from './associations/admin-associations.component';
import { AdminAuditComponent } from './audit/admin-audit.component';
import { AdminFundDetailComponent } from './funds/admin-fund-detail.component';
import { AdminFundsComponent } from './funds/admin-funds.component';
import { AdminUserDetailComponent } from './users/admin-user-detail.component';
import { AdminUsersComponent } from './users/admin-users.component';

describe('ADMIN_ROUTES', () => {
  it('loads each screen on demand and opens on the accounts', async () => {
    const children = ADMIN_ROUTES[0].children ?? [];
    expect(children[0]).toEqual({ path: '', pathMatch: 'full', redirectTo: 'comptes' });
    const loaded = await Promise.all(children.filter((route) => route.loadComponent).map((route) => route.loadComponent!()));
    expect(loaded).toEqual([AdminUsersComponent, AdminUserDetailComponent, AdminAssociationsComponent,
      AdminAssociationDetailComponent, AdminFundsComponent, AdminFundDetailComponent, AdminAuditComponent]);
  });
});
