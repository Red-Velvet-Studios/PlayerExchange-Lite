package com.playerexchange.lite.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class MessageManager {
    private final JavaPlugin plugin;
    private final MiniMessage miniMessage;
    private YamlConfiguration messages;
    private String prefix;

    public MessageManager(JavaPlugin plugin, MiniMessage miniMessage) {
        this.plugin = plugin;
        this.miniMessage = miniMessage;
    }

    public void load() {
        updateMessages("messages.yml");
        File file = new File(plugin.getDataFolder(), "messages.yml");
        messages = YamlConfiguration.loadConfiguration(file);
        prefix = messages.getString("prefix", "");
    }

    private void updateMessages(String fileName) {
        File messageFile = new File(plugin.getDataFolder(), fileName);
        if (!messageFile.exists()) {
            plugin.saveResource(fileName, false);
            return;
        }

        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(messageFile);
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
            messageFile.renameTo(backupFile);

            // Save new default config
            plugin.saveResource(fileName, false);

            // Load the newly saved config to update its values
            YamlConfiguration updatedConfig = YamlConfiguration.loadConfiguration(messageFile);
            
            // Copy old values to new config
            for (String key : currentConfig.getKeys(true)) {
                if (updatedConfig.contains(key) && !key.equals("config_version")) {
                    updatedConfig.set(key, currentConfig.get(key));
                }
            }

            try {
                updatedConfig.save(messageFile);
                plugin.getLogger().info(fileName + " successfully updated while preserving your settings.");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save updated " + fileName + ": " + e.getMessage());
            }
        }
    }

    public Component message(String key) {
        return message(key, Map.of());
    }

    public Component message(String key, Map<String, String> placeholders) {
        String template = messages.getString(key, "");
        if (template.isEmpty()) {
            return Component.empty();
        }
        TagResolver.Builder builder = TagResolver.builder();
        if (prefix != null && !prefix.isEmpty()) {
            builder.resolver(Placeholder.parsed("prefix", prefix));
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            builder.resolver(Placeholder.parsed(entry.getKey(), entry.getValue()));
        }
        return miniMessage.deserialize(template, builder.build());
    }

    public String raw(String key) {
        return messages.getString(key, "");
    }

    public List<String> list(String key) {
        List<String> lines = messages.getStringList(key);
        if (!lines.isEmpty()) {
            return lines;
        }
        String single = messages.getString(key, "");
        if (single.contains("\n")) {
            return List.of(single.split("\n"));
        }
        return List.of(single);
    }
}
