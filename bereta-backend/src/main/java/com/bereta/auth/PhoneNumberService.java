package com.bereta.auth;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Normalise les numéros au format E.164 (+33612345678) et les masque pour l'affichage. */
@Service
public class PhoneNumberService {

    private final PhoneNumberUtil util = PhoneNumberUtil.getInstance();
    private final String defaultRegion;

    public PhoneNumberService(@Value("${bereta.phone.default-region:FR}") String defaultRegion) {
        this.defaultRegion = defaultRegion;
    }

    public String normalize(String raw) {
        return tryNormalize(raw).orElseThrow(() -> AuthException.badRequest("PHONE_INVALID",
                "Numéro de téléphone invalide. Exemple : 06 12 34 56 78 ou +223 76 12 34 56."));
    }

    public Optional<String> tryNormalize(String raw) {
        return tryNormalize(raw, defaultRegion);
    }

    /** Comme {@link #tryNormalize(String)}, en lisant les numéros sans indicatif avec la région donnée (ML, SN…). */
    public Optional<String> tryNormalize(String raw, String region) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            PhoneNumber number = util.parse(raw.trim(), region);
            if (!util.isValidNumber(number)) return Optional.empty();
            return Optional.of(util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164));
        } catch (NumberParseException invalid) {
            return Optional.empty();
        }
    }

    public String defaultRegion() {
        return defaultRegion;
    }

    /** +33612345678 → « +33 6 ** ** ** 78 ». */
    public String mask(String e164) {
        if (e164 == null) return null;
        try {
            PhoneNumber number = util.parse(e164, defaultRegion);
            String national = util.getNationalSignificantNumber(number);
            if (national.length() < 4) return "+" + number.getCountryCode() + " ****";
            int hidden = national.length() - 3;
            String stars = String.join(" ", java.util.Collections.nCopies((hidden + 1) / 2, "**"));
            return "+" + number.getCountryCode() + " " + national.charAt(0) + " " + stars + " "
                    + national.substring(national.length() - 2);
        } catch (NumberParseException invalid) {
            return "****";
        }
    }
}
