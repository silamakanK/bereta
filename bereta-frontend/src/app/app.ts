import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, UpperCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MemberService } from './members/member.service';
import { CreateMemberRequest, Member, MemberCampaign } from './members/member.model';
import { FundSetupComponent } from './funds/fund-setup.component';
import { FundService } from './funds/fund.service';
import { Campaign, LocalFund } from './funds/fund.model';
import { forkJoin } from 'rxjs';
import { AuthService } from './auth/auth.service';
import { AuthUser } from './auth/auth.model';
import { LoginComponent } from './auth/login.component';

@Component({
  selector: 'app-root',
  imports: [CurrencyPipe, DatePipe, FormsModule, UpperCasePipe, FundSetupComponent, LoginComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly memberService = inject(MemberService);
  private readonly fundService = inject(FundService);
  private readonly authService = inject(AuthService);
  protected readonly today = new Date();
  protected readonly isCheckingSession = signal(true);
  protected readonly currentUser = signal<AuthUser | null>(null);
  protected readonly selectedFund = signal('Développement de Niogoméra');
  protected readonly searchTerm = signal('');
  protected readonly members = signal<Member[]>([]);
  protected readonly isLoading = signal(true);
  protected readonly isSaving = signal(false);
  protected readonly showMemberForm = signal(false);
  protected readonly showFundSetup = signal(false);
  protected readonly editingMemberId = signal<string | null>(null);
  protected readonly loadErrorMessage = signal<string | null>(null);
  protected readonly saveErrorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly selectedMemberForEnrollment = signal<Member | null>(null);
  protected readonly enrollmentCampaigns = signal<Campaign[]>([]);
  protected readonly enrollmentLocalFunds = signal<LocalFund[]>([]);
  protected readonly memberEnrollments = signal<MemberCampaign[]>([]);
  protected readonly isLoadingEnrollments = signal(false);
  protected readonly isSavingEnrollment = signal(false);
  protected readonly enrollmentErrorMessage = signal<string | null>(null);
  protected readonly enrollmentSuccessMessage = signal<string | null>(null);
  protected firstName = '';
  protected lastName = '';
  protected phone = '';
  protected selectedCampaignId = '';
  protected selectedEnrollmentLocalFundId = '';
  protected readonly filteredMembers = computed(() => {
    const query = this.searchTerm().trim().toLocaleLowerCase('fr');
    if (!query) return this.members();

    return this.members().filter(({ firstName, lastName, phone }) =>
      `${firstName} ${lastName} ${phone}`.toLocaleLowerCase('fr').includes(query),
    );
  });

  constructor() {
    this.authService.me().subscribe({
      next: (user) => {
        this.currentUser.set(user);
        this.isCheckingSession.set(false);
        this.loadMembers();
      },
      error: () => {
        this.currentUser.set(null);
        this.isCheckingSession.set(false);
      },
    });
  }

  protected onAuthenticated(user: AuthUser): void {
    this.currentUser.set(user);
    this.loadMembers();
  }

  protected logout(): void {
    this.authService.logout().subscribe({
      next: () => this.finishLogout(),
      error: () => this.finishLogout(),
    });
  }

  protected userDisplayName(): string {
    return this.currentUser()?.displayName || this.currentUser()?.email || 'Utilisateur';
  }

  protected userInitials(): string {
    const name = this.userDisplayName().split(/[@.\s_-]+/).filter(Boolean);
    return name.slice(0, 2).map((part) => part.charAt(0)).join('').toLocaleUpperCase('fr') || 'B';
  }

  protected roleLabel(): string {
    return this.currentUser()?.role === 'RESPONSABLE' ? 'Responsable de caisse' : 'Trésorier';
  }

  protected loadMembers(): void {
    this.isLoading.set(true);
    this.loadErrorMessage.set(null);
    this.memberService.list().subscribe({
      next: (members) => {
        this.members.set(members);
        this.isLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.loadErrorMessage.set(this.describeApiError(error));
        this.isLoading.set(false);
      },
    });
  }

  protected openCreateForm(): void {
    this.editingMemberId.set(null);
    this.firstName = '';
    this.lastName = '';
    this.phone = '';
    this.saveErrorMessage.set(null);
    this.showMemberForm.set(true);
  }

  protected openEditForm(member: Member): void {
    this.editingMemberId.set(member.id);
    this.firstName = member.firstName;
    this.lastName = member.lastName;
    this.phone = member.phone ?? '';
    this.saveErrorMessage.set(null);
    this.showMemberForm.set(true);
  }

  protected closeMemberForm(): void {
    this.showMemberForm.set(false);
    this.editingMemberId.set(null);
    this.saveErrorMessage.set(null);
  }

  protected saveMember(): void {
    if (this.isSaving()) return;

    const request: CreateMemberRequest = {
      firstName: this.firstName.trim(),
      lastName: this.lastName.trim(),
      phone: this.phone.trim() || null,
    };

    this.isSaving.set(true);
    this.saveErrorMessage.set(null);
    this.successMessage.set(null);
    const memberId = this.editingMemberId();
    const saveOperation = memberId
      ? this.memberService.update(memberId, request)
      : this.memberService.create(request);

    saveOperation.subscribe({
      next: (member) => {
        this.members.update((members) => {
          const exists = members.some((current) => current.id === member.id);
          const updated = exists
            ? members.map((current) => current.id === member.id ? member : current)
            : [...members, member];
          return updated.sort((a, b) =>
            a.lastName.localeCompare(b.lastName, 'fr') || a.firstName.localeCompare(b.firstName, 'fr'),
          );
        });
        this.firstName = '';
        this.lastName = '';
        this.phone = '';
        this.showMemberForm.set(false);
        this.editingMemberId.set(null);
        this.isSaving.set(false);
        this.successMessage.set(`${member.firstName} ${member.lastName} a été ${memberId ? 'modifié(e)' : 'ajouté(e)'}.`);
      },
      error: (error: HttpErrorResponse) => {
        this.saveErrorMessage.set(this.describeApiError(error));
        this.isSaving.set(false);
      },
    });
  }

  protected initials(member: Member): string {
    return `${member.firstName.charAt(0)}${member.lastName.charAt(0)}`.toLocaleUpperCase('fr');
  }

  private describeApiError(error: HttpErrorResponse): string {
    if (error.status === 0) {
      return 'Le backend est injoignable. Vérifiez qu’il tourne sur le port 8081 et redémarrez npm start pour recharger le proxy.';
    }
    if (error.status === 400) {
      return 'Vérifiez le prénom et le nom saisis, puis réessayez.';
    }
    if (error.status === 404) {
      return 'Cette route API est introuvable. Redémarrez le backend mis à jour sur le port 8081.';
    }
    if (error.status >= 500) {
      return 'Le backend a rencontré une erreur. Vérifiez ses logs et l’application de la migration de la table membres.';
    }
    return `La requête a échoué (HTTP ${error.status}). Réessayez.`;
  }

  protected updateSearch(event: Event): void {
    this.searchTerm.set((event.target as HTMLInputElement).value);
  }

  protected openEnrollment(member: Member): void {
    this.selectedMemberForEnrollment.set(member);
    this.enrollmentErrorMessage.set(null);
    this.enrollmentSuccessMessage.set(null);
    this.isLoadingEnrollments.set(true);
    forkJoin({
      campaigns: this.fundService.listCampaigns(),
      enrollments: this.memberService.listEnrollments(member.id),
    }).subscribe({
      next: ({ campaigns, enrollments }) => {
        this.enrollmentCampaigns.set(campaigns);
        this.memberEnrollments.set(enrollments);
        this.isLoadingEnrollments.set(false);
        this.onEnrollmentCampaignChange(campaigns[0]?.id ?? '');
      },
      error: (error: HttpErrorResponse) => {
        this.enrollmentErrorMessage.set(this.describeApiError(error));
        this.isLoadingEnrollments.set(false);
      },
    });
  }

  protected closeEnrollment(): void {
    this.selectedMemberForEnrollment.set(null);
    this.enrollmentErrorMessage.set(null);
    this.enrollmentSuccessMessage.set(null);
  }

  protected onEnrollmentCampaignChange(campaignId: string): void {
    this.selectedCampaignId = campaignId;
    this.selectedEnrollmentLocalFundId = '';
    const campaign = this.enrollmentCampaigns().find((item) => item.id === campaignId);
    if (!campaign) {
      this.enrollmentLocalFunds.set([]);
      return;
    }
    this.fundService.listLocalFunds(campaign.primaryFundId).subscribe({
      next: (funds) => {
        this.enrollmentLocalFunds.set(funds);
        this.selectedEnrollmentLocalFundId = funds[0]?.id ?? '';
      },
      error: (error: HttpErrorResponse) => this.enrollmentErrorMessage.set(this.describeApiError(error)),
    });
  }

  protected enrollMember(): void {
    const member = this.selectedMemberForEnrollment();
    if (!member || !this.selectedCampaignId || !this.selectedEnrollmentLocalFundId || this.isSavingEnrollment()) return;
    this.isSavingEnrollment.set(true);
    this.enrollmentErrorMessage.set(null);
    this.enrollmentSuccessMessage.set(null);
    this.memberService.enroll(member.id, {
      campaignId: this.selectedCampaignId,
      localFundId: this.selectedEnrollmentLocalFundId,
    }).subscribe({
      next: (enrollment) => {
        this.memberEnrollments.update((enrollments) => [enrollment, ...enrollments]);
        this.isSavingEnrollment.set(false);
        this.enrollmentSuccessMessage.set(`${member.firstName} a été inscrit(e) à la campagne.`);
      },
      error: (error: HttpErrorResponse) => {
        this.enrollmentErrorMessage.set(error.status === 409
          ? 'Ce membre est déjà inscrit à cette campagne.'
          : this.describeApiError(error));
        this.isSavingEnrollment.set(false);
      },
    });
  }

  protected campaignLabel(campaign: Campaign): string {
    const year = campaign.campaignYear ? ` · ${campaign.campaignYear}` : '';
    const amount = campaign.expectedAmount === null ? 'montant à définir' : `${campaign.expectedAmount} €`;
    return `${campaign.name}${year} — ${campaign.primaryFundName} (${amount})`;
  }

  private finishLogout(): void {
    this.currentUser.set(null);
    this.members.set([]);
    this.closeMemberForm();
    this.closeEnrollment();
  }
}
