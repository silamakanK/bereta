package com.bereta.funds;

import com.bereta.associations.AssociationMembershipRepository;
import com.bereta.associations.AssociationRequestContext;
import com.bereta.auth.AccountStatus;
import com.bereta.auth.AuthAuditService;
import com.bereta.auth.AuthEvent;
import com.bereta.auth.PasswordService;
import com.bereta.auth.PhoneNumberService;
import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.auth.token.RefreshTokenService;
import com.bereta.notifications.AuthMessages;
import com.bereta.notifications.SmsSender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * à changer à la première connexion). Le trésorier vérifie son numéro par le code SMS
 * de sa première connexion.
 */
@Service
public class TreasurerAccountService {

    private static final Logger logger = LoggerFactory.getLogger(TreasurerAccountService.class);

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
    private final SmsSender smsSender;
    private final AuthAuditService audit;
    private final AssociationRequestContext context;

    public TreasurerAccountService(FundAccessService fundAccess, FundMembershipRepository memberships,
                                   AssociationMembershipRepository associationMemberships,
                                   PrimaryFundRepository primaryFunds, LocalFundRepository localFunds,
                                   UserAccountRepository users, PhoneNumberService phoneNumbers,
                                   PasswordService passwordService, PasswordEncoder passwordEncoder,
                                   RefreshTokenService refreshTokens, SmsSender smsSender, AuthAuditService audit,
                                   AssociationRequestContext context) {
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
        this.smsSender = smsSender;
        this.audit = audit;
        this.context = context;
    }

    public record DesignateTreasurerRequest(@NotNull UUID localFundId, @NotBlank @Size(max = 30) String phone,
                                            @Size(max = 100) String firstName, @Size(max = 100) String lastName) {
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
        String phone = phoneNumbers.normalize(request.phone());

        String temporaryPassword = null;
        UserAccountEntity account = users.findByPhoneE164(phone).orElse(null);
        if (account == null) {
            String displayName = displayName(request);
            temporaryPassword = passwordService.generateTemporaryPassword();
            account = new UserAccountEntity(null, phone, displayName, passwordEncoder.encode(temporaryPassword),
                    "TRESORIER", AccountStatus.ACTIVE);
            account.setTemporaryPasswordHash(account.getPasswordHash());
            account = users.save(account);
            audit.record(AuthEvent.TREASURER_ACCOUNT_CREATED, account.getId(), AuthAuditService.hint(phone),
                    "Créé par le responsable " + managerId);
        } else if (!account.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le compte lié à ce numéro est désactivé.");
        }
        if (fundAccess.isAssociationManager(account.getId(), associationId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un responsable ne peut pas être affecté comme trésorier.");
        }
        UserAccountEntity treasurer = account;
        FundMembershipEntity membership = memberships.findByUserAccountIdAndLocalFundId(treasurer.getId(), localFund.getId())
                .orElseGet(() -> new FundMembershipEntity(treasurer, primaryFund, localFund, FundRole.TRESORIER));
        membership.assignTo(localFund);
        membership = memberships.save(membership);
        notify(phone, AuthMessages.treasurerDesignated(localFund.getName(), localFund.getAssociation().getName()));
        return new TreasurerDesignationResponse(FundMembershipResponse.from(membership), temporaryPassword != null,
                temporaryPassword);
    }

    /**
     * Le responsable remet un nouveau mot de passe provisoire à un trésorier qui l'a oublié.
     * Refusé si le compte sert aussi ailleurs (autre association, responsable, admin) : un
     * responsable ne doit pas pouvoir prendre la main sur un compte qu'il ne gère pas seul.
     * Le trésorier passe alors par « Mot de passe oublié » (code SMS).
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
                    + "association : le trésorier doit utiliser « Mot de passe oublié » (code SMS).");
        }
        String temporaryPassword = passwordService.generateTemporaryPassword();
        account.setTemporaryPasswordHash(passwordEncoder.encode(temporaryPassword));
        account.resetFailedLogins();
        account.bumpTokenVersion();
        refreshTokens.revokeAll(account.getId(), "TEMPORARY_PASSWORD");
        audit.record(AuthEvent.TEMPORARY_PASSWORD_ISSUED, account.getId(),
                AuthAuditService.hint(account.getPhoneE164()), "Par le responsable " + managerId);
        if (account.getPhoneE164() != null) notify(account.getPhoneE164(), AuthMessages.TEMPORARY_PASSWORD_ISSUED);
        return new TemporaryPasswordResponse(temporaryPassword, phoneNumbers.mask(account.getPhoneE164()));
    }

    private static String displayName(DesignateTreasurerRequest request) {
        String first = request.firstName() == null ? "" : request.firstName().trim();
        String last = request.lastName() == null ? "" : request.lastName().trim();
        if (first.isEmpty() || last.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aucun compte n’existe pour ce numéro : indiquez le prénom et le nom du trésorier.");
        }
        return first + " " + last;
    }

    private void notify(String phone, String message) {
        try {
            smsSender.send(phone, message);
        } catch (RuntimeException deliveryFailure) {
            logger.warn("SMS au trésorier non envoyé : {}", deliveryFailure.getMessage());
        }
    }
}
