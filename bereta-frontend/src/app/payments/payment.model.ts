export type PaymentMethod = 'ESPECES' | 'VIREMENT' | 'CHEQUE' | 'AUTRE';

export interface PaymentEnrollment {
  id: string;
  memberId: string;
  memberName: string;
  campaignId: string;
  campaignName: string;
  campaignYear: number | null;
  localFundId: string;
  localFundName: string;
  expectedAmount: number;
  paidAmount: number;
  remainingAmount: number;
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
  localFundId: string;
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
