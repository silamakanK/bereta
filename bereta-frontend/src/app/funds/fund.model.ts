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
  email: string;
  displayName: string;
  role: 'RESPONSABLE' | 'TRESORIER';
  localFundId: string | null;
  localFundName: string | null;
  createdAt: string;
}

export interface TreasurerInvitation {
  email: string;
  localFundName: string;
  token: string;
  expiresAt: string;
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
