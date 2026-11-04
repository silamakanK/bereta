export interface AdminOverview {
  associations: number;
  activeAssociations: number;
  users: number;
  activeUsers: number;
  primaryFunds: number;
  localFunds: number;
  funds: number;
  members: number;
  administrators: number;
}

export interface AdminPage<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface AdminUser {
  id: string;
  email: string;
  displayName: string;
  role: string;
  enabled: boolean;
  createdAt: string;
}

export interface AdminAssociation {
  id: string;
  name: string;
  description: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  address: string | null;
  status: 'ACTIVE' | 'ARCHIVED';
  createdAt: string;
  primaryFundCount: number;
  localFundCount: number;
  memberCount: number;
  responsibleEmails: string[];
}

export interface AdminAuditEntry {
  id: string;
  actorEmail: string;
  action: string;
  targetType: string;
  targetId: string | null;
  associationId: string | null;
  details: string | null;
  createdAt: string;
}

export interface AdminAssociationRequest {
  name: string;
  description?: string;
  contactEmail?: string;
  contactPhone?: string;
  address?: string;
  responsibleEmail: string;
}
