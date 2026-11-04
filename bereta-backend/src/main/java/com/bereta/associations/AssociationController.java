package com.bereta.associations;

import com.bereta.auth.BeretaUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/associations")
public class AssociationController {
    private final AssociationAccessService service;
    public AssociationController(AssociationAccessService service) { this.service = service; }

    @GetMapping
    public List<AssociationResponse> list(@AuthenticationPrincipal BeretaUserDetails user) {
        return service.listFor(user.getId());
    }

    @PostMapping
    public ResponseEntity<AssociationResponse> create(@AuthenticationPrincipal BeretaUserDetails user,
                                                       @Valid @RequestBody CreateAssociationRequest request) {
        AssociationResponse created = service.create(user.getId(), request);
        return ResponseEntity.created(URI.create("/api/associations/" + created.id())).body(created);
    }

    @GetMapping("/{associationId}/responsables")
    public List<AssociationManagerResponse> listManagers(@AuthenticationPrincipal BeretaUserDetails user,
                                                          @PathVariable java.util.UUID associationId) {
        return service.listManagers(user.getId(), associationId);
    }

    @PostMapping("/{associationId}/responsables")
    public ResponseEntity<AssociationManagerResponse> addManager(@AuthenticationPrincipal BeretaUserDetails user,
            @PathVariable java.util.UUID associationId,
            @Valid @RequestBody AddAssociationResponsibleRequest request) {
        return ResponseEntity.status(201).body(service.addManager(user.getId(), associationId, request));
    }
}
