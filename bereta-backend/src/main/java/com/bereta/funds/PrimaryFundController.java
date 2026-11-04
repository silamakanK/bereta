package com.bereta.funds;

import com.bereta.auth.BeretaUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/caisses-principales")
public class PrimaryFundController {

    private final PrimaryFundService primaryFundService;

    public PrimaryFundController(PrimaryFundService primaryFundService) {
        this.primaryFundService = primaryFundService;
    }

    @GetMapping
    public List<PrimaryFundResponse> findAll(@AuthenticationPrincipal BeretaUserDetails user) {
        return primaryFundService.findAll(user.getId());
    }

    @GetMapping("/{primaryFundId}")
    public PrimaryFundResponse findById(@AuthenticationPrincipal BeretaUserDetails user,
                                        @PathVariable UUID primaryFundId) {
        return primaryFundService.findById(user.getId(), primaryFundId);
    }

    @PostMapping
    public ResponseEntity<PrimaryFundResponse> create(@AuthenticationPrincipal BeretaUserDetails user,
                                                       @Valid @RequestBody PrimaryFundRequest request) {
        PrimaryFundResponse created = primaryFundService.create(user.getId(), request);
        URI location = URI.create("/api/caisses-principales/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{primaryFundId}/caisses-locales")
    public List<LocalFundResponse> findLocalFunds(@AuthenticationPrincipal BeretaUserDetails user,
                                                  @PathVariable UUID primaryFundId) {
        return primaryFundService.findLocalFunds(user.getId(), primaryFundId);
    }

    @GetMapping("/{primaryFundId}/caisses-locales/{localFundId}")
    public LocalFundResponse findLocalFund(@AuthenticationPrincipal BeretaUserDetails user,
                                           @PathVariable UUID primaryFundId, @PathVariable UUID localFundId) {
        return primaryFundService.findLocalFund(user.getId(), primaryFundId, localFundId);
    }

    @PostMapping("/{primaryFundId}/caisses-locales")
    public ResponseEntity<LocalFundResponse> createLocalFund(
            @AuthenticationPrincipal BeretaUserDetails user,
            @PathVariable UUID primaryFundId,
            @Valid @RequestBody LocalFundRequest request
    ) {
        LocalFundResponse created = primaryFundService.createLocalFund(user.getId(), primaryFundId, request);
        URI location = URI.create("/api/caisses-principales/" + primaryFundId + "/caisses-locales/" + created.id());
        return ResponseEntity.created(location).body(created);
    }
}
