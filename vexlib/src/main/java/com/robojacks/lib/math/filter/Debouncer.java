package com.robojacks.lib.math.filter;

import com.robojacks.lib.hal.HAL;

/**
 * Ignores changes in a boolean that don't last, like WPILib's Debouncer. Use it on a noisy
 * sensor (a bumper that bounces, a distance reading that flickers).
 *
 * <pre>{@code
 * private final Debouncer m_pieceDebouncer = new Debouncer(0.1, Debouncer.DebounceType.kRising);
 * boolean hasPiece = m_pieceDebouncer.calculate(m_distance.isWithin(0.05));
 * }</pre>
 */
public class Debouncer {
    public enum DebounceType {
        /** Only rising edges (false to true) must last. */
        kRising,
        /** Only falling edges must last. */
        kFalling,
        /** Both must last. */
        kBoth
    }

    private final double debounceTime;
    private final DebounceType type;
    private boolean baseline;
    private double prevTime;

    public Debouncer(double seconds, DebounceType type) {
        debounceTime = seconds;
        this.type = type;
        baseline = type == DebounceType.kFalling;
        prevTime = HAL.now();
    }

    /** Rising edges only. */
    public Debouncer(double seconds) {
        this(seconds, DebounceType.kRising);
    }

    public boolean calculate(boolean input) {
        double now = HAL.now();
        if (input == baseline) {
            prevTime = now;
        }
        if (now - prevTime >= debounceTime) {
            if (type == DebounceType.kBoth) {
                baseline = input;
                prevTime = now;
            }
            return input;
        }
        return baseline;
    }
}
