package com.bereta.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class PasswordResetService {
    private final UserAccountRepository userAccountRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<PasswordResetEmailSender> emailSenderProvider;
    private final String resetBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UserAccountRepository userAccountRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                ObjectProvider<PasswordResetEmailSender> emailSenderProvider,
                                @Value("${bereta.auth.reset.base-url}") String resetBaseUrl) {
        this.userAccountRepository = userAccountRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSenderProvider = emailSenderProvider;
        this.resetBaseUrl = resetBaseUrl.replaceAll("/$", "");
    }

    @Transactional
    public PasswordResetMessage requestReset(String suppliedEmail) {
        PasswordResetEmailSender emailSender = emailSenderProvider.getIfAvailable();
        if (emailSender == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La réinitialisation par e-mail n’est pas configurée sur ce serveur.");
        }
        String email = suppliedEmail.trim().toLowerCase(Locale.ROOT);
        UserAccountEntity account = userAccountRepository.findByEmailIgnoreCase(email)
                .filter(UserAccountEntity::isEnabled).orElse(null);
        if (account == null) return new PasswordResetMessage(genericMessage());

        UserAccountEntity lockedAccount = userAccountRepository.findByIdForUpdate(account.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, genericMessage()));
        tokenRepository.findAllByUserAccountIdAndUsedAtIsNull(lockedAccount.getId())
                .forEach(PasswordResetTokenEntity::markUsed);

        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        tokenRepository.save(new PasswordResetTokenEntity(lockedAccount, hashToken(token),
                Instant.now().plus(30, ChronoUnit.MINUTES)));
        String resetUrl = resetBaseUrl + "/reinitialiser-mot-de-passe?token=" + token;
        try {
            emailSender.sendResetLink(lockedAccount.getEmail(), resetUrl);
        } catch (RuntimeException deliveryFailure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Le service e-mail n’a pas pu traiter la demande. Réessayez plus tard.");
        }
        return new PasswordResetMessage(genericMessage());
    }

    @Transactional
    public PasswordResetMessage reset(ResetPasswordRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le mot de passe ne peut pas dépasser 72 octets UTF-8.");
        }
        String tokenHash = hashToken(request.token());
        PasswordResetTokenEntity candidate = tokenRepository.findUnusedForUpdate(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE,
                        "Ce lien de réinitialisation est invalide ou a déjà été utilisé."));
        if (!candidate.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Ce lien de réinitialisation a expiré. Demandez-en un nouveau.");
        }
        UserAccountEntity account = userAccountRepository.findByIdForUpdate(candidate.getUserAccount().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE,
                        "Ce lien de réinitialisation est invalide ou a déjà été utilisé."));
        PasswordResetTokenEntity token = tokenRepository.findUnusedForUpdate(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE,
                        "Ce lien de réinitialisation a déjà été utilisé."));
        if (!token.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Ce lien de réinitialisation a expiré. Demandez-en un nouveau.");
        }
        account.changePasswordHash(passwordEncoder.encode(request.password()));
        tokenRepository.findAllByUserAccountIdAndUsedAtIsNull(account.getId())
                .forEach(PasswordResetTokenEntity::markUsed);
        return new PasswordResetMessage("Votre mot de passe a été modifié. Vous pouvez vous connecter.");
    }

    private String genericMessage() {
        return "Si un compte correspond à cette adresse, un e-mail contenant le lien de réinitialisation a été envoyé.";
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponible", impossible);
        }
    }
}
