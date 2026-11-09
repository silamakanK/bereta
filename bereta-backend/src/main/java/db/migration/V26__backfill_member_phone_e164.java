package db.migration;

import com.bereta.auth.PhoneNumberService;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Remplit {@code members.phone_e164} pour les membres existants, avec les mêmes règles que l'application
 * ({@link PhoneNumberService}, région par défaut {@code BERETA_PHONE_DEFAULT_REGION}, FR sinon).
 * Les numéros illisibles restent vides : {@code members.phone} n'est jamais modifié.
 */
public class V26__backfill_member_phone_e164 extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws SQLException {
        String region = System.getenv().getOrDefault("BERETA_PHONE_DEFAULT_REGION", "FR");
        backfill(context.getConnection(), new PhoneNumberService(region.isBlank() ? "FR" : region));
    }

    /** Visible pour les tests. Renvoie le nombre de membres mis à jour. */
    public static int backfill(Connection connection, PhoneNumberService phones) throws SQLException {
        int updated = 0;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, phone FROM members WHERE phone IS NOT NULL AND phone_e164 IS NULL");
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE members SET phone_e164 = ? WHERE id = ?");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                var normalized = phones.tryNormalize(rows.getString("phone"));
                if (normalized.isEmpty()) continue;
                update.setString(1, normalized.get());
                update.setObject(2, rows.getObject("id", UUID.class));
                update.addBatch();
                updated++;
            }
            if (updated > 0) update.executeBatch();
        }
        return updated;
    }
}
