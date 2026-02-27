package com.playerexchange.lite.api;

/**
 * Aggregated player statistics used by the volatility model.
 */
public record PlayerMetrics(
        int kills,
        int deaths,
        long playTimeTicks,
        int diamondsMined,
        int ancientDebrisMined
) {
    /**
     * Returns a neutral baseline suitable for offline players.
     */
    public static PlayerMetrics neutral() {
        return new PlayerMetrics(1, 1, 72_000L, 0, 0);
    }

    public double kdr() {
        return kills / Math.max(1.0, deaths);
    }

    public double hoursPlayed() {
        return playTimeTicks / 72_000.0;
    }

    public int highValueMined() {
        return diamondsMined + ancientDebrisMined;
    }
}
