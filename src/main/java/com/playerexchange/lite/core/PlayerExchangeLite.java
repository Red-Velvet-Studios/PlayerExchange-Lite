package com.playerexchange.lite.core;

import com.playerexchange.lite.commands.PxAdminCommand;
import com.playerexchange.lite.commands.TradeCommand;
import com.playerexchange.lite.database.DatabaseManager;
import com.playerexchange.lite.listeners.PlayerListener;
import com.playerexchange.lite.utils.ChatFormatter;
import com.playerexchange.lite.utils.ConfigManager;
import com.playerexchange.lite.utils.MessageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

/**
 * Main plugin entrypoint for PlayerExchange-Lite.
 */
public final class PlayerExchangeLite extends JavaPlugin {
    private DatabaseManager databaseManager;
    private StockManager stockManager;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private BukkitTask updateTask;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.load();

        messageManager = new MessageManager(this, MiniMessage.miniMessage());
        messageManager.load();

        databaseManager = new DatabaseManager(this);
        databaseManager.setBasePrice(configManager.settings().basePrice());
        databaseManager.setStartingBalance(configManager.settings().walletStartingBalance());
        databaseManager.start();

        logBanner();
        logInfo("⚙ Loading Internal Wallet... OK");

        VolatilityEngine volatilityEngine = new VolatilityEngine();
        stockManager = new StockManager(
                this,
                databaseManager,
                volatilityEngine,
                configManager.settings().taxRate(),
                configManager.settings().updateIntervalSeconds() * 1000L
        );
        logInfo("⚙ Initializing Market Logic... OK");

        ChatFormatter formatter = new ChatFormatter(
                messageManager,
                configManager.settings().currencyLocale(),
                configManager.settings().currencySymbol()
        );
        PlayerListener listener = new PlayerListener(stockManager);
        getServer().getPluginManager().registerEvents(listener, this);

        TradeCommand command = new TradeCommand(this, stockManager, formatter, messageManager, configManager);
        Objects.requireNonNull(getCommand("px")).setExecutor(command);
        Objects.requireNonNull(getCommand("px")).setTabCompleter(command);

        PxAdminCommand adminCommand = new PxAdminCommand(this, databaseManager, messageManager, formatter, this::reloadAll);
        Objects.requireNonNull(getCommand("pxadmin")).setExecutor(adminCommand);
        Objects.requireNonNull(getCommand("pxadmin")).setTabCompleter(adminCommand);

        schedulePriceUpdates();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            int count = databaseManager.countPlayers();
            logInfo("Loaded " + count + " stock profiles from database.");
        });
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        if (updateTask != null) {
            updateTask.cancel();
        }
    }

    public void reloadAll() {
        configManager.load();
        messageManager.load();
        databaseManager.setBasePrice(configManager.settings().basePrice());
        databaseManager.setStartingBalance(configManager.settings().walletStartingBalance());
        stockManager.updateSettings(
                configManager.settings().taxRate(),
                configManager.settings().updateIntervalSeconds() * 1000L
        );
        schedulePriceUpdates();
    }

    private void schedulePriceUpdates() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        long intervalTicks = Math.max(20L, configManager.settings().updateIntervalSeconds() * 20L);
        updateTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (var player : getServer().getOnlinePlayers()) {
                stockManager.refreshPrice(player);
            }
        }, intervalTicks, intervalTicks);
    }

    private void logBanner() {
        logInfo(" ____ ___  _      _     _ _____ _____ ");
        logInfo("/  __\\  \\//     / \\   / Y__ __Y  __/ ");
        logInfo("|  \\/| \\  /_____ | |   | | / \\ |  \\  ");
        logInfo("|  __/ /  \\\\____\\| |_/\\| | | | |  /_ ");
        logInfo("\\_/   /__/\\\\     \\____/\\_/ \\_/ \\____\\ ");
    }

    private void logInfo(String message) {
        getLogger().info("[PX] " + message);
    }
}
