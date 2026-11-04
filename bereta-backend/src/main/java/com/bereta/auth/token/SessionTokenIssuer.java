package com.bereta.auth.token;

import com.bereta.auth.AuthResponses.TokenResponse;
import com.bereta.auth.AuthUserResponse;
import com.bereta.auth.UserAccountEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/** Délivre la paire access token (corps JSON) + refresh token (cookie HttpOnly). */
@Service
public class SessionTokenIssuer {

    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;

    public SessionTokenIssuer(AccessTokenService accessTokens, RefreshTokenService refreshTokens) {
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
    }

    public record IssuedSession(TokenResponse body, ResponseCookie refreshCookie) {
    }

    public IssuedSession issue(UserAccountEntity user) {
        return withRefreshToken(user, refreshTokens.issue(user));
    }

    public IssuedSession withRefreshToken(UserAccountEntity user, String refreshToken) {
        TokenResponse body = new TokenResponse(accessTokens.issueAccessToken(user), "Bearer",
                accessTokens.accessTtl().toSeconds(), AuthUserResponse.from(user));
        return new IssuedSession(body, refreshTokens.cookie(refreshToken));
    }
}
