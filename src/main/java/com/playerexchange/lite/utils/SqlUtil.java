package com.playerexchange.lite.utils;

import com.playerexchange.lite.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Utility for executing SQL schema statements in sequence.
 */
public final class SqlUtil {
    private SqlUtil() {
    }

    public static void executeBatch(DatabaseManager databaseManager, List<String> statements) {
        try (Connection connection = databaseManager.getConnection()) {
            for (String sql : statements) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.execute();
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to initialize schema", exception);
        }
    }
}
