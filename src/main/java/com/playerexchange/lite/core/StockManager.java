package com.playerexchange.lite.core;

import com.playerexchange.lite.api.PlayerMetrics;
import com.playerexchange.lite.api.PriceSnapshot;
import com.playerexchange.lite.api.PriceUpdate;
import com.playerexchange.lite.api.TradeFailure;
import com.playerexchange.lite.api.TradeResult;
import com.playerexchange.lite.database.DatabaseManager;
import com.playerexchange.lite.utils.PriceChart;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core trading and pricing logic for PX-Lite.
 */
public final class StockManager {
    private final PlayerExchangeLite plugin;
    private final DatabaseManager databaseManager;
    private final VolatilityEngine volatilityEngine;
    private final ConcurrentHashMap<UUID, Long> lastRefresh = new ConcurrentHashMap<>();
    private volatile long refreshCooldownMs;
    private volatile double taxRate;

    public StockManager(PlayerExchangeLite plugin, DatabaseManager databaseManager, VolatilityEngine volatilityEngine, double taxRate, long refreshCooldownMs) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.volatilityEngine = volatilityEngine;
        this.taxRate = taxRate;
        this.refreshCooldownMs = refreshCooldownMs;
    }

    public void ensurePlayer(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            databaseManager.getOrCreatePlayer(uuid);
            databaseManager.ensureWallet(uuid);
        });
    }

    public void refreshPrice(Player player) {
        long now = System.currentTimeMillis();
        long last = lastRefresh.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < refreshCooldownMs) {
            return;
        }
        lastRefresh.put(player.getUniqueId(), now);
        PlayerMetrics metrics = readMetrics(player);
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseManager.PlayerRow row = databaseManager.getOrCreatePlayer(uuid);
            if (row.frozen()) {
                return;
            }
            PriceUpdate update = volatilityEngine.calculate(row.price(), metrics, row.volume());
            databaseManager.updatePlayer(uuid, update.newPrice(), 0.0, now);
            databaseManager.insertPriceHistory(uuid, update.newPrice(), now);
        });
    }

    public PriceSnapshot getPriceSnapshot(OfflinePlayer target) {
        DatabaseManager.PlayerRow row = databaseManager.getOrCreatePlayer(target.getUniqueId());
        List<Double> history = databaseManager.getRecentPrices(target.getUniqueId(), 12);
        double current = row.price();
        double previous = history.size() >= 2 ? history.get(history.size() - 2) : current;
        double change = current - previous;
        double percent = previous == 0 ? 0.0 : (change / previous) * 100.0;
        String chart = PriceChart.render(history, current);
        return new PriceSnapshot(current, change, percent, chart, row.volume());
    }

    public TradeResult buy(Player buyer, OfflinePlayer target, int shares) {
        if (shares <= 0) {
            return TradeResult.failure(TradeFailure.INVALID_SHARES);
        }
        DatabaseManager.PlayerRow row = databaseManager.getOrCreatePlayer(target.getUniqueId());
        if (row.frozen()) {
            return TradeResult.failure(TradeFailure.FROZEN);
        }
        double baseValue = row.price() * shares;
        double tax = baseValue * taxRate;
        double totalCost = baseValue + tax;
        if (!databaseManager.withdrawBalance(buyer.getUniqueId(), totalCost)) {
            return TradeResult.failure(TradeFailure.INSUFFICIENT_FUNDS);
        }
        applyTrade(target, buyer.getUniqueId(), shares, baseValue, row);
        return TradeResult.success(row.price(), totalCost, tax, shares);
    }

    public TradeResult sell(Player seller, OfflinePlayer target, int shares) {
        if (shares <= 0) {
            return TradeResult.failure(TradeFailure.INVALID_SHARES);
        }
        int owned = databaseManager.getHolding(seller.getUniqueId(), target.getUniqueId());
        if (owned < shares) {
            return TradeResult.failure(TradeFailure.NOT_ENOUGH_SHARES);
        }
        DatabaseManager.PlayerRow row = databaseManager.getOrCreatePlayer(target.getUniqueId());
        if (row.frozen()) {
            return TradeResult.failure(TradeFailure.FROZEN);
        }
        double baseValue = row.price() * shares;
        double tax = baseValue * taxRate;
        double payout = Math.max(0.0, baseValue - tax);
        databaseManager.depositBalance(seller.getUniqueId(), payout);
        applyTrade(target, seller.getUniqueId(), -shares, baseValue, row);
        return TradeResult.success(row.price(), payout, tax, shares);
    }

    public double getBalance(UUID uuid) {
        return databaseManager.getBalance(uuid);
    }

    public void updateSettings(double taxRate, long refreshCooldownMs) {
        this.taxRate = taxRate;
        this.refreshCooldownMs = refreshCooldownMs;
    }

    private void applyTrade(OfflinePlayer target, UUID owner, int shareDelta, double tradeValue, DatabaseManager.PlayerRow row) {
        UUID targetId = target.getUniqueId();
        databaseManager.updateHolding(owner, targetId, shareDelta);
        long now = System.currentTimeMillis();
        double volume = row.volume() + tradeValue;
        PlayerMetrics metrics = target.isOnline() && target.getPlayer() != null
                ? readMetrics(target.getPlayer())
                : PlayerMetrics.neutral();
        PriceUpdate update = volatilityEngine.calculate(row.price(), metrics, volume);
        databaseManager.updatePlayer(targetId, update.newPrice(), tradeValue, now);
        databaseManager.insertPriceHistory(targetId, update.newPrice(), now);
    }

    private PlayerMetrics readMetrics(Player player) {
        int kills = player.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = player.getStatistic(Statistic.DEATHS);
        long playTime = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        int diamonds = player.getStatistic(Statistic.MINE_BLOCK, Material.DIAMOND_ORE);
        Material deepslate = Material.getMaterial("DEEPSLATE_DIAMOND_ORE");
        if (deepslate != null) {
            diamonds += player.getStatistic(Statistic.MINE_BLOCK, deepslate);
        }
        int debrisCount = 0;
        Material debris = Material.getMaterial("ANCIENT_DEBRIS");
        if (debris != null) {
            debrisCount = player.getStatistic(Statistic.MINE_BLOCK, debris);
        }
        return new PlayerMetrics(kills, deaths, playTime, diamonds, debrisCount);
    }
}
