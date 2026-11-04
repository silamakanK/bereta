package com.bereta.auth.token;

import com.bereta.auth.AccountStatus;
import com.bereta.auth.BeretaUserDetails;
import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Transforme un access token valide en principal {@link BeretaUserDetails} (utilisé
 * par tous les contrôleurs). Recharge le compte à chaque requête : un compte désactivé,
 * dont le rôle a changé ou dont la version de jeton a été incrémentée (nouveau mot de
 * passe, déconnexion de tous les appareils) est refusé immédiatement.
 */
@Component
public class BeretaJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserAccountRepository accounts;

    public BeretaJwtAuthenticationConverter(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (!AccessTokenService.ACCESS.equals(jwt.getClaimAsString(AccessTokenService.TYPE_CLAIM))) {
            throw new InvalidBearerTokenException("Jeton non utilisable pour l’API.");
        }
        UserAccountEntity account;
        try {
            account = accounts.findById(UUID.fromString(jwt.getSubject())).orElse(null);
        } catch (IllegalArgumentException invalidSubject) {
            account = null;
        }
        Number version = jwt.getClaim(AccessTokenService.VERSION_CLAIM);
        if (account == null || !account.isEnabled() || account.getStatus() != AccountStatus.ACTIVE
                || version == null || version.intValue() != account.getTokenVersion()
                || !account.getRole().equals(jwt.getClaimAsString("role"))) {
            throw new InvalidBearerTokenException("La session du compte n’est plus valide.");
        }
        BeretaUserDetails principal = new BeretaUserDetails(account);
        return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, principal.getAuthorities());
    }
}
