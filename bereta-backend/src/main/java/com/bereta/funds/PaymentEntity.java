package com.bereta.funds;

import com.bereta.associations.AssociationEntity;
import com.bereta.auth.UserAccountEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "association_id", nullable = false, updatable = false)
    private AssociationEntity association;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_campaign_id", nullable = false, updatable = false)
    private MemberCampaignEntity memberCampaign;

    /**
     * Caisse qui a encaissé, figée au moment du paiement (null = caisse principale) :
     * réaffecter le membre ensuite ne modifie pas les anciens paiements.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_fund_id", updatable = false)
    private LocalFundEntity localFund;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "paid_on", nullable = false, updatable = false)
    private LocalDate paidOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20, updatable = false)
    private PaymentMethod paymentMethod;

    @Column(length = 500, updatable = false)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_user_id", nullable = false, updatable = false)
    private UserAccountEntity recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentEntity() {
    }

    public PaymentEntity(MemberCampaignEntity memberCampaign, LocalFundEntity localFund,
                         BigDecimal amount, LocalDate paidOn, PaymentMethod paymentMethod,
                         String note, UserAccountEntity recordedBy) {
        this.memberCampaign = memberCampaign;
        this.association = memberCampaign.getAssociation();
        this.localFund = localFund;
        this.amount = amount;
        this.paidOn = paidOn;
        this.paymentMethod = paymentMethod;
        this.note = note;
        this.recordedBy = recordedBy;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public AssociationEntity getAssociation() { return association; }
    public MemberCampaignEntity getMemberCampaign() { return memberCampaign; }
    public LocalFundEntity getLocalFund() { return localFund; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getPaidOn() { return paidOn; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getNote() { return note; }
    public UserAccountEntity getRecordedBy() { return recordedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
