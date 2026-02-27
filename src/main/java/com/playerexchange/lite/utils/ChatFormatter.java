package com.playerexchange.lite.utils;

import com.playerexchange.lite.api.PriceSnapshot;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ChatFormatter {
    private final MessageManager messageManager;
    private final NumberFormat moneyFormat;
    private final NumberFormat compactFormat;
    private final NumberFormat percentFormat;
    private final String currencySymbol;

    public ChatFormatter(MessageManager messageManager, String currencyLocale, String currencySymbol) {
        this.messageManager = messageManager;
        Locale locale = parseLocale(currencyLocale).orElse(Locale.US);
        this.moneyFormat = NumberFormat.getNumberInstance(locale);
        this.moneyFormat.setMinimumFractionDigits(2);
        this.moneyFormat.setMaximumFractionDigits(2);
        this.compactFormat = NumberFormat.getNumberInstance(locale);
        this.compactFormat.setMinimumFractionDigits(1);
        this.compactFormat.setMaximumFractionDigits(1);
        this.percentFormat = NumberFormat.getNumberInstance(Locale.US);
        this.percentFormat.setMinimumFractionDigits(2);
        this.percentFormat.setMaximumFractionDigits(2);
        this.currencySymbol = currencySymbol == null ? "" : currencySymbol;
    }

    public Component priceCard(OfflinePlayer target, PriceSnapshot snapshot) {
        String fallback = messageManager.raw("common.unknown");
        String name = target.getName() == null ? fallback : target.getName();
        String change = formatSignedCurrency(snapshot.change());
        String percent = formatSignedPercent(snapshot.changePercent());
        return messageManager.message("price.card", Map.of(
                "player", name,
                "price", formatCurrency(snapshot.price()),
                "change", change,
                "change_percent", percent,
                "chart", snapshot.miniChart(),
                "buy_cmd", "/px buy " + name + " 1",
                "sell_cmd", "/px sell " + name + " 1",
                "volume", formatCurrency(snapshot.volume())
        ));
    }

    public Component helpCard() {
        return messageManager.message("help.card");
    }

    public Component tradeConfirmation(String action, String target, double price, double total, double tax, int shares) {
        return messageManager.message("trade.success", Map.of(
                "action", action,
                "shares", Integer.toString(shares),
                "target", target,
                "price", formatCurrency(price),
                "total", formatCurrency(total),
                "tax", formatCurrency(tax)
        ));
    }

    public Component balanceCard(String playerName, double balance) {
        String fallback = messageManager.raw("common.unknown");
        String name = playerName == null ? fallback : playerName;
        return messageManager.message("balance.card", Map.of(
                "player", name,
                "balance", formatCurrency(balance)
        ));
    }

    public Component enterpriseMessage() {
        return messageManager.message("market.enterprise");
    }

    public String actionBuy() {
        return messageManager.raw("trade.action.buy");
    }

    public String actionSell() {
        return messageManager.raw("trade.action.sell");
    }

    public String formatCurrency(double value) {
        double abs = Math.abs(value);
        String symbol = currencySymbol.isBlank() ? "$" : currencySymbol;
        if (abs < 1000.0) {
            return symbol + moneyFormat.format(abs);
        }
        String suffix = "k";
        double scaled = abs / 1_000.0;
        if (abs >= 1_000_000_000.0) {
            suffix = "B";
            scaled = abs / 1_000_000_000.0;
        } else if (abs >= 1_000_000.0) {
            suffix = "M";
            scaled = abs / 1_000_000.0;
        }
        return symbol + compactFormat.format(scaled) + suffix;
    }

    private String formatSignedCurrency(double value) {
        String formatted = formatCurrency(Math.abs(value));
        if (value < 0) {
            return "<red>-" + formatted + "</red>";
        }
        return "<green>+" + formatted + "</green>";
    }

    private String formatSignedPercent(double value) {
        String formatted = percentFormat.format(Math.abs(value)) + "%";
        if (value < 0) {
            return "<red>-" + formatted + "</red>";
        }
        return "<green>+" + formatted + "</green>";
    }

    private Optional<Locale> parseLocale(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String[] parts = input.split("[_-]");
        if (parts.length == 1) {
            return Optional.of(Locale.of(parts[0]));
        }
        if (parts.length >= 2) {
            return Optional.of(Locale.of(parts[0], parts[1]));
        }
        return Optional.empty();
    }
}
