package com.bereta.funds;

import com.bereta.auth.BeretaUserDetails;
import com.bereta.funds.TreasurerAccountService.DesignateTreasurerRequest;
import com.bereta.funds.TreasurerAccountService.TemporaryPasswordResponse;
import com.bereta.funds.TreasurerAccountService.TreasurerDesignationResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final TreasurerAccountService treasurerAccounts;

    public FundMembershipController(FundAccessService fundAccessService, TreasurerAccountService treasurerAccounts) {
        this.fundAccessService = fundAccessService;
        this.treasurerAccounts = treasurerAccounts;
    }

    @GetMapping
    public List<FundMembershipResponse> list(@AuthenticationPrincipal BeretaUserDetails user,
                                             @PathVariable UUID primaryFundId) {
        return fundAccessService.listMembers(user.getId(), primaryFundId);
    }

    /** Désigne (ou remplace) le trésorier d'une caisse locale : compte rattaché ou créé avec mot de passe provisoire. */
    @PostMapping
    public ResponseEntity<TreasurerDesignationResponse> designate(@AuthenticationPrincipal BeretaUserDetails user,
                                                                  @PathVariable UUID primaryFundId,
                                                                  @Valid @RequestBody DesignateTreasurerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(treasurerAccounts.designate(user.getId(), primaryFundId, request));
    }

    /** Recherche exacte par téléphone ou e-mail : « Compte BERETA existant détecté ». Responsable uniquement. */
    @GetMapping("/compte")
    public ResponseEntity<TreasurerAccountService.AccountLookupResponse> lookup(
            @AuthenticationPrincipal BeretaUserDetails user, @PathVariable UUID primaryFundId,
            @org.springframework.web.bind.annotation.RequestParam("identifiant") String identifier) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(treasurerAccounts.lookup(user.getId(), primaryFundId, identifier));
    }

    @PostMapping("/{membershipId}/mot-de-passe-provisoire")
    public ResponseEntity<TemporaryPasswordResponse> issueTemporaryPassword(
            @AuthenticationPrincipal BeretaUserDetails user,
            @PathVariable UUID primaryFundId, @PathVariable UUID membershipId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(treasurerAccounts.issueTemporaryPassword(user.getId(), primaryFundId, membershipId));
    }

    @DeleteMapping("/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal BeretaUserDetails user,
                           @PathVariable UUID primaryFundId, @PathVariable UUID membershipId) {
        fundAccessService.deactivateMembership(user.getId(), primaryFundId, membershipId);
    }
}
