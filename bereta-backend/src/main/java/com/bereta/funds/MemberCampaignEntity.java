package com.bereta.funds;

import com.bereta.members.MemberEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "member_campaigns", uniqueConstraints = @UniqueConstraint(
        name = "uq_member_campaign", columnNames = {"member_id", "campaign_id"}))
public class MemberCampaignEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberEntity member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private CampaignEntity campaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "local_fund_id", nullable = false)
    private LocalFundEntity localFund;

    @Column(name = "expected_amount", precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MemberCampaignEntity() {
    }

    public MemberCampaignEntity(MemberEntity member, CampaignEntity campaign,
                                LocalFundEntity localFund, BigDecimal expectedAmount) {
        this.member = member;
        this.campaign = campaign;
        this.localFund = localFund;
        this.expectedAmount = expectedAmount;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public MemberEntity getMember() { return member; }
    public CampaignEntity getCampaign() { return campaign; }
    public LocalFundEntity getLocalFund() { return localFund; }
    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public Instant getCreatedAt() { return createdAt; }
}
