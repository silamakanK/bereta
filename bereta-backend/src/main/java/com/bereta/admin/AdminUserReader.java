package com.bereta.admin;

import com.bereta.admin.AdminDtos.UserAssociation;
import com.bereta.admin.AdminDtos.UserDetail;
import com.bereta.admin.AdminDtos.UserFund;
import com.bereta.admin.AdminDtos.UserSummary;
import com.bereta.associations.AssociationMembershipRepository;
import com.bereta.auth.AuthAuditEntity;
import com.bereta.auth.AuthAuditRepository;
import com.bereta.auth.AuthEvent;
import com.bereta.auth.PhoneNumberService;
import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.auth.token.RefreshTokenRepository;
import com.bereta.funds.FundMembershipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.UUID;

/** Lecture des comptes : liste (coordonnées masquées) et fiche détaillée (coordonnées complètes). */
@Component
@Transactional(readOnly = true)
class AdminUserReader {

    private final UserAccountRepository users;
    private final AdminUserQuery query;
    private final AssociationMembershipRepository associationMemberships;
    private final FundMembershipRepository fundMemberships;
    private final AuthAuditRepository authAudit;
    private final RefreshTokenRepository refreshTokens;
    private final PhoneNumberService phones;
    private final Clock clock;

    AdminUserReader(UserAccountRepository users, AdminUserQuery query,
                    AssociationMembershipRepository associationMemberships, FundMembershipRepository fundMemberships,
                    AuthAuditRepository authAudit, RefreshTokenRepository refreshTokens, PhoneNumberService phones,
                    Clock clock) {
        this.users = users;
        this.query = query;
        this.associationMemberships = associationMemberships;
        this.fundMemberships = fundMemberships;
        this.authAudit = authAudit;
        this.refreshTokens = refreshTokens;
        this.phones = phones;
        this.clock = clock;
    }

    AdminPageResponse<UserSummary> list(AdminUserQuery.Filter filter, int page, int size) {
        return AdminPageResponse.from(query.search(filter, page, size), this::summary);
    }

    UserDetail detail(UUID id) {
        UserAccountEntity user = require(id);
        var associations = associationMemberships.findAllByUserAccountIdAndActiveTrueOrderByCreatedAtAsc(id).stream()
                .map(m -> new UserAssociation(m.getAssociation().getId(), m.getAssociation().getName(),
                        m.getAssociation().getStatus().name()))
                .toList();
        var funds = fundMemberships.findAllByUserAccountIdAndActiveTrueOrderByCreatedAtAsc(id).stream()
                .map(m -> new UserFund(m.getId(), m.getRole().name(), m.getAssociation().getId(),
                        m.getAssociation().getName(), m.getPrimaryFund().getName(),
                        m.getLocalFund() == null ? null : m.getLocalFund().getId(),
                        m.getLocalFund() == null ? null : m.getLocalFund().getName()))
                .toList();
        var lastLogin = authAudit.findFirstByUserAccountIdAndEventOrderByCreatedAtDesc(id, AuthEvent.LOGIN_SUCCEEDED.name())
                .map(AuthAuditEntity::getCreatedAt).orElse(null);
        return new UserDetail(user.getId(), user.getDisplayName(), phones.display(user.getPhoneE164()), user.getEmail(),
                user.getRole(), user.isEnabled(), user.isMustChangePassword(), user.getCreatedAt(), lastLogin,
                activeSessions(id), associations, funds);
    }

    UserSummary summary(UserAccountEntity user) {
        return new UserSummary(user.getId(), user.getDisplayName(),
                AdminMasking.phone(phones.display(user.getPhoneE164())), AdminMasking.email(user.getEmail()),
                user.getRole(), user.isEnabled(), user.isMustChangePassword(), user.getCreatedAt());
    }

    long activeSessions(UUID userId) {
        return refreshTokens.countByUserAccountIdAndRevokedAtIsNullAndExpiresAtAfter(userId, clock.instant());
    }

    UserAccountEntity require(UUID id) {
        return users.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte utilisateur introuvable."));
    }
}
