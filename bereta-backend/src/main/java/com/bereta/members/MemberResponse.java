package com.bereta.members;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
        UUID id,
        String firstName,
        String lastName,
        String phone,
        Instant createdAt
) {
    static MemberResponse from(MemberEntity member) {
        return new MemberResponse(
                member.getId(),
                member.getFirstName(),
                member.getLastName(),
                member.getPhone(),
                member.getCreatedAt()
        );
    }
}
