package com.bereta.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final TreasurerInvitationService invitationService;
    private final UserRegistrationService registrationService;
    private final PasswordResetService passwordResetService;

    public AuthController(TreasurerInvitationService invitationService,
                          UserRegistrationService registrationService,
                          PasswordResetService passwordResetService) {
        this.invitationService = invitationService;
        this.registrationService = registrationService;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken, HttpServletResponse response) {
        response.setHeader("Cache-Control", CacheControl.noStore().getHeaderValue());
        csrfToken.getToken();
        return csrfToken;
    }

    @GetMapping("/me")
    public AuthUserResponse currentUser(@AuthenticationPrincipal BeretaUserDetails user) {
        return AuthUserResponse.from(user);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisteredAccountResponse register(@Valid @RequestBody RegisterRequest request,
                                              HttpServletResponse response) {
        response.setHeader("Cache-Control", CacheControl.noStore().getHeaderValue());
        return registrationService.register(request);
    }

    @PostMapping("/password/forgot")
    public PasswordResetMessage forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                               HttpServletResponse response) {
        response.setHeader("Cache-Control", CacheControl.noStore().getHeaderValue());
        return passwordResetService.requestReset(request.email());
    }

    @PostMapping("/password/reset")
    public PasswordResetMessage resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                              HttpServletResponse response) {
        response.setHeader("Cache-Control", CacheControl.noStore().getHeaderValue());
        return passwordResetService.reset(request);
    }

    @PostMapping("/invitations/accept")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthUserResponse acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request,
                                             HttpServletResponse response) {
        response.setHeader("Cache-Control", CacheControl.noStore().getHeaderValue());
        return invitationService.accept(request);
    }
}
