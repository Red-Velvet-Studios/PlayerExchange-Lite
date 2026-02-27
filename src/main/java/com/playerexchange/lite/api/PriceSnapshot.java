package com.playerexchange.lite.api;

/**
 * Immutable view of a player's stock price at a point in time.
 */
public record PriceSnapshot(
        double price,
        double change,
        double changePercent,
        String miniChart,
        double volume
) {
}
