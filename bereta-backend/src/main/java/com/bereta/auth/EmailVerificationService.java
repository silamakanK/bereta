package com.bereta.auth;

import com.bereta.auth.otp.OtpChallenge;
import com.bereta.auth.otp.OtpChannel;
import com.bereta.auth.otp.OtpCodeEntity;
import com.bereta.auth.otp.OtpCodeRepository;
import com.bereta.auth.otp.OtpService;
import com.bereta.auth.otp.OtpType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Vérification facultative de l'e-mail par un code à 6 chiffres envoyé à l'adresse. */
@Service
public class EmailVerificationService {

    private final UserAccountRepository users;
    private final OtpService otp;
    private final OtpCodeRepository codes;
    private final AuthAuditService audit;

    public EmailVerificationService(UserAccountRepository users, OtpService otp, OtpCodeRepository codes,
                                    AuthAuditService audit) {
        this.users = users;
        this.otp = otp;
        this.codes = codes;
        this.audit = audit;
    }

    @Transactional(noRollbackFor = AuthException.class)
    public OtpChallenge send(UserAccountEntity user) {
        if (user.getEmail() == null) {
            throw AuthException.badRequest("EMAIL_MISSING", "Aucune adresse e-mail n’est enregistrée sur ce compte.");
        }
        if (user.isEmailVerified()) {
            throw new AuthException(HttpStatus.CONFLICT, "EMAIL_ALREADY_VERIFIED", "Cette adresse e-mail est déjà vérifiée.");
        }
        return otp.issue(user, OtpType.EMAIL_VERIFY, OtpChannel.EMAIL, user.getEmail());
    }

    @Transactional
    public OtpChallenge send(UUID userId) {
        return send(users.findById(userId).orElseThrow(AuthException::invalidCredentials));
    }

    @Transactional(noRollbackFor = AuthException.class)
    public UserAccountEntity verify(UUID userId, String code) {
        UserAccountEntity user = users.findByIdForUpdate(userId).orElseThrow(AuthException::invalidCredentials);
        UUID challengeId = codes.findAllByUserAccountIdAndTypeOrderByCreatedAtDesc(userId, OtpType.EMAIL_VERIFY)
                .stream().findFirst().map(OtpCodeEntity::getChallengeId).orElse(null);
        OtpCodeEntity verified = otp.verify(challengeId, OtpType.EMAIL_VERIFY, code);
        if (!verified.getDestination().equalsIgnoreCase(user.getEmail())) {
            throw AuthException.badRequest("CODE_INVALID", "Ce code concerne une autre adresse. Demandez un nouveau code.");
        }
        user.verifyEmail();
        audit.record(AuthEvent.EMAIL_VERIFIED, user.getId(), AuthAuditService.hint(user.getEmail()), null);
        return user;
    }
}
