package com.bereta.admin;

import com.bereta.admin.AdminDtos.ActionRequest;
import com.bereta.admin.AdminDtos.AssociationDetail;
import com.bereta.admin.AdminDtos.AssociationSummary;
import com.bereta.admin.AdminDtos.TransferRequest;
import com.bereta.associations.AssociationStatus;
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

/** Vue d'ensemble et associations : liste, fiche, archivage, restauration, transfert du responsable. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService service;
    private final AdminAssociationService associations;

    public AdminController(AdminService service, AdminAssociationService associations) {
        this.service = service;
        this.associations = associations;
    }

    @GetMapping("/overview")
    public AdminOverviewResponse overview() {
        return service.overview();
    }

    @GetMapping("/associations")
    public AdminPageResponse<AssociationSummary> associations(@RequestParam(required = false) String q,
                                                              @RequestParam(required = false) AssociationStatus status,
                                                              @RequestParam(defaultValue = "createdAt") String sort,
                                                              @RequestParam(defaultValue = "desc") String direction,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return associations.list(new AdminAssociationQuery.Filter(q, status, sort, "asc".equalsIgnoreCase(direction)),
                page, size);
    }

    @GetMapping("/associations/{id}")
    public AssociationDetail association(@PathVariable UUID id) {
        return associations.detail(id);
    }

    @PostMapping("/associations/{id}/archive")
    public AssociationDetail archive(@AuthenticationPrincipal BeretaUserDetails admin, @PathVariable UUID id,
                                     @Valid @RequestBody ActionRequest request) {
        return associations.archive(admin.getId(), id, request.reason().trim());
    }

    @PostMapping("/associations/{id}/restore")
    public AssociationDetail restore(@AuthenticationPrincipal BeretaUserDetails admin, @PathVariable UUID id,
                                     @Valid @RequestBody ActionRequest request) {
        return associations.restore(admin.getId(), id, request.reason().trim());
    }

    @PostMapping("/associations/{id}/transfer")
    public AssociationDetail transfer(@AuthenticationPrincipal BeretaUserDetails admin, @PathVariable UUID id,
                                      @Valid @RequestBody TransferRequest request) {
        return associations.transfer(admin.getId(), id, request);
    }
}
