package com.bereta.admin;

import com.bereta.admin.AdminDtos.ActionRequest;
import com.bereta.admin.AdminDtos.FundDetail;
import com.bereta.admin.AdminDtos.FundSummary;
import com.bereta.admin.AdminDtos.ReassignTreasurerRequest;
import com.bereta.auth.BeretaUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Caisses locales de toute la plateforme : liste, fiche, archivage, restauration, réaffectation du trésorier. */
@RestController
@RequestMapping("/api/admin/funds")
public class AdminFundController {

    private final AdminFundService funds;

    public AdminFundController(AdminFundService funds) {
        this.funds = funds;
    }

    @GetMapping
    public AdminPageResponse<FundSummary> list(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) UUID associationId,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "createdAt") String sort,
                                               @RequestParam(defaultValue = "desc") String direction,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return funds.list(new AdminFundQuery.Filter(q, associationId, status, sort, "asc".equalsIgnoreCase(direction)),
                page, size);
    }

    @GetMapping("/{id}")
    public FundDetail detail(@PathVariable UUID id) {
        return funds.detail(id);
    }

    @PostMapping("/{id}/archive")
    public FundDetail archive(@AuthenticationPrincipal BeretaUserDetails admin, @PathVariable UUID id,
                              @Valid @RequestBody ActionRequest request) {
        return funds.archive(admin.getId(), id, request.reason().trim());
    }

    @PostMapping("/{id}/restore")
    public FundDetail restore(@AuthenticationPrincipal BeretaUserDetails admin, @PathVariable UUID id,
                              @Valid @RequestBody ActionRequest request) {
        return funds.restore(admin.getId(), id, request.reason().trim());
    }

    @PostMapping("/{id}/reassign-treasurer")
    public FundDetail reassignTreasurer(@AuthenticationPrincipal BeretaUserDetails admin, @PathVariable UUID id,
                                        @Valid @RequestBody ReassignTreasurerRequest request) {
        return funds.reassignTreasurer(admin.getId(), id, request);
    }
}
