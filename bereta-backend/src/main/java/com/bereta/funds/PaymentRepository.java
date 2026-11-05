package com.bereta.funds;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {


    /** Paiements de l'association, caisse principale comprise (local_fund_id vide). */
    @Query("select p from PaymentEntity p join fetch p.memberCampaign mc join fetch mc.member " +
            "join fetch mc.campaign left join fetch p.localFund join fetch p.recordedBy " +
            "where p.association.id = :associationId order by p.paidOn desc, p.createdAt desc")
    List<PaymentEntity> findAllForAssociation(UUID associationId);

    @Query("select p from PaymentEntity p join fetch p.memberCampaign mc join fetch mc.member " +
            "join fetch mc.campaign left join fetch p.localFund join fetch p.recordedBy " +
            "where p.association.id = :associationId and mc.member.id = :memberId order by p.paidOn desc, p.createdAt desc")
    List<PaymentEntity> findAllForMemberInAssociation(UUID memberId, UUID associationId);

    @Query("select p.memberCampaign.id, sum(p.amount) from PaymentEntity p " +
            "where p.association.id = :associationId group by p.memberCampaign.id")
    List<Object[]> sumByEnrollmentForAssociation(UUID associationId);

    /** Encaissé directement par la caisse principale : compte tout de suite dans le solde central. */
    @Query("select coalesce(sum(p.amount), 0) from PaymentEntity p " +
            "where p.association.id = :associationId and p.localFund is null")
    BigDecimal sumDirectForAssociation(UUID associationId);

    @Query("select coalesce(sum(p.amount), 0) from PaymentEntity p where p.memberCampaign.id = :enrollmentId")
    BigDecimal sumForEnrollment(UUID enrollmentId);


    @Query("select coalesce(sum(p.amount), 0) from PaymentEntity p where p.localFund.id in :localFundIds")
    BigDecimal sumForLocalFunds(Collection<UUID> localFundIds);

    @Query("select p.localFund.id, sum(p.amount) from PaymentEntity p " +
            "where p.localFund.id in :localFundIds group by p.localFund.id")
    List<Object[]> sumByLocalFund(Collection<UUID> localFundIds);
}
