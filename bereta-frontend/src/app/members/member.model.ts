import { PaymentEnrollment } from '../payments/payment.model';

export interface Member {
  id: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  /** Caisse de rattachement : une caisse locale, ou directement la caisse principale. */
  localFundId: string | null;
  fundName: string | null;
  inPrimaryFund: boolean;
  /** false = archivé : n'est plus inscrit automatiquement aux nouvelles campagnes. */
  active: boolean;
  createdAt: string;
}

/** Ligne du tableau de suivi (GET /api/membres/suivi). */
export interface MemberTracking {
  id: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  localFundId: string | null;
  fundName: string | null;
  active: boolean;
  createdAt: string;
  enrollments: PaymentEnrollment[];
}

export interface CreateMemberRequest {
  firstName: string;
  lastName: string;
  phone?: string | null;
  /** Absent : membre rattaché directement à la caisse principale (responsable uniquement). */
  localFundId?: string | null;
}

export interface MemberCampaign {
  id: string;
  campaignId: string;
  campaignName: string;
  campaignType: 'ANNUAL' | 'EVENT';
  campaignYear: number | null;
  primaryFundName: string;
  /** null = caisse principale. */
  localFundId: string | null;
  localFundName: string;
  expectedAmount: number | null;
  createdAt: string;
}

export interface CreateMemberCampaignRequest {
  campaignId: string;
  /** Absent : caisse du membre ; null n'est pas envoyé. */
  localFundId?: string;
}
