package com.bereta.imports;

import com.bereta.associations.AssociationEntity;
import com.bereta.auth.UserAccountEntity;
import com.bereta.funds.LocalFundEntity;
import com.bereta.funds.PrimaryFundEntity;
import com.bereta.imports.extraction.ImportSourceType;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "member_imports")
public class MemberImportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "association_id", nullable = false)
    private AssociationEntity association;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_fund_id", nullable = false)
    private PrimaryFundEntity primaryFund;

    /** Vide : les membres sont rattachés à la caisse principale. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_fund_id")
    private LocalFundEntity localFund;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private UserAccountEntity createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private ImportSourceType sourceType;

    @Column(name = "file_fingerprint", nullable = false, length = 64)
    private String fileFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportStatus status = ImportStatus.BROUILLON;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "ignored_count", nullable = false)
    private int ignoredCount;

    @Column(name = "enrolled_count", nullable = false)
    private int enrolledCount;

    /** Verrou optimiste : une correction et une validation simultanées ne peuvent pas toutes deux réussir. */
    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected MemberImportEntity() {
    }

    public MemberImportEntity(AssociationEntity association, PrimaryFundEntity primaryFund, LocalFundEntity localFund,
                              UserAccountEntity createdBy, ImportSourceType sourceType, String fileFingerprint) {
        this.association = association;
        this.primaryFund = primaryFund;
        this.localFund = localFund;
        this.createdBy = createdBy;
        this.sourceType = sourceType;
        this.fileFingerprint = fileFingerprint;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isDraft() { return status == ImportStatus.BROUILLON; }

    /** Marque une modification (les lignes sont dans une autre table) : met à jour la date et la version. */
    public void touch(int rowCount) {
        this.rowCount = rowCount;
        this.updatedAt = Instant.now();
    }

    public void validate(int created, int ignored, int enrolled) {
        status = ImportStatus.VALIDE;
        createdCount = created;
        ignoredCount = ignored;
        enrolledCount = enrolled;
        validatedAt = Instant.now();
    }

    public void cancel() {
        status = ImportStatus.ANNULE;
        cancelledAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AssociationEntity getAssociation() { return association; }
    public PrimaryFundEntity getPrimaryFund() { return primaryFund; }
    public LocalFundEntity getLocalFund() { return localFund; }
    public UserAccountEntity getCreatedBy() { return createdBy; }
    public ImportSourceType getSourceType() { return sourceType; }
    public String getFileFingerprint() { return fileFingerprint; }
    public ImportStatus getStatus() { return status; }
    public int getRowCount() { return rowCount; }
    public int getCreatedCount() { return createdCount; }
    public int getIgnoredCount() { return ignoredCount; }
    public int getEnrolledCount() { return enrolledCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getValidatedAt() { return validatedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}
