package com.bereta;

import com.bereta.auth.BeretaUserDetails;
import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.associations.AssociationEntity;
import com.bereta.associations.AssociationMembershipEntity;
import com.bereta.associations.AssociationMembershipRepository;
import com.bereta.associations.AssociationRepository;
import com.bereta.funds.FundMembershipEntity;
import com.bereta.funds.FundMembershipRepository;
import com.bereta.funds.FundRole;
import com.bereta.funds.PrimaryFundEntity;
import com.bereta.funds.PrimaryFundRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

public final class TestSecurity {
    private static final ThreadLocal<UUID> ACTIVE_ASSOCIATION = new ThreadLocal<>();

    private TestSecurity() {
    }

    public static UserAccountEntity account(UserAccountRepository accounts, PasswordEncoder encoder,
                                            String email, String role) {
        return accounts.findByEmailIgnoreCase(email).orElseGet(() -> accounts.save(new UserAccountEntity(
                email, "Compte de test", encoder.encode("TestPassword!234"), role)));
    }

    public static PrimaryFundEntity primaryFund(PrimaryFundRepository funds,
                                                FundMembershipRepository memberships,
                                                AssociationRepository associations,
                                                AssociationMembershipRepository associationMemberships,
                                                UserAccountEntity account, String name) {
        AssociationEntity association = associations.save(new AssociationEntity("Association " + name,
                "Association de test", null, null, null));
        associationMemberships.save(new AssociationMembershipEntity(association, account));
        ACTIVE_ASSOCIATION.set(association.getId());
        return funds.save(new PrimaryFundEntity(association, name, "Objectif de test", null));
    }

    public static AssociationEntity association(AssociationRepository associations,
                                                AssociationMembershipRepository memberships,
                                                UserAccountEntity account, String name) {
        AssociationEntity association = associations.save(new AssociationEntity(name, null, null, null, null));
        memberships.save(new AssociationMembershipEntity(association, account));
        ACTIVE_ASSOCIATION.set(association.getId());
        return association;
    }

    public static void activeAssociation(UUID associationId) { ACTIVE_ASSOCIATION.set(associationId); }

    public static RequestPostProcessor as(UserAccountEntity account) {
        return request -> {
            UUID associationId = ACTIVE_ASSOCIATION.get();
            if (associationId != null) request.addHeader("X-Association-Id", associationId.toString());
            return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                    .user(new BeretaUserDetails(account)).postProcessRequest(request);
        };
    }
}
