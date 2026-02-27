package com.playerexchange.lite.listeners;

import com.playerexchange.lite.core.StockManager;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;

/**
 * Optimized event handler for live price updates.
 */
public final class PlayerListener implements Listener {
    private final StockManager stockManager;

    public PlayerListener(StockManager stockManager) {
        this.stockManager = stockManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        stockManager.ensurePlayer(player.getUniqueId());
        stockManager.refreshPrice(player);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        stockManager.refreshPrice(event.getEntity());
    }

    @EventHandler
    public void onStatistic(PlayerStatisticIncrementEvent event) {
        Statistic statistic = event.getStatistic();
        if (statistic == Statistic.PLAYER_KILLS || statistic == Statistic.DEATHS) {
            stockManager.refreshPrice(event.getPlayer());
            return;
        }
        if (statistic == Statistic.MINE_BLOCK) {
            Material material = event.getMaterial();
            if (material == Material.DIAMOND_ORE) {
                stockManager.refreshPrice(event.getPlayer());
                return;
            }
            String name = material.name();
            if (name.equals("DEEPSLATE_DIAMOND_ORE") || name.equals("ANCIENT_DEBRIS")) {
                stockManager.refreshPrice(event.getPlayer());
            }
        }
    }
}
