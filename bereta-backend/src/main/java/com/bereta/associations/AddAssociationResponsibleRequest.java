package com.bereta.associations;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddAssociationResponsibleRequest(@NotBlank @Email @Size(max = 320) String email) { }
