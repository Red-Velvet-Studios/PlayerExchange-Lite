package com.playerexchange.lite.commands;

import com.playerexchange.lite.api.PriceSnapshot;
import com.playerexchange.lite.api.TradeFailure;
import com.playerexchange.lite.api.TradeResult;
import com.playerexchange.lite.core.StockManager;
import com.playerexchange.lite.utils.ChatFormatter;
import com.playerexchange.lite.utils.ConfigManager;
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
import java.util.Objects;

/**
 * Handles chat-based trading commands for PX-Lite.
 */
public final class TradeCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final StockManager stockManager;
    private final ChatFormatter chatFormatter;
    private final MessageManager messageManager;
    private final ConfigManager configManager;

    public TradeCommand(JavaPlugin plugin, StockManager stockManager, ChatFormatter chatFormatter, MessageManager messageManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.stockManager = stockManager;
        this.chatFormatter = chatFormatter;
        this.messageManager = messageManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, messageManager.message("error.player_only"));
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";
        return switch (sub) {
            case "price" -> handlePrice(player, args);
            case "buy" -> handleBuy(player, args);
            case "sell" -> handleSell(player, args);
            case "balance" -> handleBalance(player);
            case "market", "gui" -> {
                if (!configManager.settings().marketEnabled()) {
                    send(player, messageManager.message("error.market_disabled"));
                    yield true;
                }
                send(player, chatFormatter.enterpriseMessage());
                yield true;
            }
            default -> {
                send(player, chatFormatter.helpCard());
                yield true;
            }
        };
    }

    private boolean handlePrice(Player player, String[] args) {
        if (args.length < 2) {
            send(player, messageManager.message("error.usage.price"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getName() == null) {
            send(player, messageManager.message("error.player_not_found"));
            return true;
        }
        if (target.isOnline() && target.getPlayer() != null) {
            stockManager.refreshPrice(target.getPlayer());
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PriceSnapshot snapshot = stockManager.getPriceSnapshot(target);
            Bukkit.getScheduler().runTask(plugin, () -> send(player, chatFormatter.priceCard(target, snapshot)));
        });
        return true;
    }

    private boolean handleBuy(Player player, String[] args) {
        if (args.length < 3) {
            send(player, messageManager.message("error.usage.buy"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getName() == null) {
            send(player, messageManager.message("error.player_not_found"));
            return true;
        }
        int shares = parseShares(args[2]);
        if (shares <= 0) {
            send(player, messageManager.message("error.shares_invalid"));
            return true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            TradeResult result = stockManager.buy(player, target, shares);
            Bukkit.getScheduler().runTask(plugin, () -> handleTradeResult(player, target, result, true));
        });
        return true;
    }

    private boolean handleSell(Player player, String[] args) {
        if (args.length < 3) {
            send(player, messageManager.message("error.usage.sell"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getName() == null) {
            send(player, messageManager.message("error.player_not_found"));
            return true;
        }
        int shares = parseShares(args[2]);
        if (shares <= 0) {
            send(player, messageManager.message("error.shares_invalid"));
            return true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            TradeResult result = stockManager.sell(player, target, shares);
            Bukkit.getScheduler().runTask(plugin, () -> handleTradeResult(player, target, result, false));
        });
        return true;
    }

    private boolean handleBalance(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            double balance = stockManager.getBalance(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> send(player, chatFormatter.balanceCard(player.getName(), balance)));
        });
        return true;
    }

    private void handleTradeResult(Player player, OfflinePlayer target, TradeResult result, boolean buy) {
        if (!result.success()) {
            send(player, messageManager.message(failureKey(result.failure())));
            return;
        }
        String action = buy ? chatFormatter.actionBuy() : chatFormatter.actionSell();
        send(player, chatFormatter.tradeConfirmation(action, Objects.requireNonNull(target.getName()), result.price(), result.total(), result.tax(), result.shares()));
    }

    private String failureKey(TradeFailure failure) {
        return switch (failure) {
            case INVALID_SHARES -> "error.shares_invalid";
            case INSUFFICIENT_FUNDS -> "error.insufficient_funds";
            case NOT_ENOUGH_SHARES -> "error.not_enough_shares";
            case FROZEN -> "error.trade_frozen";
        };
    }

    private void send(CommandSender sender, Component component) {
        if (sender instanceof Audience audience) {
            audience.sendMessage(component);
        } else {
            sender.sendMessage(component.toString());
        }
    }

    private int parseShares(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return List.of("price", "buy", "sell", "balance", "market", "gui").stream()
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && List.of("price", "buy", "sell").contains(args[0].toLowerCase(Locale.ROOT))) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        return List.of();
    }
}
