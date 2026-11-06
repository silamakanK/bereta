import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { CurrencyPipe, DatePipe, LowerCasePipe, UpperCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MemberService } from './members/member.service';
import { CreateMemberRequest, Member, MemberCampaign, MemberTracking } from './members/member.model';
import { FundSetupComponent } from './funds/fund-setup.component';
import { FundService } from './funds/fund.service';
import { Campaign, LocalFund } from './funds/fund.model';
import { forkJoin } from 'rxjs';
import { AuthService } from './auth/auth.service';
import { FirstLoginComponent } from './auth/first-login.component';
import { PaymentService } from './payments/payment.service';
import { AssociationService } from './associations/association.service';
import { AssociationBoardComponent } from './associations/association-board.component';
import { MemberTableComponent } from './members/member-table.component';
import { DataRefreshService } from './shared/data-refresh';
import { ModalDialogDirective } from './shared/modal-dialog.directive';
import { CreatePaymentRequest, Payment, PaymentEnrollment, PaymentMethod, PaymentSummary } from './payments/payment.model';

@Component({
  selector: 'app-root',
  imports: [CurrencyPipe, DatePipe, FormsModule, LowerCasePipe, UpperCasePipe, FundSetupComponent, RouterLink, RouterOutlet, FirstLoginComponent, AssociationBoardComponent, MemberTableComponent, ModalDialogDirective],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly memberService = inject(MemberService);
  private readonly fundService = inject(FundService);
  private readonly authService = inject(AuthService);
  private readonly paymentService = inject(PaymentService);
  private readonly associationService = inject(AssociationService);
  private readonly refresh = inject(DataRefreshService);
  protected readonly today = new Date();
  protected readonly isCheckingSession = signal(true);
  protected readonly currentUser = this.authService.currentUser;
  private readonly router = inject(Router);
  private readonly currentUrl = toSignal(this.router.events.pipe(
    filter((event): event is NavigationEnd => event instanceof NavigationEnd),
    map((event) => event.urlAfterRedirects)), { initialValue: this.router.url });
  protected readonly isAdminRoute = computed(() => this.currentUrl().startsWith('/admin'));
  /** « Complète ton profil » s'affiche hors du tableau de bord. */
  protected readonly isAccountRoute = computed(() =>
    this.currentUrl().startsWith('/completer-profil'));

  protected readonly associations = this.associationService.associations;
  protected readonly activeAssociation = this.associationService.activeAssociation;
  protected readonly activeAssociationId = this.associationService.activeAssociationId;
  protected readonly isCheckingAssociations = signal(true);
  protected readonly associationLoadError = signal(false);
  protected readonly associationCreateError = signal<string | null>(null);
  protected readonly isCreatingAssociation = signal(false);
  protected readonly showAssociationForm = signal(false);
  protected associationName = '';
  protected associationDescription = '';
  protected associationEmail = '';
  protected associationPhone = '';
  protected associationAddress = '';
  protected readonly hasPrimaryFunds = signal(false);
  protected readonly isCheckingFunds = signal(false);
  protected readonly fundLoadError = signal(false);
  protected readonly searchTerm = signal('');
  protected readonly members = signal<Member[]>([]);
  protected readonly accessibleLocalFunds = signal<LocalFund[]>([]);
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
  protected readonly paymentSummary = signal<PaymentSummary | null>(null);
  protected readonly recentPayments = signal<Payment[]>([]);
  /** Trésorier de plusieurs caisses : caisse affichée (sélecteur en haut du tableau de bord). */
  protected readonly activeLocalFundId = signal<string | null>(null);
  protected readonly visiblePayments = computed(() => {
    const fundId = this.isResponsible() ? null : this.activeLocalFundId();
    return fundId ? this.recentPayments().filter((payment) => payment.localFundId === fundId) : this.recentPayments();
  });
  protected readonly isResponsible = computed(() => !!this.activeAssociation()?.currentUserResponsible);
  /** Un compte trésorier (créé par un responsable) ne crée pas d'association. */
  protected readonly canCreateAssociation = computed(() => this.currentUser()?.role !== 'TRESORIER');
  protected readonly memberPayments = signal<Payment[]>([]);
  protected readonly paymentTargets = signal<PaymentEnrollment[]>([]);
  protected readonly showPaymentForm = signal(false);
  protected readonly isLoadingPaymentTargets = signal(false);
  protected readonly isSavingPayment = signal(false);
  protected readonly isReviewingPayment = signal(false);
  protected readonly paymentErrorMessage = signal<string | null>(null);
  protected readonly paymentSuccessMessage = signal<string | null>(null);
  protected selectedPaymentEnrollmentId = '';
  protected paymentAmount = 0;
  protected paymentDate = this.localDateToday();
  protected paymentMethod: PaymentMethod = 'ESPECES';
  protected paymentNote = '';
  protected firstName = '';
  protected lastName = '';
  protected phone = '';
  protected selectedCampaignId = '';
  protected selectedCreateLocalFundId = '';
  protected selectedEnrollmentLocalFundId = '';
  /** Valeur du sélecteur « Caisse » pour un membre suivi directement par la caisse principale. */
  protected readonly primaryFundChoice = 'PRINCIPALE';
  protected readonly filteredMembers = computed(() => {
    const query = this.searchTerm().trim().toLocaleLowerCase('fr');
    if (!query) return this.members();

    return this.members().filter(({ firstName, lastName, phone }) =>
      `${firstName} ${lastName} ${phone}`.toLocaleLowerCase('fr').includes(query),
    );
  });

  constructor() {
    effect(() => {
      const user = this.currentUser();
      if (this.isCheckingSession() || !user || user.mustChangePassword) return;
      this.loadAssociations();
    });
    effect(() => {
      if (!this.currentUser()) return;
      if (this.isCheckingAssociations()) return;
      if (!this.activeAssociationId()) return;
      this.isCheckingFunds.set(true);
      this.fundLoadError.set(false);
      this.loadAccessibleLocalFunds();
      this.loadMembers();
      this.loadPaymentData();
    });
    // Après chaque écriture réussie (membre, caisse, paiement…), on recharge sans effet de « Chargement… ».
    let seenVersion = this.refresh.version();
    effect(() => {
      const version = this.refresh.version();
      if (version === seenVersion) return;
      seenVersion = version;
      untracked(() => {
        if (!this.currentUser() || !this.activeAssociationId() || this.isCheckingAssociations()) return;
        this.loadAccessibleLocalFunds();
        this.loadMembers(true);
        this.loadPaymentData();
      });
    });
    // Au chargement, l'access token (gardé en mémoire) est perdu : on le redemande grâce au cookie.
    this.authService.restoreSession().subscribe({
      next: () => this.isCheckingSession.set(false),
      error: () => this.isCheckingSession.set(false),
    });
  }

  protected loadAssociations(): void {
    this.isCheckingAssociations.set(true);
    this.associationLoadError.set(false);
    this.associationService.list().subscribe({
      next: () => this.isCheckingAssociations.set(false),
      error: () => {
        this.associationLoadError.set(true);
        this.isCheckingAssociations.set(false);
      },
    });
  }

  protected selectAssociation(id: string): void {
    this.associationService.select(id || null);
    this.members.set([]);
    this.recentPayments.set([]);
    this.paymentSummary.set(null);
    this.hasPrimaryFunds.set(false);
    this.showFundSetup.set(false);
    this.closeMemberForm();
    this.closeEnrollment();
    this.closePaymentForm();
  }

  protected createAssociation(): void {
    if (this.isCreatingAssociation() || !this.associationName.trim()) return;
    this.isCreatingAssociation.set(true);
    this.associationCreateError.set(null);
    this.associationService.create({
      name: this.associationName.trim(),
      description: this.associationDescription.trim() || undefined,
      contactEmail: this.associationEmail.trim() || undefined,
      contactPhone: this.associationPhone.trim() || undefined,
      address: this.associationAddress.trim() || undefined,
    }).subscribe({
      next: () => {
        this.associationName = '';
        this.associationDescription = '';
        this.associationEmail = '';
        this.associationPhone = '';
        this.associationAddress = '';
        this.showAssociationForm.set(false);
        this.isCreatingAssociation.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.associationCreateError.set(this.describeApiError(error));
        this.isCreatingAssociation.set(false);
      },
    });
  }

  protected onFundSetupClosed(): void {
    this.showFundSetup.set(false);
    this.loadAccessibleLocalFunds();
    this.loadMembers();
    this.loadPaymentData();
  }

  protected retryLoadFunds(): void {
    this.fundLoadError.set(false);
    this.isCheckingFunds.set(true);
    this.loadAccessibleLocalFunds();
    this.loadMembers();
    this.loadPaymentData();
  }


  protected logout(): void {
    this.authService.logout().subscribe({
      next: () => this.finishLogout(),
      error: () => this.finishLogout(),
    });
  }

  protected userDisplayName(): string {
    return this.currentUser()?.displayName || this.currentUser()?.email || this.currentUser()?.phone || 'Utilisateur';
  }

  protected userInitials(): string {
    const name = this.userDisplayName().split(/[@.\s_-]+/).filter(Boolean);
    return name.slice(0, 2).map((part) => part.charAt(0)).join('').toLocaleUpperCase('fr') || 'B';
  }

  protected roleLabel(): string {
    const active = this.activeAssociation();
    if (active) return active.currentUserResponsible ? 'Responsable de l’association' : 'Trésorier local';
    if (!this.hasPrimaryFunds()) return 'Compte BERETA';
    return this.currentUser()?.role === 'RESPONSABLE' ? 'Responsable de caisse' : 'Trésorier';
  }

  protected loadMembers(silent = false): void {
    const associationId = this.activeAssociationId();
    if (!silent) this.isLoading.set(true);
    this.loadErrorMessage.set(null);
    this.memberService.list().subscribe({
      next: (members) => {
        if (associationId !== this.activeAssociationId()) return;
        this.members.set(members);
        this.isLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        if (associationId !== this.activeAssociationId()) return;
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
    if (!this.isValidMemberFund(this.selectedCreateLocalFundId)) {
      this.selectedCreateLocalFundId = this.defaultMemberFund();
    }
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
      localFundId: this.editingMemberId() || this.selectedCreateLocalFundId === this.primaryFundChoice
        ? undefined : this.selectedCreateLocalFundId,
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
    if (error.status === 403) {
      return 'Vous n’avez pas accès à cette caisse ou à cette caisse locale.';
    }
    if (error.status === 404) {
      return 'Cette route API est introuvable. Redémarrez le backend mis à jour sur le port 8081.';
    }
    if (error.status >= 500) {
      return 'Le backend a rencontré une erreur. Vérifiez ses logs et l’application de la migration de la table membres.';
    }
    return `La requête a échoué (HTTP ${error.status}). Réessayez.`;
  }

  private loadAccessibleLocalFunds(): void {
    const associationId = this.activeAssociationId();
    this.fundService.listPrimaryFunds().subscribe({
      next: (funds) => {
        if (associationId !== this.activeAssociationId()) return;
        this.hasPrimaryFunds.set(funds.length > 0);
        this.isCheckingFunds.set(false);
        if (funds.length === 0) {
          this.accessibleLocalFunds.set([]);
          this.selectedCreateLocalFundId = '';
          return;
        }
        forkJoin(funds.map((fund) => this.fundService.listLocalFunds(fund.id))).subscribe({
          next: (groups) => {
            if (associationId !== this.activeAssociationId()) return;
            const localFunds = groups.flat().sort((a, b) =>
              a.primaryFundName.localeCompare(b.primaryFundName, 'fr') || a.name.localeCompare(b.name, 'fr'),
            );
            this.accessibleLocalFunds.set(localFunds);
            if (!localFunds.some((fund) => fund.id === this.activeLocalFundId())) this.activeLocalFundId.set(localFunds[0]?.id ?? null);
            if (!this.isValidMemberFund(this.selectedCreateLocalFundId)) {
              this.selectedCreateLocalFundId = this.defaultMemberFund();
            }
          },
          error: () => {
            if (associationId !== this.activeAssociationId()) return;
            this.accessibleLocalFunds.set([]);
            this.selectedCreateLocalFundId = this.defaultMemberFund();
          },
        });
      },
      error: () => {
        this.accessibleLocalFunds.set([]);
        this.selectedCreateLocalFundId = '';
        this.hasPrimaryFunds.set(false);
        this.fundLoadError.set(true);
        this.isCheckingFunds.set(false);
      },
    });
  }

  /** Le responsable rattache un membre à la caisse principale ou à une caisse locale ; le trésorier, à sa caisse. */
  private isValidMemberFund(choice: string): boolean {
    if (choice === this.primaryFundChoice) return this.isResponsible() && this.hasPrimaryFunds();
    return this.accessibleLocalFunds().some((fund) => fund.id === choice);
  }

  private defaultMemberFund(): string {
    if (this.isResponsible() && this.hasPrimaryFunds()) return this.primaryFundChoice;
    return this.accessibleLocalFunds()[0]?.id ?? '';
  }

  protected updateSearch(event: Event): void {
    this.searchTerm.set((event.target as HTMLInputElement).value);
  }

  /** Ligne du tableau → fiche membre (formulaire de modification, inscriptions). */
  protected asMember(row: MemberTracking): Member {
    return { id: row.id, firstName: row.firstName, lastName: row.lastName, phone: row.phone, localFundId: row.localFundId,
      fundName: row.fundName, inPrimaryFund: row.localFundId === null, active: row.active, createdAt: row.createdAt };
  }

  /** Campagne ouverte la plus récente : sert à signaler « Non inscrit à la campagne en cours » sur la fiche. */
  protected currentCampaign(): Campaign | null {
    return this.enrollmentCampaigns()[0] ?? null;
  }

  protected isEnrolledInCurrentCampaign(): boolean {
    const current = this.currentCampaign();
    return !current || this.memberEnrollments().some((enrollment) => enrollment.campaignId === current.id);
  }

  protected openEnrollment(member: Member): void {
    this.selectedMemberForEnrollment.set(member);
    this.enrollmentErrorMessage.set(null);
    this.enrollmentSuccessMessage.set(null);
    this.isLoadingEnrollments.set(true);
    forkJoin({
      campaigns: this.fundService.listCampaigns(),
      enrollments: this.memberService.listEnrollments(member.id),
      payments: this.paymentService.historyForMember(member.id),
    }).subscribe({
      next: ({ campaigns, enrollments, payments }) => {
        // Seules les campagnes ouvertes acceptent une inscription.
        this.enrollmentCampaigns.set(campaigns.filter((campaign) => campaign.status === 'OUVERTE'));
        this.memberEnrollments.set(enrollments);
        this.memberPayments.set(payments);
        this.isLoadingEnrollments.set(false);
        this.onEnrollmentCampaignChange(this.enrollmentCampaigns()[0]?.id ?? '');
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

  /**
   * Caisses proposées : celles de l'utilisateur, plus la caisse principale pour le responsable.
   * Par défaut, la caisse du membre.
   */
  protected onEnrollmentCampaignChange(campaignId: string): void {
    this.selectedCampaignId = campaignId;
    this.enrollmentLocalFunds.set(this.accessibleLocalFunds());
    const member = this.selectedMemberForEnrollment();
    const own = member?.localFundId ?? (this.isResponsible() ? this.primaryFundChoice : '');
    this.selectedEnrollmentLocalFundId = own && (own === this.primaryFundChoice
      || this.accessibleLocalFunds().some((fund) => fund.id === own)) ? own : this.accessibleLocalFunds()[0]?.id ?? '';
  }

  protected enrollMember(): void {
    const member = this.selectedMemberForEnrollment();
    if (!member || !this.selectedCampaignId || !this.selectedEnrollmentLocalFundId || this.isSavingEnrollment()) return;
    this.isSavingEnrollment.set(true);
    this.enrollmentErrorMessage.set(null);
    this.enrollmentSuccessMessage.set(null);
    this.memberService.enroll(member.id, {
      campaignId: this.selectedCampaignId,
      localFundId: this.selectedEnrollmentLocalFundId === this.primaryFundChoice ? undefined : this.selectedEnrollmentLocalFundId,
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
    return `${campaign.name}${year} (${amount})`;
  }

  /** {@code enrollmentId} : inscription présélectionnée (bouton « Encaisser » du tableau). */
  protected openPaymentForm(enrollmentId?: string): void {
    this.showPaymentForm.set(true);
    this.isReviewingPayment.set(false);
    this.paymentErrorMessage.set(null);
    this.paymentSuccessMessage.set(null);
    this.paymentSuccessMessage.set(null);
    this.isLoadingPaymentTargets.set(true);
    this.paymentService.payableEnrollments().subscribe({
      next: (targets) => {
        this.paymentTargets.set(targets);
        this.isLoadingPaymentTargets.set(false);
        const target = targets.find((item) => item.id === enrollmentId) ?? targets[0];
        this.selectedPaymentEnrollmentId = target?.id ?? '';
        this.paymentAmount = target?.remainingAmount ?? 0;
      },
      error: (error: HttpErrorResponse) => {
        this.paymentErrorMessage.set(this.describeApiError(error));
        this.isLoadingPaymentTargets.set(false);
      },
    });
  }

  protected closePaymentForm(): void {
    this.showPaymentForm.set(false);
    this.isReviewingPayment.set(false);
    this.paymentErrorMessage.set(null);
  }

  protected selectedPaymentTarget(): PaymentEnrollment | undefined {
    return this.paymentTargets().find((target) => target.id === this.selectedPaymentEnrollmentId);
  }

  protected onPaymentTargetChange(enrollmentId: string): void {
    this.selectedPaymentEnrollmentId = enrollmentId;
    this.paymentAmount = this.selectedPaymentTarget()?.remainingAmount ?? 0;
    this.isReviewingPayment.set(false);
  }

  protected reviewPayment(): void {
    const target = this.selectedPaymentTarget();
    if (!target || this.paymentAmount <= 0 || this.paymentAmount > (target.remainingAmount ?? 0)) return;
    this.paymentErrorMessage.set(null);
    this.isReviewingPayment.set(true);
  }

  protected savePayment(): void {
    const target = this.selectedPaymentTarget();
    if (!target || this.isSavingPayment() || this.paymentAmount <= 0 || this.paymentAmount > (target.remainingAmount ?? 0)) return;
    const request: CreatePaymentRequest = {
      amount: this.paymentAmount,
      paidOn: this.paymentDate,
      paymentMethod: this.paymentMethod,
      note: this.paymentNote.trim() || null,
    };
    this.isSavingPayment.set(true);
    this.paymentErrorMessage.set(null);
    this.paymentService.record(target.id, request).subscribe({
      next: (payment) => {
        this.recentPayments.update((payments) => [payment, ...payments].slice(0, 10));
        if (this.selectedMemberForEnrollment()?.id === payment.memberId) {
          this.memberPayments.update((payments) => [payment, ...payments]);
        }
        this.paymentSuccessMessage.set(`Paiement de ${payment.amount.toLocaleString('fr-FR', { style: 'currency', currency: 'EUR' })} enregistré pour ${payment.memberName}.`);
        this.paymentTargets.update((targets) => targets
          .map((item) => item.id === target.id
            ? { ...item, paidAmount: item.paidAmount + payment.amount, remainingAmount: (item.remainingAmount ?? 0) - payment.amount,
              status: (item.remainingAmount ?? 0) - payment.amount <= 0 ? 'PAYE' as const : 'PARTIELLEMENT_PAYE' as const }
            : item)
          .filter((item) => (item.remainingAmount ?? 0) > 0));
        this.paymentSummary.update((summary) => summary ? {
          ...summary,
          collectedAmount: summary.collectedAmount + payment.amount,
          remainingAmount: Math.max(0, summary.remainingAmount - payment.amount),
          heldAmount: summary.heldAmount + payment.amount,
        } : summary);
        this.isSavingPayment.set(false);
        this.closePaymentForm();
        this.loadPaymentData();
      },
      error: (error: HttpErrorResponse) => {
        this.paymentErrorMessage.set(error.status === 400
          ? 'Le montant dépasse le reste à payer. Vérifiez le montant et réessayez.'
          : this.describeApiError(error));
        this.isSavingPayment.set(false);
        this.isReviewingPayment.set(false);
      },
    });
  }

  protected paymentMethodLabel(method: PaymentMethod): string {
    return ({ ESPECES: 'Espèces', VIREMENT: 'Virement', CHEQUE: 'Chèque', AUTRE: 'Autre' })[method];
  }

  private loadPaymentData(): void {
    const associationId = this.activeAssociationId();
    this.paymentService.summary().subscribe({ next: (summary) => {
      if (associationId === this.activeAssociationId()) this.paymentSummary.set(summary);
    } });
    this.paymentService.history().subscribe({ next: (payments) => {
      if (associationId === this.activeAssociationId()) this.recentPayments.set(payments.slice(0, 10));
    } });
  }

  private localDateToday(): string {
    const date = new Date();
    const offset = date.getTimezoneOffset() * 60_000;
    return new Date(date.getTime() - offset).toISOString().slice(0, 10);
  }

  private finishLogout(): void {
    this.authService.clearSession();
    this.associationService.clear();
    this.hasPrimaryFunds.set(false);
    this.isCheckingFunds.set(false);
    this.members.set([]);
    this.closeMemberForm();
    this.closeEnrollment();
    this.memberPayments.set([]);
  }
}
