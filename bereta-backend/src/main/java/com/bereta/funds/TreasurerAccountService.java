package com.bereta.funds;

import com.bereta.associations.AssociationMembershipRepository;
import com.bereta.associations.AssociationRequestContext;
import com.bereta.auth.AuthAuditService;
import com.bereta.auth.AuthEvent;
import com.bereta.auth.PasswordService;
import com.bereta.auth.PhoneNumberService;
import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.auth.token.RefreshTokenService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Comptes trésoriers selon la maquette « Créer une caisse — étape 2 » : le responsable
 * désigne le trésorier par son téléphone. Un compte existant est rattaché ; sinon un
 * compte est créé avec un mot de passe provisoire (affiché une seule fois au responsable,
 * à changer à la première connexion).
 */
@Service
public class TreasurerAccountService {


    private final FundAccessService fundAccess;
    private final FundMembershipRepository memberships;
    private final AssociationMembershipRepository associationMemberships;
    private final PrimaryFundRepository primaryFunds;
    private final LocalFundRepository localFunds;
    private final UserAccountRepository users;
    private final PhoneNumberService phoneNumbers;
    private final PasswordService passwordService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final AuthAuditService audit;
    private final AssociationRequestContext context;
    private final com.bereta.admin.AdminAuditService adminAudit;

    public TreasurerAccountService(FundAccessService fundAccess, FundMembershipRepository memberships,
                                   AssociationMembershipRepository associationMemberships,
                                   PrimaryFundRepository primaryFunds, LocalFundRepository localFunds,
                                   UserAccountRepository users, PhoneNumberService phoneNumbers,
                                   PasswordService passwordService, PasswordEncoder passwordEncoder,
                                   RefreshTokenService refreshTokens, AuthAuditService audit,
                                   AssociationRequestContext context, com.bereta.admin.AdminAuditService adminAudit) {
        this.fundAccess = fundAccess;
        this.memberships = memberships;
        this.associationMemberships = associationMemberships;
        this.primaryFunds = primaryFunds;
        this.localFunds = localFunds;
        this.users = users;
        this.phoneNumbers = phoneNumbers;
        this.passwordService = passwordService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.context = context;
        this.adminAudit = adminAudit;
    }

    /**
     * phone et/ou email (au moins un) : compte existant rattaché, sinon créé avec mot de passe provisoire
     * (prénom et nom requis). replaceMembershipId : trésorier actuel à remplacer (« Changer »).
     */
    public record DesignateTreasurerRequest(@NotNull UUID localFundId, @Size(max = 30) String phone,
                                            @jakarta.validation.constraints.Email @Size(max = 320) String email,
                                            @Size(max = 100) String firstName, @Size(max = 100) String lastName,
                                            UUID replaceMembershipId) {
    }

    /** Résultat d'une recherche exacte : jamais de liste, seulement le compte correspondant. */
    public record AccountLookupResponse(boolean exists, String displayName, String maskedPhone, boolean alreadyTreasurer) {
    }

    /** temporaryPassword n'est renseigné que pour un compte créé à l'instant. */
    public record TreasurerDesignationResponse(FundMembershipResponse membership, boolean accountCreated,
                                               String temporaryPassword) {
    }

    public record TemporaryPasswordResponse(String temporaryPassword, String maskedPhone) {
    }

