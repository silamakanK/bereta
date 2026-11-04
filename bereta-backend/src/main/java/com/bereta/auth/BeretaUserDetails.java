package com.bereta.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class BeretaUserDetails implements UserDetails {

    private final UUID id;
    private final String email;
    private final String phoneE164;
    private final String displayName;
    private final String passwordHash;
    private final String role;
    private final boolean enabled;
    private final boolean mustChangePassword;
    private final boolean phoneVerified;
    private final boolean emailVerified;

    public BeretaUserDetails(UserAccountEntity account) {
        id = account.getId();
        email = account.getEmail();
        phoneE164 = account.getPhoneE164();
        displayName = account.getDisplayName();
        passwordHash = account.getPasswordHash();
        role = account.getRole();
        enabled = account.isEnabled();
        mustChangePassword = account.isMustChangePassword();
        phoneVerified = account.isPhoneVerified();
        emailVerified = account.isEmailVerified();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPhoneE164() { return phoneE164; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public boolean isPhoneVerified() { return phoneVerified; }
    public boolean isEmailVerified() { return emailVerified; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return id.toString(); }

    @Override
    public boolean isEnabled() { return enabled; }
}
