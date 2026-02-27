package com.playerexchange.lite.core;

import com.playerexchange.lite.api.PlayerMetrics;
import com.playerexchange.lite.api.PriceUpdate;

/**
 * Deterministic volatility model driven by player activity and trading volume.
 */
public final class VolatilityEngine {
    public PriceUpdate calculate(double currentPrice, PlayerMetrics metrics, double tradingVolume) {
        double kdrScore = normalize(metrics.kdr(), 0.5, 3.0);
        double timeScore = normalize(metrics.hoursPlayed(), 1.0, 120.0);
        double miningScore = normalize(metrics.highValueMined(), 0.0, 250.0);
        double volumeScore = normalize(tradingVolume, 0.0, 25_000.0);

        double momentum = (kdrScore * 0.4) + (timeScore * 0.2) + (miningScore * 0.2) + (volumeScore * 0.2);
        double drift = (momentum - 0.5) * 0.12;
        double delta = currentPrice * drift;
        double next = Math.max(1.0, currentPrice + delta);

        return new PriceUpdate(next, delta);
    }

    private double normalize(double value, double min, double max) {
        if (value <= min) {
            return 0.0;
        }
        if (value >= max) {
            return 1.0;
        }
        return (value - min) / (max - min);
    }
}
