package com.playerexchange.lite.api;

/**
 * Output of the volatility model when recalculating price.
 */
public record PriceUpdate(double newPrice, double delta) {
}
