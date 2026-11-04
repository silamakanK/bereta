package com.bereta.associations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "associations")
public class AssociationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "contact_email", length = 320)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(length = 500)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssociationStatus status = AssociationStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssociationEntity() { }

    public AssociationEntity(String name, String description, String contactEmail,
                             String contactPhone, String address) {
        this.name = name;
        this.description = description;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.address = address;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public void archive() { status = AssociationStatus.ARCHIVED; }
    public void updateDetails(String name, String description, String contactEmail, String contactPhone, String address) {
        this.name = name;
        this.description = description;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.address = address;
    }
    public void setStatus(AssociationStatus status) { this.status = status; }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getAddress() { return address; }
    public AssociationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
