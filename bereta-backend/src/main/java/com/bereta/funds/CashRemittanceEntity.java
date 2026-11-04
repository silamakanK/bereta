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
@Table(name = "cash_remittances")
public class CashRemittanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "association_id", nullable = false, updatable = false)
    private AssociationEntity association;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_fund_id", nullable = false, updatable = false)
    private PrimaryFundEntity primaryFund;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "local_fund_id", nullable = false, updatable = false)
    private LocalFundEntity localFund;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "transferred_on", nullable = false, updatable = false)
    private LocalDate transferredOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "remittance_method", nullable = false, length = 20, updatable = false)
    private PaymentMethod method;

    @Column(length = 500, updatable = false)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RemittanceStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "declared_by_user_id", nullable = false, updatable = false)
    private UserAccountEntity declaredBy;

    @Column(name = "declared_at", nullable = false, updatable = false)
    private Instant declaredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_user_id")
    private UserAccountEntity confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_user_id")
    private UserAccountEntity cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    protected CashRemittanceEntity() {
    }

    public CashRemittanceEntity(PrimaryFundEntity primaryFund, LocalFundEntity localFund, BigDecimal amount,
                                LocalDate transferredOn, PaymentMethod method, String note,
                                UserAccountEntity declaredBy) {
        this.primaryFund = primaryFund;
        this.association = primaryFund.getAssociation();
        this.localFund = localFund;
        this.amount = amount;
        this.transferredOn = transferredOn;
        this.method = method;
        this.note = note;
        this.declaredBy = declaredBy;
        this.status = RemittanceStatus.EN_ATTENTE;
    }

    @PrePersist
    void onCreate() { declaredAt = Instant.now(); }

    public void confirm(UserAccountEntity user) {
        status = RemittanceStatus.CONFIRMEE;
        confirmedBy = user;
        confirmedAt = Instant.now();
    }

    public void cancel(UserAccountEntity user, String reason) {
        status = RemittanceStatus.ANNULEE;
        cancelledBy = user;
        cancelledAt = Instant.now();
        cancellationReason = reason;
    }

    public UUID getId() { return id; }
    public AssociationEntity getAssociation() { return association; }
    public PrimaryFundEntity getPrimaryFund() { return primaryFund; }
    public LocalFundEntity getLocalFund() { return localFund; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getTransferredOn() { return transferredOn; }
    public PaymentMethod getMethod() { return method; }
    public String getNote() { return note; }
    public RemittanceStatus getStatus() { return status; }
    public UserAccountEntity getDeclaredBy() { return declaredBy; }
    public Instant getDeclaredAt() { return declaredAt; }
    public UserAccountEntity getConfirmedBy() { return confirmedBy; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public UserAccountEntity getCancelledBy() { return cancelledBy; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
}
