package com.playerexchange.lite.commands;

import com.playerexchange.lite.database.DatabaseManager;
import com.playerexchange.lite.utils.ChatFormatter;
import com.playerexchange.lite.utils.MessageManager;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PxAdminCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final MessageManager messageManager;
    private final ChatFormatter chatFormatter;
    private final Runnable reloadAction;

    public PxAdminCommand(
            JavaPlugin plugin,
            DatabaseManager databaseManager,
            MessageManager messageManager,
            ChatFormatter chatFormatter,
            Runnable reloadAction
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.messageManager = messageManager;
        this.chatFormatter = chatFormatter;
        this.reloadAction = reloadAction;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("px.admin")) {
            send(sender, messageManager.message("admin.no_permission"));
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        return switch (sub) {
            case "setprice" -> handleSetPrice(sender, args);
            case "addmoney" -> handleAddMoney(sender, args);
            case "freeze" -> handleFreeze(sender, args);
            case "clear" -> handleClear(sender);
            case "reload" -> handleReload(sender);
            default -> {
                send(sender, messageManager.message("admin.usage.main"));
                yield true;
            }
        };
    }

    private boolean handleSetPrice(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, messageManager.message("admin.usage.setprice"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getName() == null) {
            send(sender, messageManager.message("error.player_not_found"));
            return true;
        }
        double value;
        try {
            value = Double.parseDouble(args[2]);
        } catch (NumberFormatException ignored) {
            send(sender, messageManager.message("admin.error.invalid_number"));
            return true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            databaseManager.updatePriceDirect(target.getUniqueId(), value);
            Bukkit.getScheduler().runTask(plugin, () -> send(sender, messageManager.message("admin.setprice.success", Map.of(
                    "player", target.getName(),
                    "price", chatFormatter.formatCurrency(value)
            ))));
        });
        return true;
    }

    private boolean handleFreeze(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, messageManager.message("admin.usage.freeze"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getName() == null) {
            send(sender, messageManager.message("error.player_not_found"));
            return true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean frozen = databaseManager.isFrozen(target.getUniqueId());
            databaseManager.setFrozen(target.getUniqueId(), !frozen);
            Bukkit.getScheduler().runTask(plugin, () -> {
                String key = frozen ? "admin.unfreeze.success" : "admin.freeze.success";
                send(sender, messageManager.message(key, Map.of("player", target.getName())));
            });
        });
        return true;
    }

    private boolean handleAddMoney(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, messageManager.message("admin.usage.addmoney"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getName() == null) {
            send(sender, messageManager.message("error.player_not_found"));
            return true;
        }
        double value;
        try {
            value = Double.parseDouble(args[2]);
        } catch (NumberFormatException ignored) {
            send(sender, messageManager.message("admin.error.invalid_number"));
            return true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            databaseManager.addMoney(target.getUniqueId(), value);
            Bukkit.getScheduler().runTask(plugin, () -> send(sender, messageManager.message("admin.addmoney.success", Map.of(
                    "player", target.getName(),
                    "amount", chatFormatter.formatCurrency(value)
            ))));
        });
        return true;
    }

    private boolean handleClear(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            databaseManager.clearMarket();
            Bukkit.getScheduler().runTask(plugin, () -> send(sender, messageManager.message("admin.clear.success")));
        });
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        reloadAction.run();
        send(sender, messageManager.message("admin.reload.success"));
        return true;
    }

    private void send(CommandSender sender, Component component) {
        if (sender instanceof Audience audience) {
            audience.sendMessage(component);
        } else {
            sender.sendMessage(component.toString());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return List.of("setprice", "addmoney", "freeze", "clear", "reload").stream()
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && List.of("setprice", "freeze", "addmoney").contains(args[0].toLowerCase(Locale.ROOT))) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        return List.of();
    }
}
