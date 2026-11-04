import { PaymentMethod } from '../payments/payment.model';

export type RemittanceStatus = 'EN_ATTENTE' | 'CONFIRMEE' | 'ANNULEE';

export interface Remittance {
  id: string;
  primaryFundId: string;
  primaryFundName: string;
  localFundId: string;
  localFundName: string;
  amount: number;
  transferredOn: string;
  method: PaymentMethod;
  note: string | null;
  status: RemittanceStatus;
  declaredByName: string;
  declaredAt: string;
  confirmedByName: string | null;
  confirmedAt: string | null;
  cancelledByName: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
}

export interface LocalFundCashBalance {
  localFundId: string;
  localFundName: string;
  totalCollected: number;
  availableLocally: number;
  inTransit: number;
}

export interface CashRemittanceSummary {
  primaryFundId: string;
  primaryFundName: string;
  localFunds: LocalFundCashBalance[];
  totalCollected: number;
  availableLocally: number;
  inTransit: number;
  centralBalance: number | null;
  totalHeldGlobally: number | null;
}

export interface CreateRemittanceRequest {
  amount: number;
  transferredOn: string;
  method: PaymentMethod;
  note?: string | null;
}
