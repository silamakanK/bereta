package com.bereta.funds;

import com.bereta.associations.AssociationEntity;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "local_funds")
public class LocalFundEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_fund_id", nullable = false)
    private PrimaryFundEntity primaryFund;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "association_id", nullable = false)
    private AssociationEntity association;

    @Column(nullable = false, length = 100)
    private String name;

    /** Adresse ou lieu de réunion (facultatif). */
    @Column(length = 200)
    private String location;

    /** Objectif spécifique ou remarque (facultatif). */
    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LocalFundEntity() {
    }

    public LocalFundEntity(PrimaryFundEntity primaryFund, String name) {
        this(primaryFund, name, null, null);
    }

    public LocalFundEntity(PrimaryFundEntity primaryFund, String name, String location, String description) {
        this.primaryFund = primaryFund;
        this.association = primaryFund.getAssociation();
        this.name = name;
        this.location = location;
        this.description = description;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public PrimaryFundEntity getPrimaryFund() { return primaryFund; }
    public AssociationEntity getAssociation() { return association; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
