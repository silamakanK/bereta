package com.bereta.funds;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FundInvitationRepository extends JpaRepository<FundInvitationEntity, UUID> {
    Optional<FundInvitationEntity> findByTokenHashAndAcceptedAtIsNull(String tokenHash);
}
