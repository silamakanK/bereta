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
@RequestMapping("/api/paiements")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) { this.paymentService = paymentService; }

    @GetMapping
    public List<PaymentResponse> history(@AuthenticationPrincipal BeretaUserDetails user) {
        return paymentService.history(user.getId());
    }

    @GetMapping("/inscriptions-a-payer")
    public List<PaymentEnrollmentResponse> payableEnrollments(@AuthenticationPrincipal BeretaUserDetails user) {
        return paymentService.payableEnrollments(user.getId());
    }

    @GetMapping("/synthese")
    public PaymentSummaryResponse summary(@AuthenticationPrincipal BeretaUserDetails user) {
        return paymentService.summary(user.getId());
    }

    @GetMapping("/membres/{memberId}")
    public List<PaymentResponse> historyForMember(@AuthenticationPrincipal BeretaUserDetails user,
                                                   @PathVariable UUID memberId) {
        return paymentService.historyForMember(user.getId(), memberId);
    }

    @PostMapping("/inscriptions/{enrollmentId}")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse record(@AuthenticationPrincipal BeretaUserDetails user,
                                  @PathVariable UUID enrollmentId,
                                  @Valid @RequestBody PaymentRequest request) {
        return paymentService.record(user.getId(), enrollmentId, request);
    }
}
