package db.vendor.h2;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Équivalent H2 (tests uniquement) de {@code db/migration/V16__on_delete_user_accounts.sql}.
 * H2 ne connaît pas les noms de contraintes générés par PostgreSQL ({@code *_fkey}) : on les lit dans
 * les métadonnées, puis on recrée chaque clé étrangère avec la règle ON DELETE voulue.
 */
public class V16__on_delete_user_accounts extends BaseJavaMigration {

    private static final String[][] CASCADE = {
            {"refresh_tokens", "user_account_id"},
            {"otp_codes", "user_account_id"},
            {"password_reset_tokens", "user_account_id"},
            {"association_memberships", "user_account_id"},
            {"fund_memberships", "user_account_id"},
    };

    private static final String[][] SET_NULL = {
            {"members", "created_by_user_id"},
            {"fund_invitations", "invited_by_user_id"},
            {"payments", "recorded_by_user_id"},
            {"cash_remittances", "declared_by_user_id"},
            {"cash_remittances", "confirmed_by_user_id"},
            {"cash_remittances", "cancelled_by_user_id"},
            {"admin_audit_log", "actor_user_id"},
    };

    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        for (String[] fk : CASCADE) replace(connection, fk[0], fk[1], "CASCADE");
        for (String[] fk : SET_NULL) {
            execute(connection, "ALTER TABLE " + fk[0] + " ALTER COLUMN " + fk[1] + " SET NULL");
            replace(connection, fk[0], fk[1], "SET NULL");
        }
    }

    private static void replace(Connection connection, String table, String column, String rule) throws SQLException {
        String constraint = null;
        try (ResultSet keys = connection.getMetaData().getImportedKeys(null, null, table.toUpperCase())) {
            while (keys.next()) {
                if (keys.getString("FKCOLUMN_NAME").equalsIgnoreCase(column)
                        && keys.getString("PKTABLE_NAME").equalsIgnoreCase("user_accounts")) {
                    constraint = keys.getString("FK_NAME");
                }
            }
        }
        if (constraint == null) throw new IllegalStateException("Clé étrangère introuvable : " + table + "." + column);
        String name = table + "_" + column + "_fkey";
        execute(connection, "ALTER TABLE " + table + " DROP CONSTRAINT \"" + constraint + "\"");
        execute(connection, "ALTER TABLE " + table + " ADD CONSTRAINT " + name + " FOREIGN KEY (" + column
                + ") REFERENCES user_accounts (id) ON DELETE " + rule);
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
