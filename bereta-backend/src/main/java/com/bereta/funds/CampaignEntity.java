package com.bereta.funds;

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
@Table(name = "campaigns")
public class CampaignEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_fund_id", nullable = false)
    private PrimaryFundEntity primaryFund;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_type", nullable = false, length = 20)
    private CampaignType campaignType;

    @Column(name = "campaign_year")
    private Integer campaignYear;

    @Column(name = "expected_amount", precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CampaignEntity() {
    }

    public CampaignEntity(PrimaryFundEntity primaryFund, String name, CampaignType campaignType,
                          Integer campaignYear, BigDecimal expectedAmount, LocalDate dueDate) {
        this.primaryFund = primaryFund;
        this.name = name;
        this.campaignType = campaignType;
        this.campaignYear = campaignYear;
        this.expectedAmount = expectedAmount;
        this.dueDate = dueDate;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public PrimaryFundEntity getPrimaryFund() { return primaryFund; }
    public String getName() { return name; }
    public CampaignType getCampaignType() { return campaignType; }
    public Integer getCampaignYear() { return campaignYear; }
    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public LocalDate getDueDate() { return dueDate; }
    public Instant getCreatedAt() { return createdAt; }
}
