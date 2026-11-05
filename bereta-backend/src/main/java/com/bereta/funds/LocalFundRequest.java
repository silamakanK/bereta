package com.bereta.funds;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Maquette « Créer une caisse – étape 1 » : nom obligatoire, lieu et objectif facultatifs. */
public record LocalFundRequest(@NotBlank @Size(max = 100) String name,
                               @Size(max = 200) String location,
                               @Size(max = 500) String description) {
}
