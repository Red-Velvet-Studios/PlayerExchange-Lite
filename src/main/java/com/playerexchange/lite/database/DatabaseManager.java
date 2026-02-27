package com.playerexchange.lite.database;

import com.playerexchange.lite.utils.SqlUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * High-performance SQLite access layer backed by HikariCP.
 */
public final class DatabaseManager {
    private final Plugin plugin;
    private HikariDataSource dataSource;
    private double basePrice = 10.0;
    private double startingBalance = 1000.0;

    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        String jdbcUrl = "jdbc:sqlite:" + new File(dataFolder, "playerexchange-lite.db").getAbsolutePath();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setPoolName("px-lite");
        config.setMaximumPoolSize(10);
        config.setConnectionTestQuery("SELECT 1");
        config.setAutoCommit(true);

        dataSource = new HikariDataSource(config);
        createSchema();
    }

    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public PlayerRow getOrCreatePlayer(UUID uuid) {
        try (Connection connection = getConnection()) {
            long now = System.currentTimeMillis();
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO players (uuid, price, volume, last_update, frozen) VALUES (?, ?, ?, ?, ?)"
            )) {
                statement.setString(1, uuid.toString());
                statement.setDouble(2, basePrice);
                statement.setDouble(3, 0.0);
                statement.setLong(4, now);
                statement.setInt(5, 0);
                statement.executeUpdate();
            }
            PlayerRow row = getPlayerRow(connection, uuid);
            if (row != null) {
                return row;
            }
            return new PlayerRow(uuid, basePrice, 0.0, now, false);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load player row", exception);
        }
    }

    public void setBasePrice(double basePrice) {
        this.basePrice = basePrice;
    }

    public void setStartingBalance(double startingBalance) {
        this.startingBalance = startingBalance;
    }

    public int countPlayers() {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) as total FROM players"
             );
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt("total");
            }
            return 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to count players", exception);
        }
    }

    public boolean isFrozen(UUID uuid) {
        return getOrCreatePlayer(uuid).frozen();
    }

    public void setFrozen(UUID uuid, boolean frozen) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE players SET frozen = ? WHERE uuid = ?"
             )) {
            statement.setInt(1, frozen ? 1 : 0);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update frozen status", exception);
        }
    }

    public void updatePriceDirect(UUID uuid, double price) {
        getOrCreatePlayer(uuid);
        long now = System.currentTimeMillis();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE players SET price = ?, last_update = ? WHERE uuid = ?"
             )) {
            statement.setDouble(1, price);
            statement.setLong(2, now);
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update price", exception);
        }
        insertPriceHistory(uuid, price, now);
    }

    public void ensureWallet(UUID uuid) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO wallets (uuid, balance) VALUES (?, ?)"
             )) {
            statement.setString(1, uuid.toString());
            statement.setDouble(2, startingBalance);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to ensure wallet", exception);
        }
    }

    public double getBalance(UUID uuid) {
        ensureWallet(uuid);
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT balance FROM wallets WHERE uuid = ?"
             )) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getDouble("balance");
                }
                return startingBalance;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to read wallet balance", exception);
        }
    }

    public boolean withdrawBalance(UUID uuid, double amount) {
        if (amount <= 0) {
            return true;
        }
        ensureWallet(uuid);
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE wallets SET balance = balance - ? WHERE uuid = ? AND balance >= ?"
             )) {
            statement.setDouble(1, amount);
            statement.setString(2, uuid.toString());
            statement.setDouble(3, amount);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to withdraw from wallet", exception);
        }
    }

    public void depositBalance(UUID uuid, double amount) {
        if (amount <= 0) {
            return;
        }
        ensureWallet(uuid);
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE wallets SET balance = balance + ? WHERE uuid = ?"
             )) {
            statement.setDouble(1, amount);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to deposit to wallet", exception);
        }
    }

    public void addMoney(UUID uuid, double amount) {
        depositBalance(uuid, amount);
    }

    public void clearMarket() {
        long now = System.currentTimeMillis();
        try (Connection connection = getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM holdings")) {
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM price_history")) {
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE players SET price = ?, volume = 0.0, last_update = ?, frozen = 0"
            )) {
                statement.setDouble(1, basePrice);
                statement.setLong(2, now);
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to clear market", exception);
        }
    }

    public void updatePlayer(UUID uuid, double price, double volumeDelta, long timestamp) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE players SET price = ?, volume = volume + ?, last_update = ? WHERE uuid = ?"
             )) {
            statement.setDouble(1, price);
            statement.setDouble(2, volumeDelta);
            statement.setLong(3, timestamp);
            statement.setString(4, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update player row", exception);
        }
    }

    public int getHolding(UUID owner, UUID target) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT shares FROM holdings WHERE owner_uuid = ? AND target_uuid = ?"
             )) {
            statement.setString(1, owner.toString());
            statement.setString(2, target.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("shares");
                }
                return 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load holdings", exception);
        }
    }

    public void updateHolding(UUID owner, UUID target, int delta) {
        try (Connection connection = getConnection()) {
            int current = getHolding(owner, target);
            int next = current + delta;
            if (next <= 0) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM holdings WHERE owner_uuid = ? AND target_uuid = ?"
                )) {
                    statement.setString(1, owner.toString());
                    statement.setString(2, target.toString());
                    statement.executeUpdate();
                }
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO holdings (owner_uuid, target_uuid, shares) VALUES (?, ?, ?) " +
                            "ON CONFLICT(owner_uuid, target_uuid) DO UPDATE SET shares = excluded.shares"
            )) {
                statement.setString(1, owner.toString());
                statement.setString(2, target.toString());
                statement.setInt(3, next);
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update holdings", exception);
        }
    }

    public void insertPriceHistory(UUID target, double price, long timestamp) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO price_history (target_uuid, price, time) VALUES (?, ?, ?)"
             )) {
            statement.setString(1, target.toString());
            statement.setDouble(2, price);
            statement.setLong(3, timestamp);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to insert price history", exception);
        }
    }

    public List<Double> getRecentPrices(UUID target, int limit) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT price FROM price_history WHERE target_uuid = ? ORDER BY time DESC LIMIT ?"
             )) {
            statement.setString(1, target.toString());
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Double> prices = new ArrayList<>();
                while (resultSet.next()) {
                    prices.add(resultSet.getDouble("price"));
                }
                java.util.Collections.reverse(prices);
                return prices;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to read price history", exception);
        }
    }

    private void createSchema() {
        List<String> statements = List.of(
                "CREATE TABLE IF NOT EXISTS players (" +
                        "uuid TEXT PRIMARY KEY," +
                        "price REAL NOT NULL," +
                        "volume REAL NOT NULL," +
                        "last_update INTEGER NOT NULL," +
                        "frozen INTEGER NOT NULL DEFAULT 0" +
                        ")",
                "CREATE TABLE IF NOT EXISTS wallets (" +
                        "uuid TEXT PRIMARY KEY," +
                        "balance REAL NOT NULL" +
                        ")",
                "CREATE TABLE IF NOT EXISTS holdings (" +
                        "owner_uuid TEXT NOT NULL," +
                        "target_uuid TEXT NOT NULL," +
                        "shares INTEGER NOT NULL," +
                        "PRIMARY KEY (owner_uuid, target_uuid)" +
                        ")",
                "CREATE TABLE IF NOT EXISTS price_history (" +
                        "target_uuid TEXT NOT NULL," +
                        "price REAL NOT NULL," +
                        "time INTEGER NOT NULL" +
                        ")",
                "CREATE INDEX IF NOT EXISTS idx_price_history_target_time ON price_history(target_uuid, time)"
        );
        SqlUtil.executeBatch(this, statements);
        ensureFrozenColumn();
    }

    private void ensureFrozenColumn() {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "ALTER TABLE players ADD COLUMN frozen INTEGER NOT NULL DEFAULT 0"
             )) {
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private PlayerRow getPlayerRow(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT price, volume, last_update, frozen FROM players WHERE uuid = ?"
        )) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new PlayerRow(
                        uuid,
                        resultSet.getDouble("price"),
                        resultSet.getDouble("volume"),
                        resultSet.getLong("last_update"),
                        resultSet.getInt("frozen") == 1
                );
            }
        }
    }

    public record PlayerRow(UUID uuid, double price, double volume, long lastUpdate, boolean frozen) {
    }
}
