package com.playerexchange.lite.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ConfigManager {
    private final JavaPlugin plugin;
    private Settings settings;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        updateConfig("config.yml");
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        settings = new Settings(
                config.getDouble("base_price", 10.0),
                config.getDouble("tax_rate", 0.02),
                config.getInt("update_interval", 30),
                config.getBoolean("market_enabled", true),
                config.getDouble("wallet.starting_balance", 1000.0),
                config.getString("wallet.currency_locale", "en_US"),
                config.getString("wallet.currency_symbol", "$")
        );
    }

    private void updateConfig(String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        if (!configFile.exists()) {
            plugin.saveResource(fileName, false);
            return;
        }

        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
        double currentVersion = currentConfig.getDouble("config_version", 0.0);

        InputStream resourceStream = plugin.getResource(fileName);
        if (resourceStream == null) return;

        YamlConfiguration defaultConfiguration = YamlConfiguration.loadConfiguration(new InputStreamReader(resourceStream, StandardCharsets.UTF_8));
        double defaultVersion = defaultConfiguration.getDouble("config_version", 0.0);

        if (currentVersion < defaultVersion) {
            plugin.getLogger().info("Updating " + fileName + " from version " + currentVersion + " to " + defaultVersion);
            
            // Backup old config
            File backupFile = new File(plugin.getDataFolder(), fileName + ".bak");
            if (backupFile.exists()) backupFile.delete();
            configFile.renameTo(backupFile);

            // Save new default config
            plugin.saveResource(fileName, false);

            // Load the newly saved config to update its values
            YamlConfiguration updatedConfig = YamlConfiguration.loadConfiguration(configFile);
            
            // Copy old values to new config
            for (String key : currentConfig.getKeys(true)) {
                if (updatedConfig.contains(key) && !key.equals("config_version")) {
                    updatedConfig.set(key, currentConfig.get(key));
                }
            }

            try {
                updatedConfig.save(configFile);
                plugin.getLogger().info(fileName + " successfully updated while preserving your settings.");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save updated " + fileName + ": " + e.getMessage());
            }
        }
    }

    public Settings settings() {
        return settings;
    }

    public record Settings(
            double basePrice,
            double taxRate,
            int updateIntervalSeconds,
            boolean marketEnabled,
            double walletStartingBalance,
            String currencyLocale,
            String currencySymbol
    ) {
    }
}
