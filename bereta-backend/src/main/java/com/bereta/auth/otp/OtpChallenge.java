package com.bereta.auth.otp;

import java.util.UUID;

/** Ce que le client reçoit après l'envoi d'un code (jamais le code lui-même). */
public record OtpChallenge(UUID challengeId, OtpChannel channel, String maskedDestination,
                           long expiresInSeconds, long resendAvailableInSeconds) {
}
