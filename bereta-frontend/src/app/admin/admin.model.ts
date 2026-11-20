/** Contrats de l'API /api/admin (comptes, associations, caisses et journal). */

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

// ---- Associations et caisses (lot 2) -------------------------------------------------------

export type AdminStatus = 'ACTIVE' | 'ARCHIVED';

/** Filtres communs aux listes paginées. */
export interface AdminListFilters {
  q: string;
  sort: string;
  direction: 'asc' | 'desc';
  page: number;
  size: number;
}

/** Personne rattachée (fiche : coordonnées complètes). */
export interface AdminPerson {
  userId: string;
  displayName: string | null;
  phone: string | null;
  email: string | null;
  role: AdminRole;
  enabled: boolean;
}

export interface AdminBalance { collected: number; availableLocally: number; inTransit: number; }

export interface AdminAssociationSummary {
  id: string;
  name: string;
  status: AdminStatus;
  createdAt: string;
  responsibleName: string | null;
  localFundCount: number;
  memberCount: number;
}

export interface AdminFundLine {
  id: string;
  name: string;
  location: string | null;
  status: AdminStatus;
  treasurers: AdminPerson[];
  memberCount: number;
  balance: AdminBalance;
}

export interface AdminAssociationDetail {
  id: string;
  name: string;
  description: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  address: string | null;
  status: AdminStatus;
  createdAt: string;
  responsibles: AdminPerson[];
  primaryFundId: string | null;
  primaryFundName: string | null;
  localFunds: AdminFundLine[];
  memberCount: number;
  activeMemberCount: number;
  campaignCount: number;
  openCampaignCount: number;
  balances: { totalCollected: number; central: number; inTransit: number; availableLocally: number };
}

export interface AdminAssociationFilters extends AdminListFilters {
  status: '' | AdminStatus;
  sort: 'createdAt' | 'name';
}

export interface AdminFundSummary {
  id: string;
  name: string;
  location: string | null;
  status: AdminStatus;
  associationId: string;
  associationName: string;
  associationStatus: AdminStatus;
  treasurerNames: string[];
  balance: AdminBalance;
  createdAt: string;
}

export interface AdminFundDetail {
  id: string;
  name: string;
  location: string | null;
  description: string | null;
  status: AdminStatus;
  associationId: string;
  associationName: string;
  associationStatus: AdminStatus;
  primaryFundName: string;
  createdAt: string;
  treasurers: AdminPerson[];
  memberCount: number;
  balance: AdminBalance;
}

export interface AdminFundFilters extends AdminListFilters {
  status: '' | AdminStatus;
  associationId: string;
  sort: 'createdAt' | 'name' | 'association';
}
