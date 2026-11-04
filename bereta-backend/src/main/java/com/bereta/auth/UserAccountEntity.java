package com.bereta.auth;

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
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
public class UserAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, length = 320)
    private String email;

    @Column(name = "email_normalized", length = 320)
    private String emailNormalized;

    @Column(name = "phone_e164", unique = true, length = 16)
    private String phoneE164;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String role;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountStatus status;

    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserAccountEntity() {
    }

    public UserAccountEntity(String email, String displayName, String passwordHash, String role) {
        this(email, null, displayName, passwordHash, role, AccountStatus.ACTIVE);
    }

    public UserAccountEntity(String email, String phoneE164, String displayName, String passwordHash,
                             String role, AccountStatus status) {
        this.email = email;
        this.emailNormalized = email == null ? null : email.toLowerCase(Locale.ROOT);
        this.phoneE164 = phoneE164;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPhoneE164() { return phoneE164; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public AccountStatus getStatus() { return status; }
    public Instant getPhoneVerifiedAt() { return phoneVerifiedAt; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public Instant getLockedUntil() { return lockedUntil; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public int getTokenVersion() { return tokenVersion; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isLocked(Instant now) { return lockedUntil != null && lockedUntil.isAfter(now); }
    public boolean isPhoneVerified() { return phoneVerifiedAt != null; }
    public boolean isEmailVerified() { return emailVerifiedAt != null; }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = Instant.now();
        this.mustChangePassword = false;
    }

    /** Mot de passe provisoire fixé par un responsable : à changer à la prochaine connexion. */
    public void setTemporaryPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = Instant.now();
        this.mustChangePassword = true;
    }

    /** Invalide tous les access tokens déjà émis. */
    public void bumpTokenVersion() { tokenVersion++; }

    public void recordFailedLogin(int maxFailures, Instant lockUntil) {
        failedLoginCount++;
        if (failedLoginCount >= maxFailures) {
            lockedUntil = lockUntil;
            failedLoginCount = 0;
        }
    }

    public void resetFailedLogins() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

    public void verifyPhone(String phoneE164) {
        this.phoneE164 = phoneE164;
        this.phoneVerifiedAt = Instant.now();
        this.status = AccountStatus.ACTIVE;
    }

    public void verifyEmail() { this.emailVerifiedAt = Instant.now(); }
    public void acceptTerms() { this.termsAcceptedAt = Instant.now(); }

    public void updateProfile(String email, String displayName) {
        if (email != null && !email.equalsIgnoreCase(this.email)) emailVerifiedAt = null;
        this.email = email;
        this.emailNormalized = email == null ? null : email.toLowerCase(Locale.ROOT);
        this.displayName = displayName;
    }
    public void changeRole(String role) { this.role = role; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
