package com.bereta.auth;

import com.bereta.auth.otp.OtpChallenge;
import com.bereta.auth.otp.OtpChannel;

import java.util.UUID;

public final class AuthResponses {

    private AuthResponses() {
    }

    public enum LoginStep { OTP_REQUIRED, PHONE_REQUIRED }

    /** Étape 1 de la connexion : jamais de jeton d'accès, seulement un challenge. */
    public record LoginStepResponse(LoginStep step, UUID challengeId, String maskedPhone,
                                    Long expiresInSeconds, Long resendAvailableInSeconds,
                                    String enrollmentToken) {
        static LoginStepResponse otp(OtpChallenge challenge) {
            return new LoginStepResponse(LoginStep.OTP_REQUIRED, challenge.challengeId(),
                    challenge.maskedDestination(), challenge.expiresInSeconds(),
                    challenge.resendAvailableInSeconds(), null);
        }

        static LoginStepResponse phoneRequired(String enrollmentToken) {
            return new LoginStepResponse(LoginStep.PHONE_REQUIRED, null, null, null, null, enrollmentToken);
        }
    }

    public record CodeSentResponse(UUID challengeId, OtpChannel channel, String maskedDestination,
                                   long expiresInSeconds, long resendAvailableInSeconds) {
        static CodeSentResponse from(OtpChallenge challenge) {
            return new CodeSentResponse(challenge.challengeId(), challenge.channel(), challenge.maskedDestination(),
                    challenge.expiresInSeconds(), challenge.resendAvailableInSeconds());
        }
    }

    public record RegistrationResponse(CodeSentResponse phoneVerification, boolean emailVerificationSent) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds, AuthUserResponse user) {
    }

    /** Identique que le compte existe ou non. challengeId n'est fourni que pour un téléphone. */
    public record ForgotPasswordResponse(String message, OtpChannel channel, UUID challengeId,
                                         Long expiresInSeconds) {
    }

    public record MessageResponse(String message) {
    }
}
