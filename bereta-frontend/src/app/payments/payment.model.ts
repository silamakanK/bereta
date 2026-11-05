import { CampaignStatus } from '../funds/fund.model';

export type PaymentMethod = 'ESPECES' | 'VIREMENT' | 'CHEQUE' | 'AUTRE';

export interface PaymentEnrollment {
  id: string;
  memberId: string;
  memberName: string;
  campaignId: string;
  campaignName: string;
  campaignYear: number | null;
  campaignStatus: CampaignStatus;
  dueDate: string | null;
  /** null = caisse principale. */
  localFundId: string | null;
  localFundName: string;
  expectedAmount: number | null;
  paidAmount: number;
  remainingAmount: number | null;
  status: 'A_PAYER' | 'PARTIELLEMENT_PAYE' | 'PAYE' | 'EN_RETARD' | 'MONTANT_A_DEFINIR';
}

export interface CreatePaymentRequest {
  amount: number;
  paidOn: string;
  paymentMethod: PaymentMethod;
  note?: string | null;
}

export interface Payment {
  id: string;
  enrollmentId: string;
  memberId: string;
  memberName: string;
  campaignId: string;
  campaignName: string;
  /** Caisse qui a encaissé (null = caisse principale), figée au moment du paiement. */
  localFundId: string | null;
  localFundName: string;
  amount: number;
  paidOn: string;
  paymentMethod: PaymentMethod;
  note: string | null;
  recordedByName: string;
  createdAt: string;
}

export interface PaymentSummary {
  memberCount: number;
  expectedAmount: number;
  collectedAmount: number;
  remainingAmount: number;
  heldAmount: number;
}
