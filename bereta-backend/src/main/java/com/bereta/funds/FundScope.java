package com.bereta.funds;

import java.util.Set;
import java.util.UUID;

/**
 * Périmètre de l'utilisateur dans l'association active. Le responsable voit toute l'association,
 * caisse principale comprise ; le trésorier, uniquement ses caisses locales.
 */
public record FundScope(UUID associationId, boolean manager, Set<UUID> localFundIds) {

    /** {@code localFund} null = caisse principale, réservée au responsable. */
    public boolean covers(LocalFundEntity localFund) {
        if (manager) return true;
        return localFund != null && localFundIds.contains(localFund.getId());
    }
}
