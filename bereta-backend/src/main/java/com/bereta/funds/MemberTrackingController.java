package com.bereta.funds;

import com.bereta.auth.BeretaUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Tableau de suivi des membres : caisse, inscriptions, payé / dû, statut. */
@RestController
public class MemberTrackingController {

    private final PaymentService paymentService;

    public MemberTrackingController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/api/membres/suivi")
    public List<MemberTrackingResponse> tracking(@AuthenticationPrincipal BeretaUserDetails user) {
        return paymentService.tracking(user.getId());
    }
}
