export interface Member {
  id: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  createdAt: string;
}

export interface CreateMemberRequest {
  firstName: string;
  lastName: string;
  phone?: string | null;
}

export interface MemberCampaign {
  id: string;
  campaignId: string;
  campaignName: string;
  campaignType: 'ANNUAL' | 'EVENT';
  campaignYear: number | null;
  primaryFundName: string;
  localFundId: string;
  localFundName: string;
  expectedAmount: number | null;
  createdAt: string;
}

export interface CreateMemberCampaignRequest {
  campaignId: string;
  localFundId: string;
}
