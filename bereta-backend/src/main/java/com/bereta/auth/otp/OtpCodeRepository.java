package com.bereta.auth.otp;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OtpCodeRepository extends JpaRepository<OtpCodeEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OtpCodeEntity c join fetch c.userAccount where c.challengeId = :challengeId "
            + "order by c.createdAt desc")
    List<OtpCodeEntity> findAllByChallengeForUpdate(@Param("challengeId") UUID challengeId);

    long countByChallengeIdAndCreatedAtAfter(UUID challengeId, Instant since);

    List<OtpCodeEntity> findAllByUserAccountIdAndTypeAndUsedAtIsNullAndInvalidatedAtIsNull(UUID userId, OtpType type);

    List<OtpCodeEntity> findAllByUserAccountIdAndTypeOrderByCreatedAtDesc(UUID userId, OtpType type);
}
