import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateMemberCampaignRequest, CreateMemberRequest, Member, MemberCampaign, MemberTracking } from './member.model';

@Injectable({ providedIn: 'root' })
export class MemberService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/membres';

  list(): Observable<Member[]> {
    return this.http.get<Member[]>(this.endpoint);
  }

  /** Tableau de suivi : membres, caisse, inscriptions, payé / dû, statut. */
  tracking(): Observable<MemberTracking[]> {
    return this.http.get<MemberTracking[]>(`${this.endpoint}/suivi`);
  }

  setActive(id: string, active: boolean): Observable<Member> {
    return this.http.post<Member>(`${this.endpoint}/${id}/${active ? 'reactiver' : 'archiver'}`, {});
  }

  create(request: CreateMemberRequest): Observable<Member> {
    return this.http.post<Member>(this.endpoint, request);
  }

  update(id: string, request: CreateMemberRequest): Observable<Member> {
    return this.http.put<Member>(`${this.endpoint}/${id}`, request);
  }

  listEnrollments(memberId: string): Observable<MemberCampaign[]> {
    return this.http.get<MemberCampaign[]>(`${this.endpoint}/${memberId}/inscriptions`);
  }

  enroll(memberId: string, request: CreateMemberCampaignRequest): Observable<MemberCampaign> {
    return this.http.post<MemberCampaign>(`${this.endpoint}/${memberId}/inscriptions`, request);
  }
}