    @Transactional
    public TreasurerDesignationResponse designate(UUID managerId, UUID primaryFundId,
                                                  DesignateTreasurerRequest request) {
        fundAccess.requireManager(managerId, primaryFundId);
        UUID associationId = context.requireId();
        PrimaryFundEntity primaryFund = primaryFunds.findById(primaryFundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caisse principale introuvable."));
        LocalFundEntity localFund = localFunds.findById(request.localFundId())
                .filter(fund -> fund.getPrimaryFund().getId().equals(primaryFundId))
                .filter(fund -> fund.getAssociation().getId().equals(associationId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La caisse locale ne dépend pas de cette association et caisse principale."));
        String phone = blank(request.phone()) ? null : phoneNumbers.normalize(request.phone());
        String email = blank(request.email()) ? null : request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (phone == null && email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indiquez le téléphone ou l’e-mail du trésorier.");
        }

        String temporaryPassword = null;
        UserAccountEntity account = findAccount(phone, email);
        if (account == null) {
            String displayName = displayName(request);
            if ((phone != null && users.existsByPhoneE164(phone)) || (email != null && users.existsByEmailIgnoreCase(email))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ce téléphone et cet e-mail correspondent à deux comptes différents : n’en indiquez qu’un.");
            }
            temporaryPassword = passwordService.generateTemporaryPassword();
            account = new UserAccountEntity(email, phone, displayName, passwordEncoder.encode(temporaryPassword),
                    "TRESORIER");
            account.setTemporaryPasswordHash(account.getPasswordHash());
            account = users.save(account);
            audit.record(AuthEvent.TREASURER_ACCOUNT_CREATED, account.getId(),
                    AuthAuditService.hint(phone != null ? phone : email), "Créé par le responsable " + managerId);
        } else if (!account.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce compte est désactivé.");
        }
        if (fundAccess.isAssociationManager(account.getId(), associationId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un responsable ne peut pas être affecté comme trésorier.");
        }
        UserAccountEntity treasurer = account;
        FundMembershipEntity membership = memberships.findByUserAccountIdAndLocalFundId(treasurer.getId(), localFund.getId())
                .orElseGet(() -> new FundMembershipEntity(treasurer, primaryFund, localFund, FundRole.TRESORIER));
        membership.assignTo(localFund);
        membership = memberships.save(membership);

        UserAccountEntity manager = users.findById(managerId).orElseThrow();
        if (request.replaceMembershipId() != null && !request.replaceMembershipId().equals(membership.getId())) {
            FundMembershipEntity previous = memberships.findByIdAndPrimaryFundIdAndActiveTrue(request.replaceMembershipId(), primaryFundId)
                    .filter(m -> m.getRole() == FundRole.TRESORIER)
                    .filter(m -> m.getLocalFund() != null && m.getLocalFund().getId().equals(localFund.getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trésorier à remplacer introuvable."));
            previous.deactivate();
            adminAudit.record(manager, "TREASURER_REMOVED", "LOCAL_FUND", localFund.getId(), localFund.getAssociation(),
                    "Remplacé : " + label(previous.getUserAccount()) + " (caisse " + localFund.getName() + ")");
        }
        adminAudit.record(manager, "TREASURER_ASSIGNED", "LOCAL_FUND", localFund.getId(), localFund.getAssociation(),
                label(treasurer) + (temporaryPassword != null ? " (compte créé)" : " (compte existant)")
                        + " · caisse " + localFund.getName());
        // Emplacement réservé : invitation par e-mail via fund_invitations, à activer avec l'envoi d'e-mails.
        return new TreasurerDesignationResponse(FundMembershipResponse.from(membership), temporaryPassword != null,
                temporaryPassword);
    }

    /** Recherche exacte par téléphone ou e-mail (réservée au responsable), pour proposer le rattachement. */
    @Transactional(readOnly = true)
    public AccountLookupResponse lookup(UUID managerId, UUID primaryFundId, String identifier) {
        fundAccess.requireManager(managerId, primaryFundId);
        String value = identifier == null ? "" : identifier.trim();
        UserAccountEntity account = value.contains("@")
                ? users.findByEmailIgnoreCase(value).orElse(null)
                : phoneNumbers.tryNormalize(value).flatMap(users::findByPhoneE164).orElse(null);
        if (account == null || !account.isEnabled()) return new AccountLookupResponse(false, null, null, false);
        boolean treasurerHere = memberships.findAllByUserAccountIdAndAssociationIdAndActiveTrueOrderByCreatedAtAsc(
                account.getId(), context.requireId()).stream().anyMatch(m -> m.getRole() == FundRole.TRESORIER);
        return new AccountLookupResponse(true, account.getDisplayName(),
                account.getPhoneE164() == null ? null : phoneNumbers.mask(account.getPhoneE164()), treasurerHere);
    }

    private UserAccountEntity findAccount(String phone, String email) {
        if (phone != null) {
            UserAccountEntity byPhone = users.findByPhoneE164(phone).orElse(null);
            if (byPhone != null) return byPhone;
        }
        return email == null ? null : users.findByEmailIgnoreCase(email).orElse(null);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String label(UserAccountEntity account) {
        String name = account.getDisplayName() == null ? "Sans nom" : account.getDisplayName();
        return name + " [" + account.getId() + "]";
    }

    /**
     * Le responsable remet un nouveau mot de passe provisoire à un trésorier qui l'a oublié.
     * Refusé si le compte sert aussi ailleurs (autre association, responsable, admin) : un
     * responsable ne doit pas pouvoir prendre la main sur un compte qu'il ne gère pas seul.
     * L'administrateur BERETA réinitialise alors le mot de passe.
     */
    @Transactional
    public TemporaryPasswordResponse issueTemporaryPassword(UUID managerId, UUID primaryFundId, UUID membershipId) {
        fundAccess.requireManager(managerId, primaryFundId);
        UUID associationId = context.requireId();
        FundMembershipEntity membership = memberships.findByIdAndPrimaryFundIdAndActiveTrue(membershipId, primaryFundId)
                .filter(m -> m.getAssociation().getId().equals(associationId))
                .filter(m -> m.getRole() == FundRole.TRESORIER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trésorier introuvable."));
        UserAccountEntity account = users.findByIdForUpdate(membership.getUserAccount().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trésorier introuvable."));
        boolean usedElsewhere = "ADMIN".equals(account.getRole())
                || !associationMemberships.findAllByUserAccountIdAndActiveTrueOrderByCreatedAtAsc(account.getId()).isEmpty()
                || memberships.findAllByUserAccountIdAndActiveTrueOrderByCreatedAtAsc(account.getId()).stream()
                .anyMatch(m -> !m.getAssociation().getId().equals(associationId));
        if (usedElsewhere) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce compte est aussi utilisé dans une autre "
                    + "association : demandez à l’administrateur BERETA de réinitialiser son mot de passe.");
        }
        String temporaryPassword = passwordService.generateTemporaryPassword();
        account.setTemporaryPasswordHash(passwordEncoder.encode(temporaryPassword));
        account.resetFailedLogins();
        account.bumpTokenVersion();
        refreshTokens.revokeAll(account.getId(), "TEMPORARY_PASSWORD");
        audit.record(AuthEvent.TEMPORARY_PASSWORD_ISSUED, account.getId(),
                AuthAuditService.hint(account.getPhoneE164()), "Par le responsable " + managerId);
        return new TemporaryPasswordResponse(temporaryPassword, phoneNumbers.mask(account.getPhoneE164()));
    }

    private static String displayName(DesignateTreasurerRequest request) {
        String first = request.firstName() == null ? "" : request.firstName().trim();
        String last = request.lastName() == null ? "" : request.lastName().trim();
        if (first.isEmpty() || last.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aucun compte n’existe pour ce téléphone ou cet e-mail : indiquez le prénom et le nom du trésorier.");
        }
        return first + " " + last;
    }
}
