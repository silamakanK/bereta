package com.bereta.auth;

import com.bereta.auth.AuthRequests.ChangePassword;
import com.bereta.auth.AuthRequests.ForgotPassword;
import com.bereta.auth.AuthRequests.Login;
import com.bereta.auth.AuthRequests.Register;
import com.bereta.auth.AuthRequests.ResetPasswordWithLink;
import com.bereta.auth.AuthRequests.UpdateProfile;
import com.bereta.auth.AuthResponses.MessageResponse;
import com.bereta.auth.AuthResponses.TokenResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final ProfileService profiles;
    private final SessionTokenIssuer sessions;
    private final RefreshTokenService refreshTokens;
    private final UserAccountRepository users;
    private final AuthAuditService audit;

    public AuthController(RegistrationService registration, LoginService login, PasswordService passwords,
                          ProfileService profiles, SessionTokenIssuer sessions, RefreshTokenService refreshTokens,
                          UserAccountRepository users, AuthAuditService audit) {
        this.registration = registration;
        this.login = login;
        this.passwords = passwords;
        this.profiles = profiles;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.audit = audit;
    }

    /** Compte actif immédiatement : la session est ouverte dans la foulée. */
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody Register request) {
        return withSession(HttpStatus.CREATED, sessions.issue(registration.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody Login request) {
        return withSession(HttpStatus.OK, sessions.issue(login.login(request.identifier(), request.password())));
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
        return withSession(HttpStatus.OK, sessions.withRefreshToken(rotation.user(), rotation.refreshToken()));
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

    /** « Complète ton profil » : prénom, nom, e-mail. */
    @PatchMapping("/profile")
    public AuthUserResponse updateProfile(@AuthenticationPrincipal BeretaUserDetails user,
                                          @Valid @RequestBody UpdateProfile request) {
        return AuthUserResponse.from(profiles.update(user.getId(), request));
    }

    /** Désactivé tant que BERETA_FORGOT_PASSWORD_ENABLED=false (503 FORGOT_PASSWORD_DISABLED). */
    @PostMapping("/password/forgot")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPassword request) {
        return noStore(HttpStatus.OK).body(new MessageResponse(passwords.forgot(request.identifier())));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<MessageResponse> resetWithLink(@Valid @RequestBody ResetPasswordWithLink request) {
        passwords.resetWithLink(request.token(), request.password());
        return noStore(HttpStatus.OK).body(new MessageResponse(
                "Votre mot de passe a été modifié et vos appareils ont été déconnectés. Vous pouvez vous connecter."));
    }

    /** Changement depuis le profil ou remplacement du mot de passe provisoire : nouvelle session. */
    @PostMapping("/password/change")
    public ResponseEntity<TokenResponse> changePassword(@AuthenticationPrincipal BeretaUserDetails user,
                                                        @Valid @RequestBody ChangePassword request) {
        return withSession(HttpStatus.OK, sessions.issue(
                passwords.change(user.getId(), request.currentPassword(), request.newPassword())));
    }

    private static void requireMarker(String marker) {
        if (!"1".equals(marker)) {
            throw new AuthException(HttpStatus.FORBIDDEN, "REFRESH_HEADER_MISSING", "Requête refusée.");
        }
    }

    private static ResponseEntity.BodyBuilder noStore(HttpStatus status) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore());
    }

    private static ResponseEntity<TokenResponse> withSession(HttpStatus status, IssuedSession session) {
        return noStore(status).header(HttpHeaders.SET_COOKIE, session.refreshCookie().toString()).body(session.body());
    }
}
