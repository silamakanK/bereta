package com.bereta.admin;

import com.bereta.admin.AdminDtos.Balance;
import com.bereta.admin.AdminDtos.FundDetail;
import com.bereta.admin.AdminDtos.FundSummary;
import com.bereta.admin.AdminDtos.ReassignTreasurerRequest;
import com.bereta.associations.AssociationMembershipRepository;
import com.bereta.auth.UserAccountEntity;
import com.bereta.funds.CashRemittanceRepository;
import com.bereta.funds.FundMembershipEntity;
import com.bereta.funds.FundMembershipRepository;
import com.bereta.funds.FundRole;
import com.bereta.funds.LocalFundEntity;
import com.bereta.funds.RemittanceStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Caisses locales vues par l'administrateur : consultation, archivage (lecture seule), restauration et
 * réaffectation du trésorier. Les encaissements passés gardent leur auteur et leur caisse d'origine.
 */
@Service
@Transactional(readOnly = true)
public class AdminFundService {

    private static final String TARGET = "LOCAL_FUND";
    private static final String STATUS = "status";

    private final AdminFundReader reader;
    private final AdminUserReader users;
    private final AdminFundFigures figures;
    private final FundMembershipRepository memberships;
    private final AssociationMembershipRepository associationMemberships;
    private final CashRemittanceRepository remittances;
    private final AdminAuditService audit;

    AdminFundService(AdminFundReader reader, AdminUserReader users, AdminFundFigures figures,
                     FundMembershipRepository memberships, AssociationMembershipRepository associationMemberships,
                     CashRemittanceRepository remittances, AdminAuditService audit) {
        this.reader = reader;
        this.users = users;
        this.figures = figures;
        this.memberships = memberships;
        this.associationMemberships = associationMemberships;
        this.remittances = remittances;
        this.audit = audit;
    }

    public AdminPageResponse<FundSummary> list(AdminFundQuery.Filter filter, int page, int size) {
        return reader.list(filter, page, size);
    }

    public FundDetail detail(UUID id) {
        return reader.detail(id);
    }

    /** Refusé tant qu'il reste des espèces sur place ou une remise à confirmer : l'argent ne doit pas rester bloqué. */
    @Transactional
    public FundDetail archive(UUID actorId, UUID id, String reason) {
        UserAccountEntity actor = users.require(actorId);
        LocalFundEntity fund = reader.requireForUpdate(id);
        if (!fund.isActive()) throw conflict("Cette caisse est déjà archivée.");
        BigDecimal pending = remittances.sumForLocalFundAndStatuses(id, List.of(RemittanceStatus.EN_ATTENTE));
        if (pending.signum() > 0) {
            throw conflict("Une remise de " + pending.toPlainString() + " € attend la confirmation du responsable : "
                    + "elle doit être confirmée ou annulée avant l’archivage.");
        }
        Balance balance = figures.of(List.of(id)).balance(id);
        if (balance.availableLocally().signum() > 0) {
            throw conflict("Il reste " + balance.availableLocally().toPlainString() + " € d’espèces dans cette caisse : "
                    + "le trésorier doit les remettre avant l’archivage.");
        }
        fund.archive();
        audit.record(actor, new AdminAuditService.Entry(AdminAction.LOCAL_FUND_ARCHIVED, TARGET, id,
                fund.getAssociation(), reason, Map.of(STATUS, LocalFundEntity.STATUS_ACTIVE),
                Map.of(STATUS, LocalFundEntity.STATUS_ARCHIVED)));
        return reader.detail(id);
    }

    @Transactional
    public FundDetail restore(UUID actorId, UUID id, String reason) {
        UserAccountEntity actor = users.require(actorId);
        LocalFundEntity fund = reader.requireForUpdate(id);
        if (fund.isActive()) throw conflict("Cette caisse est déjà active.");
        fund.restore();
        audit.record(actor, new AdminAuditService.Entry(AdminAction.LOCAL_FUND_RESTORED, TARGET, id,
                fund.getAssociation(), reason, Map.of(STATUS, LocalFundEntity.STATUS_ARCHIVED),
                Map.of(STATUS, LocalFundEntity.STATUS_ACTIVE)));
        return reader.detail(id);
    }

    /** replaceUserId : trésorier remplacé (désactivé) ; sans lui, le nouveau trésorier est ajouté. */
    @Transactional
    public FundDetail reassignTreasurer(UUID actorId, UUID id, ReassignTreasurerRequest request) {
        UserAccountEntity actor = users.require(actorId);
        LocalFundEntity fund = reader.requireForUpdate(id);
        if (!fund.isActive()) throw conflict("Cette caisse est archivée : restaurez-la avant d’y affecter un trésorier.");
        FundMembershipEntity previous = request.replaceUserId() == null ? null : currentTreasurer(id, request);
        UserAccountEntity next = users.require(request.toUserId());
        requireEligibleTreasurer(next, fund);

        FundMembershipEntity membership = memberships.findByUserAccountIdAndLocalFundId(next.getId(), id)
                .orElseGet(() -> new FundMembershipEntity(next, fund.getPrimaryFund(), fund, FundRole.TRESORIER));
        membership.assignTo(fund);
        memberships.save(membership);
        Map<String, Object> before = new LinkedHashMap<>();
        if (previous != null) {
            previous.deactivate();
            before.put("treasurerId", previous.getUserAccount().getId().toString());
            before.put("treasurerName", AdminFundFigures.label(previous.getUserAccount()));
        }
        audit.record(actor, new AdminAuditService.Entry(AdminAction.TREASURER_REASSIGNED, TARGET, id,
                fund.getAssociation(), request.reason().trim(), before,
                Map.of("treasurerId", next.getId().toString(), "treasurerName", AdminFundFigures.label(next))));
        return reader.detail(id);
    }

    private FundMembershipEntity currentTreasurer(UUID fundId, ReassignTreasurerRequest request) {
        if (request.replaceUserId().equals(request.toUserId())) {
            throw conflict("Choisissez un autre compte que le trésorier actuel.");
        }
        return memberships.findByUserAccountIdAndLocalFundId(request.replaceUserId(), fundId)
                .filter(m -> m.isActive() && m.getRole() == FundRole.TRESORIER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ce compte n’est pas trésorier de cette caisse."));
    }

    private void requireEligibleTreasurer(UserAccountEntity account, LocalFundEntity fund) {
        if (!account.isEnabled()) throw conflict("Ce compte est désactivé : réactivez-le ou choisissez-en un autre.");
        if ("ADMIN".equals(account.getRole())) throw conflict("Un administrateur ne peut pas être trésorier.");
        UUID associationId = fund.getAssociation().getId();
        if (associationMemberships.findByAssociationIdAndUserAccountIdAndActiveTrue(associationId, account.getId()).isPresent()) {
            throw conflict("Un responsable de l’association ne peut pas être affecté comme trésorier.");
        }
        boolean already = memberships.findByUserAccountIdAndLocalFundId(account.getId(), fund.getId())
                .filter(m -> m.isActive() && m.getRole() == FundRole.TRESORIER).isPresent();
        if (already) throw conflict("Ce compte est déjà trésorier de cette caisse.");
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
