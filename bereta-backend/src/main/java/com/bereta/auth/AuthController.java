package com.bereta.auth;

import com.bereta.auth.AuthRequests.ChangePassword;
import com.bereta.auth.AuthRequests.EmailCode;
import com.bereta.auth.AuthRequests.ForgotPassword;
import com.bereta.auth.AuthRequests.Login;
import com.bereta.auth.AuthRequests.PhoneEnrollment;
import com.bereta.auth.AuthRequests.Register;
import com.bereta.auth.AuthRequests.ResendCode;
import com.bereta.auth.AuthRequests.ResetPasswordWithCode;
import com.bereta.auth.AuthRequests.ResetPasswordWithLink;
import com.bereta.auth.AuthRequests.VerifyCode;
import com.bereta.auth.AuthResponses.CodeSentResponse;
import com.bereta.auth.AuthResponses.ForgotPasswordResponse;
import com.bereta.auth.AuthResponses.LoginStepResponse;
import com.bereta.auth.AuthResponses.MessageResponse;
import com.bereta.auth.AuthResponses.RegistrationResponse;
import com.bereta.auth.AuthResponses.TokenResponse;
import com.bereta.auth.otp.OtpService;
import com.bereta.auth.token.RefreshTokenService;
import com.bereta.auth.token.SessionTokenIssuer;
import com.bereta.auth.token.SessionTokenIssuer.IssuedSession;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** En-tête exigé sur les routes qui lisent le cookie : un formulaire tiers ne peut pas l'envoyer. */
    public static final String REFRESH_HEADER = "X-Bereta-Refresh";

    private final RegistrationService registration;
    private final LoginService login;
    private final PasswordService passwords;
    private final EmailVerificationService emailVerification;
    private final OtpService otp;
    private final SessionTokenIssuer sessions;
    private final RefreshTokenService refreshTokens;
    private final UserAccountRepository users;
    private final AuthAuditService audit;

    public AuthController(RegistrationService registration, LoginService login, PasswordService passwords,
                          EmailVerificationService emailVerification, OtpService otp, SessionTokenIssuer sessions,
                          RefreshTokenService refreshTokens, UserAccountRepository users, AuthAuditService audit) {
        this.registration = registration;
        this.login = login;
        this.passwords = passwords;
        this.emailVerification = emailVerification;
        this.otp = otp;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.audit = audit;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody Register request) {
        return noStore(HttpStatus.CREATED).body(registration.register(request));
    }

    @PostMapping("/register/verify")
    public ResponseEntity<TokenResponse> verifyRegistration(@Valid @RequestBody VerifyCode request) {
        return withSession(sessions.issue(registration.verifyPhone(request.challengeId(), request.code())));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginStepResponse> login(@Valid @RequestBody Login request) {
        return noStore(HttpStatus.OK).body(login.start(request.identifier(), request.password()));
    }

    @PostMapping("/login/phone")
    public ResponseEntity<LoginStepResponse> enrollPhone(@Valid @RequestBody PhoneEnrollment request) {
        return noStore(HttpStatus.OK).body(login.enrollPhone(request.enrollmentToken(), request.phone()));
    }

    @PostMapping("/login/verify")
    public ResponseEntity<TokenResponse> verifyLogin(@Valid @RequestBody VerifyCode request) {
        return withSession(sessions.issue(login.complete(request.challengeId(), request.code())));
    }

    @PostMapping("/otp/resend")
    public ResponseEntity<CodeSentResponse> resend(@Valid @RequestBody ResendCode request) {
        return noStore(HttpStatus.OK).body(CodeSentResponse.from(otp.resend(request.challengeId())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @RequestHeader(name = REFRESH_HEADER, required = false) String marker,
            @CookieValue(name = RefreshTokenService.COOKIE_NAME, required = false) String refreshToken) {
        requireMarker(marker);
        RefreshTokenService.Rotation rotation;
        try {
            rotation = refreshTokens.rotate(refreshToken);
        } catch (AuthException expired) {
            return ResponseEntity.status(expired.getStatus())
                    .header(HttpHeaders.SET_COOKIE, refreshTokens.clearedCookie().toString())
                    .body(null);
        }
        return withSession(sessions.withRefreshToken(rotation.user(), rotation.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(name = REFRESH_HEADER, required = false) String marker,
            @CookieValue(name = RefreshTokenService.COOKIE_NAME, required = false) String refreshToken,
            @AuthenticationPrincipal BeretaUserDetails user) {
        requireMarker(marker);
        refreshTokens.revoke(refreshToken);
        audit.record(AuthEvent.LOGOUT, user == null ? null : user.getId(), null, null);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokens.clearedCookie().toString()).build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutEverywhere(@AuthenticationPrincipal BeretaUserDetails user) {
        UserAccountEntity account = users.findById(user.getId()).orElseThrow(AuthException::invalidCredentials);
        account.bumpTokenVersion();
        users.save(account);
        refreshTokens.revokeAll(account.getId(), "LOGOUT_ALL");
        audit.record(AuthEvent.LOGOUT_ALL_DEVICES, account.getId(), null, null);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokens.clearedCookie().toString()).build();
    }

    @GetMapping("/me")
    public AuthUserResponse currentUser(@AuthenticationPrincipal BeretaUserDetails user) {
        return AuthUserResponse.from(user);
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPassword request) {
        return noStore(HttpStatus.OK).body(passwords.forgot(request.identifier()));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<MessageResponse> resetWithLink(@Valid @RequestBody ResetPasswordWithLink request) {
        passwords.resetWithLink(request.token(), request.password());
        return noStore(HttpStatus.OK).body(passwordResetDone());
    }

    @PostMapping("/password/reset/code")
    public ResponseEntity<MessageResponse> resetWithCode(@Valid @RequestBody ResetPasswordWithCode request) {
        passwords.resetWithCode(request.challengeId(), request.code(), request.password());
        return noStore(HttpStatus.OK).body(passwordResetDone());
    }

    /** Changement depuis le profil ou remplacement du mot de passe provisoire : nouvelle session. */
    @PostMapping("/password/change")
    public ResponseEntity<TokenResponse> changePassword(@AuthenticationPrincipal BeretaUserDetails user,
                                                        @Valid @RequestBody ChangePassword request) {
        return withSession(sessions.issue(
                passwords.change(user.getId(), request.currentPassword(), request.newPassword())));
    }

    @PostMapping("/email/send-code")
    public ResponseEntity<CodeSentResponse> sendEmailCode(@AuthenticationPrincipal BeretaUserDetails user) {
        return noStore(HttpStatus.OK).body(CodeSentResponse.from(emailVerification.send(user.getId())));
    }

    @PostMapping("/email/verify")
    public AuthUserResponse verifyEmail(@AuthenticationPrincipal BeretaUserDetails user,
                                        @Valid @RequestBody EmailCode request) {
        return AuthUserResponse.from(emailVerification.verify(user.getId(), request.code()));
    }

    private static MessageResponse passwordResetDone() {
        return new MessageResponse("Votre mot de passe a été modifié et vos appareils ont été déconnectés. "
                + "Vous pouvez vous connecter.");
    }

    private static void requireMarker(String marker) {
        if (!"1".equals(marker)) {
            throw new AuthException(HttpStatus.FORBIDDEN, "REFRESH_HEADER_MISSING", "Requête refusée.");
        }
    }

    private static ResponseEntity.BodyBuilder noStore(HttpStatus status) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore());
    }

    private static ResponseEntity<TokenResponse> withSession(IssuedSession session) {
        return noStore(HttpStatus.OK).header(HttpHeaders.SET_COOKIE, session.refreshCookie().toString())
                .body(session.body());
    }
}
