package com.bereta.funds;

import java.time.Instant;

public record TreasurerInvitationResponse(String email, String localFundName, String token, Instant expiresAt) {
}
