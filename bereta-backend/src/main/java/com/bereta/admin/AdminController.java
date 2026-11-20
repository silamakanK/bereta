package com.bereta.admin;

import com.bereta.admin.AdminDtos.ActionRequest;
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

/** Vue d'ensemble et associations (lot suivant : transfert, restauration, caisses). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public AdminOverviewResponse overview() {
        return service.overview();
    }

    @GetMapping("/associations")
    public AdminPageResponse<AdminAssociationResponse> associations(@RequestParam(defaultValue = "") String q,
                                                                    @RequestParam(defaultValue = "0") int page) {
        return service.listAssociations(q, page);
    }

    @GetMapping("/associations/{id}")
    public AdminAssociationResponse association(@PathVariable UUID id) {
        return service.getAssociation(id);
    }

    @PostMapping("/associations/{id}/archive")
    public AdminAssociationResponse archive(@AuthenticationPrincipal BeretaUserDetails admin, @PathVariable UUID id,
                                            @Valid @RequestBody ActionRequest request) {
        return service.archiveAssociation(admin.getId(), id, request.reason().trim());
    }
}
