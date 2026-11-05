import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AssociationService } from '../associations/association.service';
import { AuthService } from '../auth/auth.service';
import { AdminApiService } from './admin-api.service';
import { AdminAssociation, AdminAuditEntry, AdminOverview, AdminUser } from './admin.model';

type AdminTab = 'overview' | 'users' | 'associations' | 'audit';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.scss',
})
export class AdminComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly auth = inject(AuthService);
  private readonly associationsService = inject(AssociationService);
  private readonly router = inject(Router);

  protected readonly tab = signal<AdminTab>('overview');
  protected readonly currentUser = this.auth.currentUser;
  protected readonly associations = this.associationsService.associations;
  protected readonly activeAssociationId = this.associationsService.activeAssociationId;
  protected readonly overview = signal<AdminOverview | null>(null);
  protected readonly users = signal<AdminUser[]>([]);
  protected readonly userPage = signal(0);
  protected readonly userTotal = signal(0);
  protected readonly userPages = signal(0);
  protected userQuery = '';
  protected readonly selectedUser = signal<AdminUser | null>(null);
  protected readonly associationsPage = signal(0);
  protected readonly associationsTotal = signal(0);
  protected readonly associationsPages = signal(0);
  protected associationQuery = '';
  protected readonly associationRows = signal<AdminAssociation[]>([]);
  protected readonly auditRows = signal<AdminAuditEntry[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly showCreateUser = signal(false);
  protected readonly showCreateAssociation = signal(false);
  protected readonly passwordVisible = signal(false);
  protected readonly busyUserId = signal<string | null>(null);

  protected newEmail = '';
  protected newDisplayName = '';
  protected newPassword = '';
  protected newRole = 'TRESORIER';
  protected editEmail = '';
  protected editDisplayName = '';
  protected editRole = '';
  protected associationName = '';
  protected associationDescription = '';
  protected associationEmail = '';
  protected associationPhone = '';
  protected associationAddress = '';
  protected responsibleEmail = '';
  protected readonly editingAssociationId = signal<string | null>(null);
  protected readonly extraResponsibleFor = signal<string | null>(null);
  protected extraResponsibleEmail = '';

  ngOnInit(): void {
    this.auth.me().subscribe({ next: () => this.loadAssociations(), error: () => void this.router.navigateByUrl('/connexion') });
    this.loadOverview();
    this.loadUsers();
    this.loadAssociationsPage();
    this.loadAudit();
  }

  protected changeTab(tab: AdminTab): void {
    this.tab.set(tab);
    this.error.set(null);
    this.success.set(null);
  }

  protected selectAssociation(id: string): void {
    this.associationsService.select(id || null);
  }

  protected openBusinessWorkspace(): void {
    if (!this.activeAssociationId()) {
      this.error.set('Choisissez une association avant d’ouvrir son espace métier.');
      return;
    }
    void this.router.navigateByUrl('/');
  }

  protected loadUsers(page = this.userPage()): void {
    this.loading.set(true);
    this.api.users(this.userQuery, page).subscribe({
      next: (result) => {
        this.users.set(result.items);
        this.userPage.set(result.page);
        this.userTotal.set(result.totalItems);
        this.userPages.set(result.totalPages);
        this.loading.set(false);
      },
      error: (e) => this.fail(e),
    });
  }

  protected loadAssociationsPage(page = this.associationsPage()): void {
    this.loading.set(true);
    this.api.associations(this.associationQuery, page).subscribe({
      next: (result) => {
        this.associationRows.set(result.items);
        this.associationsPage.set(result.page);
        this.associationsTotal.set(result.totalItems);
        this.associationsPages.set(result.totalPages);
        this.loading.set(false);
      },
      error: (e) => this.fail(e),
    });
  }

  protected loadOverview(): void {
    this.api.overview().subscribe({ next: (summary) => this.overview.set(summary), error: (e) => this.fail(e) });
  }

  protected loadAudit(): void {
    this.api.audit().subscribe({ next: (result) => this.auditRows.set(result.items), error: (e) => this.fail(e) });
  }

  protected createUser(): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    this.api.createUser({ email: this.newEmail.trim(), displayName: this.newDisplayName.trim(),
      password: this.newPassword, role: this.newRole }).subscribe({
      next: () => {
        this.success.set('Le compte a été créé. Le mot de passe n’est affiché qu’ici dans le formulaire et n’est pas renvoyé par l’API.');
        this.newEmail = ''; this.newDisplayName = ''; this.newPassword = '';
        this.showCreateUser.set(false); this.saving.set(false);
        this.loadUsers(0); this.loadOverview(); this.loadAudit();
      }, error: (e) => this.fail(e),
    });
  }

  protected startEditUser(user: AdminUser): void {
    this.selectedUser.set(user);
    this.editEmail = user.email;
    this.editDisplayName = user.displayName;
    this.editRole = user.role;
  }

  protected updateUser(): void {
    const user = this.selectedUser();
    if (!user || this.saving()) return;
    this.saving.set(true); this.error.set(null);
    this.api.updateUser(user.id, { email: this.editEmail.trim(), displayName: this.editDisplayName.trim(), role: this.editRole }).subscribe({
      next: (updated) => {
        this.success.set('Le compte a été modifié.');
        this.selectedUser.set(null); this.saving.set(false);
        this.loadUsers(); this.loadAudit();
        if (updated.id === this.currentUser()?.id) this.auth.currentUser.set({ ...this.currentUser()!, role: updated.role as 'ADMIN' | 'RESPONSABLE' | 'TRESORIER' });
      }, error: (e) => this.fail(e),
    });
  }

  protected resetUserPassword(user: AdminUser): void {
    if (!confirm(`Réinitialiser le mot de passe de ${user.displayName || user.email} ? Ses sessions seront fermées.`)) return;
    this.busyUserId.set(user.id); this.error.set(null);
    this.api.resetUserPassword(user.id).subscribe({
      next: ({ temporaryPassword }) => {
        this.success.set(`Mot de passe provisoire de ${user.displayName || user.email} : ${temporaryPassword} — à transmettre, il devra le changer à la connexion.`);
        this.busyUserId.set(null); this.loadAudit();
      }, error: (e) => { this.busyUserId.set(null); this.fail(e); },
    });
  }

  protected setUserEnabled(user: AdminUser, enabled: boolean): void {
    this.busyUserId.set(user.id); this.error.set(null);
    this.api.setUserEnabled(user.id, enabled).subscribe({
      next: () => {
        this.success.set(enabled ? 'Compte activé.' : 'Compte désactivé. Ses sessions existantes seront rejetées.');
        this.busyUserId.set(null); this.loadUsers(); this.loadOverview(); this.loadAudit();
      }, error: (e) => { this.busyUserId.set(null); this.fail(e); },
    });
  }

  protected createAssociation(): void {
    if (this.saving()) return;
    this.saving.set(true); this.error.set(null);
    this.api.createAssociation({ name: this.associationName.trim(), description: this.associationDescription.trim() || undefined,
      contactEmail: this.associationEmail.trim() || undefined, contactPhone: this.associationPhone.trim() || undefined,
      address: this.associationAddress.trim() || undefined, responsibleEmail: this.responsibleEmail.trim() }).subscribe({
      next: (association) => {
        this.success.set(`Association « ${association.name} » créée. Son responsable est ${association.responsibleEmails[0] ?? this.responsibleEmail}.`);
        this.clearAssociationForm(); this.showCreateAssociation.set(false); this.saving.set(false);
        this.loadAssociationsPage(0); this.loadAssociations(); this.loadOverview(); this.loadAudit();
      }, error: (e) => this.fail(e),
    });
  }

  protected editAssociation(association: AdminAssociation): void {
    this.editingAssociationId.set(association.id);
    this.associationName = association.name;
    this.associationDescription = association.description ?? '';
    this.associationEmail = association.contactEmail ?? '';
    this.associationPhone = association.contactPhone ?? '';
    this.associationAddress = association.address ?? '';
    this.responsibleEmail = '';
  }

  protected updateAssociation(association: AdminAssociation): void {
    if (this.saving()) return;
    this.saving.set(true); this.error.set(null);
    this.api.updateAssociation(association.id, { name: this.associationName.trim(),
      description: this.associationDescription.trim() || undefined, contactEmail: this.associationEmail.trim() || undefined,
      contactPhone: this.associationPhone.trim() || undefined, address: this.associationAddress.trim() || undefined,
      status: association.status }).subscribe({
      next: () => { this.success.set('Association modifiée.'); this.editingAssociationId.set(null);
        this.saving.set(false); this.loadAssociationsPage(); this.loadAssociations(); this.loadAudit(); }, error: (e) => this.fail(e),
    });
  }

  protected addResponsible(association: AdminAssociation): void {
    this.extraResponsibleFor.set(association.id);
    this.extraResponsibleEmail = '';
  }

  protected saveResponsible(association: AdminAssociation): void {
    this.saving.set(true); this.error.set(null);
    this.api.addResponsible(association.id, this.extraResponsibleEmail.trim()).subscribe({
      next: () => { this.success.set('Responsable ajouté à l’association.'); this.extraResponsibleFor.set(null);
        this.saving.set(false); this.loadAssociationsPage(); this.loadAudit(); }, error: (e) => this.fail(e),
    });
  }

  protected archiveAssociation(association: AdminAssociation): void {
    if (association.status === 'ARCHIVED' || !window.confirm(`Archiver « ${association.name} » ? Les données resteront conservées.`)) return;
    this.api.archiveAssociation(association.id).subscribe({
      next: () => { this.success.set('Association archivée. Son historique est conservé.');
        this.loadAssociationsPage(); this.loadAssociations(); this.loadOverview(); this.loadAudit(); }, error: (e) => this.fail(e),
    });
  }

  protected logout(): void {
    this.auth.logout().subscribe({ next: () => void this.router.navigateByUrl('/connexion'),
      error: () => void this.router.navigateByUrl('/connexion') });
  }

  protected userPageMove(delta: number): void { this.loadUsers(this.userPage() + delta); }
  protected associationPageMove(delta: number): void { this.loadAssociationsPage(this.associationsPage() + delta); }
  protected entryRangeStart(page: number, total: number): number { return total ? page * 10 + 1 : 0; }
  protected entryRangeEnd(page: number, total: number): number { return Math.min((page + 1) * 10, total); }

  private loadAssociations(): void {
    this.associationsService.list().subscribe({ error: (e) => this.fail(e) });
  }
  private clearAssociationForm(): void {
    this.associationName = ''; this.associationDescription = ''; this.associationEmail = '';
    this.associationPhone = ''; this.associationAddress = ''; this.responsibleEmail = '';
  }
  private fail(error: HttpErrorResponse): void {
    const body = typeof error.error === 'string' ? error.error : error.error?.detail || error.error?.message;
    this.error.set(body || (error.status === 403 ? 'Accès administrateur requis.' : `Échec de l’opération (HTTP ${error.status}).`));
    this.saving.set(false); this.loading.set(false);
  }
}
