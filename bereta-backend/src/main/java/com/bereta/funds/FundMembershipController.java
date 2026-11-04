package com.bereta.funds;

import com.bereta.auth.BeretaUserDetails;
import com.bereta.auth.TreasurerInvitationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/caisses-principales/{primaryFundId}/acces")
public class FundMembershipController {

    private final FundAccessService fundAccessService;
    private final TreasurerInvitationService invitationService;

    public FundMembershipController(FundAccessService fundAccessService,
                                    TreasurerInvitationService invitationService) {
        this.fundAccessService = fundAccessService;
        this.invitationService = invitationService;
    }

    @GetMapping
    public List<FundMembershipResponse> list(@AuthenticationPrincipal BeretaUserDetails user,
                                             @PathVariable UUID primaryFundId) {
        return fundAccessService.listMembers(user.getId(), primaryFundId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FundMembershipResponse assign(@AuthenticationPrincipal BeretaUserDetails user,
                                         @PathVariable UUID primaryFundId,
                                         @Valid @RequestBody AssignTreasurerRequest request) {
        return fundAccessService.assignTreasurer(user.getId(), primaryFundId, request);
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public TreasurerInvitationResponse invite(@AuthenticationPrincipal BeretaUserDetails user,
                                              @PathVariable UUID primaryFundId,
                                              @Valid @RequestBody CreateTreasurerInvitationRequest request,
                                              HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return invitationService.create(user.getId(), primaryFundId, request.email(), request.localFundId());
    }

    @DeleteMapping("/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal BeretaUserDetails user,
                           @PathVariable UUID primaryFundId, @PathVariable UUID membershipId) {
        fundAccessService.deactivateMembership(user.getId(), primaryFundId, membershipId);
    }
}
