package com.playerexchange.lite.api;

/**
 * Outcome of a trade execution.
 */
public record TradeResult(boolean success, TradeFailure failure, double price, double total, double tax, int shares) {
    public static TradeResult success(double price, double total, double tax, int shares) {
        return new TradeResult(true, null, price, total, tax, shares);
    }

    public static TradeResult failure(TradeFailure failure) {
        return new TradeResult(false, failure, 0.0, 0.0, 0.0, 0);
    }
}
