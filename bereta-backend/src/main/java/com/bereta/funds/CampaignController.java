package com.bereta.funds;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/campagnes")
@CrossOrigin(origins = "http://localhost:4200")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @GetMapping
    public List<CampaignResponse> findAll(@RequestParam(required = false) UUID caissePrincipaleId) {
        return campaignService.findAll(caissePrincipaleId);
    }

    @GetMapping("/{id}")
    public CampaignResponse findById(@PathVariable UUID id) {
        return campaignService.findById(id);
    }

    @PostMapping
    public ResponseEntity<CampaignResponse> create(@Valid @RequestBody CampaignRequest request) {
        CampaignResponse created = campaignService.create(request);
        URI location = URI.create("/api/campagnes/" + created.id());
        return ResponseEntity.created(location).body(created);
    }
}
