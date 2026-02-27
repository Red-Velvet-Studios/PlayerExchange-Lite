package com.playerexchange.lite.utils;

import java.util.List;

/**
 * Builds a compact price trend visualization for chat.
 */
public final class PriceChart {
    private PriceChart() {
    }

    public static String render(List<Double> history, double current) {
        List<Double> values = history.isEmpty() ? List.of(current) : history;
        int start = Math.max(0, values.size() - 10);
        List<Double> slice = values.subList(start, values.size());
        double min = slice.stream().min(Double::compareTo).orElse(current);
        double max = slice.stream().max(Double::compareTo).orElse(current);
        double first = slice.get(0);
        double last = slice.get(slice.size() - 1);
        char[] bars = new char[]{'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};
        StringBuilder builder = new StringBuilder();
        for (double value : slice) {
            int level;
            if (max == min) {
                level = 4;
            } else {
                double ratio = (value - min) / (max - min);
                level = Math.min(7, Math.max(0, (int) Math.round(ratio * 7)));
            }
            builder.append(bars[level]);
        }
        while (builder.length() < 10) {
            builder.append('▁');
        }
        String color = last > first ? "green" : (last < first ? "red" : "yellow");
        return "<" + color + ">" + builder + "</" + color + ">";
    }
}
