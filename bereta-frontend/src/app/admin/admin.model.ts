/** Contrats de l'API /api/admin (lot 1 : comptes et journal). */

export interface AdminPage<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export type AdminRole = 'ADMIN' | 'RESPONSABLE' | 'TRESORIER';

/** Ligne de liste : téléphone et e-mail partiellement masqués. */
export interface AdminUserSummary {
  id: string;
  displayName: string | null;
  phone: string | null;
  email: string | null;
  role: AdminRole;
  enabled: boolean;
  mustChangePassword: boolean;
  createdAt: string;
}

export interface AdminUserAssociation { id: string; name: string; status: 'ACTIVE' | 'ARCHIVED'; }

export interface AdminUserFund {
  membershipId: string;
  role: 'RESPONSABLE' | 'TRESORIER';
  associationId: string;
  associationName: string;
  primaryFundName: string;
  localFundId: string | null;
  localFundName: string | null;
}

/** Fiche détaillée : coordonnées complètes. */
export interface AdminUserDetail {
  id: string;
  displayName: string | null;
  phone: string | null;
  email: string | null;
  role: AdminRole;
  enabled: boolean;
  mustChangePassword: boolean;
  createdAt: string;
  lastLoginAt: string | null;
  activeSessions: number;
  associations: AdminUserAssociation[];
  funds: AdminUserFund[];
}

export interface AdminUserActionResult { user: AdminUserSummary; closedSessions: number; }

/** Renvoyé une seule fois : à afficher, puis à oublier. */
export interface AdminPasswordReset { temporaryPassword: string; closedSessions: number; }

export interface AdminUserFilters {
  q: string;
  role: '' | AdminRole;
  enabled: '' | 'true' | 'false';
  sort: 'createdAt' | 'name' | 'role';
  direction: 'asc' | 'desc';
  page: number;
  size: number;
}

export interface AdminAuditEntry {
  id: string;
  actorId: string | null;
  actorName: string;
  action: string;
  targetType: string;
  targetId: string | null;
  associationId: string | null;
  associationName: string | null;
  reason: string | null;
  details: string | null;
  before: Record<string, unknown> | null;
  after: Record<string, unknown> | null;
  ipAddress: string | null;
  createdAt: string;
}

export interface AdminAuditFilters {
  action: string;
  targetType: string;
  targetId: string;
  associationId: string;
  from: string;
  to: string;
  page: number;
  size: number;
}
