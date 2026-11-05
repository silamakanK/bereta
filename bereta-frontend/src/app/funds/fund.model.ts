export interface PrimaryFund {
  id: string;
  name: string;
  objective: string;
  description: string | null;
  createdAt: string;
}

export interface TreasurerSummary {
  membershipId: string;
  userId: string;
  displayName: string | null;
  phone: string | null;
  email: string | null;
}

export interface LocalFund {
  id: string;
  primaryFundId: string;
  primaryFundName: string;
  name: string;
  location: string | null;
  description: string | null;
  /** Vide : caisse « Sans responsable ». */
  treasurers: TreasurerSummary[];
  createdAt: string;
}

export interface CreateLocalFundRequest {
  name: string;
  location?: string | null;
  description?: string | null;
}

/** Recherche exacte par téléphone ou e-mail (jamais de liste de comptes). */
export interface AccountLookup {
  exists: boolean;
  displayName: string | null;
  maskedPhone: string | null;
  alreadyTreasurer: boolean;
}

export interface FundMembership {
  id: string;
  userId: string;
  email: string | null;
  phone: string | null;
  displayName: string | null;
  role: 'RESPONSABLE' | 'TRESORIER';
  localFundId: string | null;
  localFundName: string | null;
  createdAt: string;
}

export interface DesignateTreasurerRequest {
  localFundId: string;
  phone?: string | null;
  email?: string | null;
  firstName?: string;
  lastName?: string;
  /** Trésorier actuel à remplacer (« Changer »). */
  replaceMembershipId?: string | null;
}

/** temporaryPassword n'est fourni qu'une fois, à la création du compte. */
export interface TreasurerDesignation {
  membership: FundMembership;
  accountCreated: boolean;
  temporaryPassword: string | null;
}

export interface TemporaryPassword {
  temporaryPassword: string;
  maskedPhone: string | null;
}

export type CampaignType = 'ANNUAL' | 'EVENT';

export interface Campaign {
  id: string;
  primaryFundId: string;
  primaryFundName: string;
  name: string;
  campaignType: CampaignType;
  campaignYear: number | null;
  expectedAmount: number | null;
  dueDate: string | null;
  status: CampaignStatus;
  closedAt: string | null;
  /** Membres inscrits (à la création : inscrits automatiquement). */
  enrolledCount: number;
  createdAt: string;
}

export type CampaignStatus = 'OUVERTE' | 'CLOTUREE';

export interface CreatePrimaryFundRequest {
  name: string;
  objective: string;
  description?: string | null;
}

export interface CreateCampaignRequest {
  primaryFundId: string;
  name: string;
  campaignType: CampaignType;
  campaignYear?: number | null;
  expectedAmount?: string | null;
  dueDate?: string | null;
}
