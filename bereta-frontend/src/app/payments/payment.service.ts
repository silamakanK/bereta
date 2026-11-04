import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreatePaymentRequest, Payment, PaymentEnrollment, PaymentSummary } from './payment.model';

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/paiements';

  history(): Observable<Payment[]> {
    return this.http.get<Payment[]>(this.endpoint);
  }

  historyForMember(memberId: string): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${this.endpoint}/membres/${memberId}`);
  }

  payableEnrollments(): Observable<PaymentEnrollment[]> {
    return this.http.get<PaymentEnrollment[]>(`${this.endpoint}/inscriptions-a-payer`);
  }

  summary(): Observable<PaymentSummary> {
    return this.http.get<PaymentSummary>(`${this.endpoint}/synthese`);
  }

  record(enrollmentId: string, request: CreatePaymentRequest): Observable<Payment> {
    return this.http.post<Payment>(`${this.endpoint}/inscriptions/${enrollmentId}`, request);
  }
}
