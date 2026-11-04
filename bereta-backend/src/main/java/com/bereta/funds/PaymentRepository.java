package com.bereta.funds;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    @Query("select p from PaymentEntity p join fetch p.memberCampaign mc join fetch mc.member " +
            "join fetch mc.campaign c join fetch c.primaryFund join fetch p.localFund " +
            "join fetch p.recordedBy where p.localFund.id in :localFundIds order by p.paidOn desc, p.createdAt desc")
    List<PaymentEntity> findAllForHistory(Collection<UUID> localFundIds);

    @Query("select p from PaymentEntity p join fetch p.memberCampaign mc join fetch mc.member " +
            "join fetch mc.campaign c join fetch c.primaryFund join fetch p.localFund " +
            "join fetch p.recordedBy where mc.member.id = :memberId and p.localFund.id in :localFundIds " +
            "order by p.paidOn desc, p.createdAt desc")
    List<PaymentEntity> findAllForMember(UUID memberId, Collection<UUID> localFundIds);

    @Query("select coalesce(sum(p.amount), 0) from PaymentEntity p where p.memberCampaign.id = :enrollmentId")
    BigDecimal sumForEnrollment(UUID enrollmentId);

    @Query("select p.memberCampaign.id, sum(p.amount) from PaymentEntity p " +
            "where p.localFund.id in :localFundIds group by p.memberCampaign.id")
    List<Object[]> sumByEnrollment(Collection<UUID> localFundIds);

    @Query("select coalesce(sum(p.amount), 0) from PaymentEntity p where p.localFund.id in :localFundIds")
    BigDecimal sumForLocalFunds(Collection<UUID> localFundIds);

    @Query("select p.localFund.id, sum(p.amount) from PaymentEntity p " +
            "where p.localFund.id in :localFundIds group by p.localFund.id")
    List<Object[]> sumByLocalFund(Collection<UUID> localFundIds);
}
