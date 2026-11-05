package com.bereta.admin;

import com.bereta.associations.AddAssociationResponsibleRequest;
import com.bereta.auth.BeretaUserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) { this.service = service; }

    @GetMapping("/overview")
    public AdminOverviewResponse overview() { return service.overview(); }

    @GetMapping("/users")
    public AdminPageResponse<AdminUserResponse> users(@RequestParam(defaultValue = "") String q,
                                                       @RequestParam(defaultValue = "0") int page) {
        return service.listUsers(q, page);
    }

    @GetMapping("/users/{id}")
    public AdminUserResponse user(@PathVariable UUID id) { return service.getUser(id); }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse createUser(@AuthenticationPrincipal BeretaUserDetails actor,
                                        @Valid @RequestBody AdminUserCreateRequest request) {
        return service.createUser(actor.getId(), request);
    }

    @PutMapping("/users/{id}")
    public AdminUserResponse updateUser(@AuthenticationPrincipal BeretaUserDetails actor,
            @PathVariable UUID id, @Valid @RequestBody AdminUserUpdateRequest request) {
        return service.updateUser(actor.getId(), id, request);
    }

    /** Réinitialise le mot de passe : renvoie une seule fois un mot de passe provisoire à transmettre. */
    @PostMapping("/users/{id}/password")
    public com.bereta.auth.AuthResponses.TemporaryPasswordResponse resetPassword(
            @AuthenticationPrincipal BeretaUserDetails actor, @PathVariable UUID id) {
        return new com.bereta.auth.AuthResponses.TemporaryPasswordResponse(service.resetPassword(actor.getId(), id));
    }

    @PatchMapping("/users/{id}/activation")
    public AdminUserResponse setEnabled(@AuthenticationPrincipal BeretaUserDetails actor,
            @PathVariable UUID id, @RequestBody AdminEnabledRequest request) {
        return service.setEnabled(actor.getId(), id, request.enabled());
    }

    @GetMapping("/associations")
    public AdminPageResponse<AdminAssociationResponse> associations(
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page) {
        return service.listAssociations(q, page);
    }

    @GetMapping("/associations/{id}")
    public AdminAssociationResponse association(@PathVariable UUID id) { return service.getAssociation(id); }

    @PostMapping("/associations")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminAssociationResponse createAssociation(@AuthenticationPrincipal BeretaUserDetails actor,
            @Valid @RequestBody AdminAssociationRequest request) {
        return service.createAssociation(actor.getId(), request);
    }

    @PutMapping("/associations/{id}")
    public AdminAssociationResponse updateAssociation(@AuthenticationPrincipal BeretaUserDetails actor,
            @PathVariable UUID id, @Valid @RequestBody AdminAssociationUpdateRequest request) {
        return service.updateAssociation(actor.getId(), id, request);
    }

    @PostMapping("/associations/{id}/archive")
    public AdminAssociationResponse archiveAssociation(@AuthenticationPrincipal BeretaUserDetails actor,
                                                        @PathVariable UUID id) {
        return service.archiveAssociation(actor.getId(), id);
    }

    @PostMapping("/associations/{id}/responsables")
    @ResponseStatus(HttpStatus.CREATED)
    public com.bereta.associations.AssociationManagerResponse addAssociationResponsible(
            @AuthenticationPrincipal BeretaUserDetails actor, @PathVariable UUID id,
            @Valid @RequestBody AddAssociationResponsibleRequest request) {
        return service.addAssociationResponsible(actor.getId(), id, request);
    }

    @GetMapping("/audit")
    public AdminPageResponse<AdminAuditResponse> audit(@RequestParam(defaultValue = "0") int page) {
        return service.audit(page);
    }
}
