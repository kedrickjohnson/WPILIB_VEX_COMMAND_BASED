package com.robojacks.lib.math.filter;

import java.util.Arrays;

/**
 * Median of the last N values, like WPILib's MedianFilter. Unlike an average, a single wild
 * reading (a distance sensor glitch) doesn't move it.
 */
public class MedianFilter {
    private final double[] window;
    private int count;
    private int next;

    public MedianFilter(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        window = new double[size];
    }

    public double calculate(double input) {
        window[next] = input;
        next = (next + 1) % window.length;
        count = Math.min(count + 1, window.length);
        double[] sorted = Arrays.copyOf(window, count);
        if (count < window.length) {
            // window isn't full: use only the filled part
            sorted = new double[count];
            for (int i = 0; i < count; i++) {
                sorted[i] = window[(next - count + i + window.length) % window.length];
            }
        }
        Arrays.sort(sorted);
        return count % 2 == 1 ? sorted[count / 2] : (sorted[count / 2 - 1] + sorted[count / 2]) / 2.0;
    }

    public void reset() {
        count = 0;
        next = 0;
    }
}
