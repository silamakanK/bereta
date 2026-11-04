package com.bereta.funds;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashRemittanceRepository extends JpaRepository<CashRemittanceEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CashRemittanceEntity r join fetch r.primaryFund join fetch r.localFund " +
            "join fetch r.declaredBy where r.id = :id")
    Optional<CashRemittanceEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("select r from CashRemittanceEntity r join fetch r.primaryFund join fetch r.localFund " +
            "join fetch r.declaredBy left join fetch r.confirmedBy left join fetch r.cancelledBy " +
            "where r.localFund.id in :localFundIds order by r.declaredAt desc")
    List<CashRemittanceEntity> findAllForLocalFunds(Collection<UUID> localFundIds);

    @Query("select r.localFund.id, r.status, sum(r.amount) from CashRemittanceEntity r " +
            "where r.localFund.id in :localFundIds and r.status in :statuses group by r.localFund.id, r.status")
    List<Object[]> sumByLocalFundAndStatus(Collection<UUID> localFundIds,
                                           Collection<RemittanceStatus> statuses);

    @Query("select coalesce(sum(r.amount), 0) from CashRemittanceEntity r " +
            "where r.localFund.id = :localFundId and r.status in :statuses")
    BigDecimal sumForLocalFundAndStatuses(UUID localFundId, Collection<RemittanceStatus> statuses);

    @Query("select coalesce(sum(r.amount), 0) from CashRemittanceEntity r " +
            "where r.primaryFund.id = :primaryFundId and r.status = :status")
    BigDecimal sumForPrimaryFundAndStatus(UUID primaryFundId, RemittanceStatus status);
}
