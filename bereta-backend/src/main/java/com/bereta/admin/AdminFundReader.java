package com.bereta.admin;

import com.bereta.admin.AdminDtos.FundDetail;
import com.bereta.admin.AdminDtos.FundSummary;
import com.bereta.funds.LocalFundEntity;
import com.bereta.funds.LocalFundRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Lecture des caisses locales de toute la plateforme : liste paginée et fiche. */
@Component
@Transactional(readOnly = true)
class AdminFundReader {

    private final LocalFundRepository localFunds;
    private final AdminFundQuery query;
    private final AdminFundFigures figures;

    AdminFundReader(LocalFundRepository localFunds, AdminFundQuery query, AdminFundFigures figures) {
        this.localFunds = localFunds;
        this.query = query;
        this.figures = figures;
    }

    AdminPageResponse<FundSummary> list(AdminFundQuery.Filter filter, int page, int size) {
        var result = query.search(filter, page, size);
        AdminFundFigures.Figures numbers = figures.of(result.getContent().stream().map(LocalFundEntity::getId).toList());
        return AdminPageResponse.from(result, fund -> new FundSummary(fund.getId(), fund.getName(), fund.getLocation(),
                fund.getStatus(), fund.getAssociation().getId(), fund.getAssociation().getName(),
                fund.getAssociation().getStatus().name(),
                numbers.treasurersOf(fund.getId()).stream().map(AdminFundFigures::label).toList(),
                numbers.balance(fund.getId()), fund.getCreatedAt()));
    }

    FundDetail detail(UUID id) {
        LocalFundEntity fund = require(id);
        AdminFundFigures.Figures numbers = figures.of(List.of(id));
        return new FundDetail(fund.getId(), fund.getName(), fund.getLocation(), fund.getDescription(), fund.getStatus(),
                fund.getAssociation().getId(), fund.getAssociation().getName(), fund.getAssociation().getStatus().name(),
                fund.getPrimaryFund().getName(), fund.getCreatedAt(), figures.people(numbers.treasurersOf(id)),
                numbers.membersOf(id), numbers.balance(id));
    }

    LocalFundEntity require(UUID id) {
        return localFunds.findById(id).orElseThrow(AdminFundReader::notFound);
    }

    LocalFundEntity requireForUpdate(UUID id) {
        return localFunds.findByIdForUpdate(id).orElseThrow(AdminFundReader::notFound);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Caisse locale introuvable.");
    }
}
