package com.bereta.funds;

import com.bereta.auth.BeretaUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api")
public class CashRemittanceController {

    private final CashRemittanceService remittanceService;

    public CashRemittanceController(CashRemittanceService remittanceService) {
        this.remittanceService = remittanceService;
    }

    @PostMapping("/caisses-locales/{localFundId}/remises")
    @ResponseStatus(HttpStatus.CREATED)
    public CashRemittanceResponse declare(@AuthenticationPrincipal BeretaUserDetails user,
                                           @PathVariable UUID localFundId,
                                           @Valid @RequestBody RemittanceRequest request) {
        return remittanceService.declare(user.getId(), localFundId, request);
    }

    @GetMapping("/caisses-locales/{localFundId}/remises/solde")
    public LocalFundCashBalanceResponse localBalance(@AuthenticationPrincipal BeretaUserDetails user,
                                                       @PathVariable UUID localFundId) {
        return remittanceService.localBalance(user.getId(), localFundId);
    }

    @GetMapping("/caisses-principales/{primaryFundId}/remises")
    public List<CashRemittanceResponse> list(@AuthenticationPrincipal BeretaUserDetails user,
                                              @PathVariable UUID primaryFundId) {
        return remittanceService.list(user.getId(), primaryFundId);
    }

    @GetMapping("/caisses-principales/{primaryFundId}/remises/synthese")
    public CashRemittanceSummaryResponse summary(@AuthenticationPrincipal BeretaUserDetails user,
                                                  @PathVariable UUID primaryFundId) {
        return remittanceService.summary(user.getId(), primaryFundId);
    }

    @PostMapping("/caisses-principales/{primaryFundId}/remises/{remittanceId}/confirmer")
    public CashRemittanceResponse confirm(@AuthenticationPrincipal BeretaUserDetails user,
                                          @PathVariable UUID primaryFundId,
                                          @PathVariable UUID remittanceId) {
        return remittanceService.confirm(user.getId(), primaryFundId, remittanceId);
    }

    @PostMapping("/caisses-principales/{primaryFundId}/remises/{remittanceId}/annuler")
    public CashRemittanceResponse cancel(@AuthenticationPrincipal BeretaUserDetails user,
                                         @PathVariable UUID primaryFundId,
                                         @PathVariable UUID remittanceId,
                                         @Valid @RequestBody CancelRemittanceRequest request) {
        return remittanceService.cancel(user.getId(), primaryFundId, remittanceId, request.reason());
    }
}
