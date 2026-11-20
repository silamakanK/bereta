package com.bereta.admin;

import com.bereta.admin.AdminDtos.AssociationBalances;
import com.bereta.admin.AdminDtos.AssociationDetail;
import com.bereta.admin.AdminDtos.AssociationSummary;
import com.bereta.admin.AdminDtos.Balance;
import com.bereta.admin.AdminDtos.FundLine;
import com.bereta.associations.AssociationEntity;
import com.bereta.associations.AssociationMembershipEntity;
import com.bereta.associations.AssociationMembershipRepository;
import com.bereta.associations.AssociationRepository;
import com.bereta.funds.CampaignRepository;
import com.bereta.funds.LocalFundEntity;
import com.bereta.funds.LocalFundRepository;
import com.bereta.funds.PaymentRepository;
import com.bereta.funds.PrimaryFundEntity;
import com.bereta.funds.PrimaryFundRepository;
import com.bereta.members.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Lecture des associations : liste paginée et fiche (responsables, caisses, membres, campagnes, soldes). */
@Component
@Transactional(readOnly = true)
class AdminAssociationReader {

    private final AssociationRepository associations;
    private final AdminAssociationQuery query;
    private final AssociationMembershipRepository associationMemberships;
    private final PrimaryFundRepository primaryFunds;
    private final LocalFundRepository localFunds;
    private final MemberRepository members;
    private final CampaignRepository campaigns;
    private final PaymentRepository payments;
    private final AdminFundFigures figures;

    AdminAssociationReader(AssociationRepository associations, AdminAssociationQuery query,
                           AssociationMembershipRepository associationMemberships, PrimaryFundRepository primaryFunds,
                           LocalFundRepository localFunds, MemberRepository members, CampaignRepository campaigns,
                           PaymentRepository payments, AdminFundFigures figures) {
        this.associations = associations;
        this.query = query;
        this.associationMemberships = associationMemberships;
        this.primaryFunds = primaryFunds;
        this.localFunds = localFunds;
        this.members = members;
        this.campaigns = campaigns;
        this.payments = payments;
        this.figures = figures;
    }

    AdminPageResponse<AssociationSummary> list(AdminAssociationQuery.Filter filter, int page, int size) {
        return AdminPageResponse.from(query.search(filter, page, size), this::summary);
    }

    AssociationDetail detail(UUID id) {
        AssociationEntity association = require(id);
        List<AssociationMembershipEntity> responsibles = responsibles(id);
        PrimaryFundEntity primary = primaryFunds.findActiveByAssociationId(id).orElse(null);
        List<LocalFundEntity> locals = localFunds.findAllByAssociationIdOrderByNameAsc(id);
        AdminFundFigures.Figures numbers = figures.of(locals.stream().map(LocalFundEntity::getId).toList());
        List<FundLine> lines = locals.stream().map(local -> new FundLine(local.getId(), local.getName(),
                local.getLocation(), local.getStatus(), figures.people(numbers.treasurersOf(local.getId())),
                numbers.membersOf(local.getId()), numbers.balance(local.getId()))).toList();

        BigDecimal direct = payments.sumDirectForAssociation(id);
        AssociationBalances balances = new AssociationBalances(direct.add(total(lines, Balance::collected)),
                direct.add(numbers.confirmed()), total(lines, Balance::inTransit), total(lines, Balance::availableLocally));
        return new AssociationDetail(association.getId(), association.getName(), association.getDescription(),
                association.getContactEmail(), association.getContactPhone(), association.getAddress(),
                association.getStatus().name(), association.getCreatedAt(),
                responsibles.stream().map(m -> figures.person(m.getUserAccount())).toList(),
                primary == null ? null : primary.getId(), primary == null ? null : primary.getName(), lines,
                members.countByAssociationId(id), members.countByAssociationIdAndActiveTrue(id),
                campaigns.countByAssociationId(id), campaigns.countByAssociationIdAndStatus(id, "OUVERTE"), balances);
    }

    AssociationEntity require(UUID id) {
        return associations.findById(id).orElseThrow(AdminAssociationReader::notFound);
    }

    AssociationEntity requireForUpdate(UUID id) {
        return associations.findByIdForUpdate(id).orElseThrow(AdminAssociationReader::notFound);
    }

    private AssociationSummary summary(AssociationEntity association) {
        UUID id = association.getId();
        String responsible = responsibles(id).stream().findFirst()
                .map(m -> AdminFundFigures.label(m.getUserAccount())).orElse(null);
        return new AssociationSummary(id, association.getName(), association.getStatus().name(),
                association.getCreatedAt(), responsible, localFunds.countByAssociationId(id),
                members.countByAssociationId(id));
    }

    private List<AssociationMembershipEntity> responsibles(UUID associationId) {
        return associationMemberships.findAllByAssociationIdAndActiveTrueOrderByCreatedAtAsc(associationId);
    }

    private static BigDecimal total(List<FundLine> lines, Function<Balance, BigDecimal> amount) {
        return lines.stream().map(line -> amount.apply(line.balance())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Association introuvable.");
    }
}
