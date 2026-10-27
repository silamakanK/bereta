package com.bereta.funds;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/membres/{memberId}/inscriptions")
@CrossOrigin(origins = "http://localhost:4200")
public class MemberCampaignController {

    private final MemberCampaignService memberCampaignService;

    public MemberCampaignController(MemberCampaignService memberCampaignService) {
        this.memberCampaignService = memberCampaignService;
    }

    @GetMapping
    public List<MemberCampaignResponse> findAllForMember(@PathVariable UUID memberId) {
        return memberCampaignService.findAllForMember(memberId);
    }

    @PostMapping
    public ResponseEntity<MemberCampaignResponse> enroll(
            @PathVariable UUID memberId,
            @Valid @RequestBody MemberCampaignRequest request
    ) {
        MemberCampaignResponse created = memberCampaignService.enroll(memberId, request);
        URI location = URI.create("/api/membres/" + memberId + "/inscriptions/" + created.id());
        return ResponseEntity.created(location).body(created);
    }
}
