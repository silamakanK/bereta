package com.bereta.admin;

import com.bereta.associations.AssociationRepository;
import com.bereta.associations.AssociationStatus;
import com.bereta.auth.UserAccountRepository;
import com.bereta.funds.LocalFundRepository;
import com.bereta.funds.PrimaryFundRepository;
import com.bereta.members.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vue d'ensemble de la plateforme. Comptes : {@link AdminUserService} ; associations : {@link AdminAssociationService} ;
 * caisses : {@link AdminFundService}. La création ou la promotion d'un administrateur n'est possible que par la
 * procédure interne (docs/admin/promouvoir-admin.sql), jamais par l'API.
 */
@Service
@Transactional(readOnly = true)
public class AdminService {

    private final UserAccountRepository users;
    private final AssociationRepository associations;
    private final PrimaryFundRepository primaryFunds;
    private final LocalFundRepository localFunds;
    private final MemberRepository members;

    public AdminService(UserAccountRepository users, AssociationRepository associations,
                        PrimaryFundRepository primaryFunds, LocalFundRepository localFunds, MemberRepository members) {
        this.users = users;
        this.associations = associations;
        this.primaryFunds = primaryFunds;
        this.localFunds = localFunds;
        this.members = members;
    }

    public AdminOverviewResponse overview() {
        long primaryCount = primaryFunds.count();
        long localCount = localFunds.count();
        return new AdminOverviewResponse(associations.count(), associations.countByStatus(AssociationStatus.ACTIVE),
                users.count(), users.countByEnabledTrue(), primaryCount, localCount, primaryCount + localCount,
                members.count(), users.countByRoleAndEnabledTrue("ADMIN"));
    }
}
