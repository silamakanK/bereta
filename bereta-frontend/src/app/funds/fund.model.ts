export interface PrimaryFund {
  id: string;
  name: string;
  objective: string;
  description: string | null;
  createdAt: string;
}

export interface LocalFund {
  id: string;
  primaryFundId: string;
  primaryFundName: string;
  name: string;
  createdAt: string;
}

export interface FundMembership {
  id: string;
  userId: string;
  email: string | null;
  phone: string | null;
  displayName: string;
  role: 'RESPONSABLE' | 'TRESORIER';
  localFundId: string | null;
  localFundName: string | null;
  createdAt: string;
}

export interface DesignateTreasurerRequest {
  localFundId: string;
  phone: string;
  firstName?: string;
  lastName?: string;
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
  createdAt: string;
}

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
