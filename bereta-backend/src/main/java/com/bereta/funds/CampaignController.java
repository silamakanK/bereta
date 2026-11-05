package com.bereta.funds;

import com.bereta.auth.BeretaUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/campagnes")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @GetMapping
    public List<CampaignResponse> findAll(@AuthenticationPrincipal BeretaUserDetails user,
                                          @RequestParam(required = false) UUID caissePrincipaleId) {
        return campaignService.findAll(user.getId(), caissePrincipaleId);
    }

    @GetMapping("/{id}")
    public CampaignResponse findById(@AuthenticationPrincipal BeretaUserDetails user, @PathVariable UUID id) {
        return campaignService.findById(user.getId(), id);
    }

    @PostMapping
    public ResponseEntity<CampaignResponse> create(@AuthenticationPrincipal BeretaUserDetails user,
                                                   @Valid @RequestBody CampaignRequest request) {
        CampaignResponse created = campaignService.create(user.getId(), request);
        URI location = URI.create("/api/campagnes/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @PostMapping("/{id}/cloture")
    public CampaignResponse close(@AuthenticationPrincipal BeretaUserDetails user, @PathVariable UUID id) {
        return campaignService.close(user.getId(), id);
    }
}
