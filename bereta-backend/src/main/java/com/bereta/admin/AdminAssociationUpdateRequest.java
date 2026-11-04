package com.bereta.admin;

import com.bereta.associations.AssociationStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAssociationUpdateRequest(@NotBlank @Size(max=160) String name,
        @Size(max=1000) String description, @Email @Size(max=320) String contactEmail,
        @Size(max=30) String contactPhone, @Size(max=500) String address,
        AssociationStatus status) { }
