package com.robojacks.lib.math.filter;

import com.robojacks.lib.hal.HAL;

/**
 * Limits how fast a value can change, like WPILib's SlewRateLimiter. Smooths joystick input so
 * the robot doesn't tip or slip when the driver slams the stick.
 *
 * <pre>{@code
 * // Full speed (1.0) reached in no less than 0.25 s
 * private final SlewRateLimiter m_forwardLimiter = new SlewRateLimiter(4.0);
 * double forward = m_forwardLimiter.calculate(m_driverController.getLeftY());
 * }</pre>
 */
public class SlewRateLimiter {
    private final double positiveRateLimit;
    private final double negativeRateLimit;
    private double prevVal;
    private double prevTime;

    /**
     * @param positiveRateLimit fastest increase, units per second
     * @param negativeRateLimit fastest decrease, units per second (a negative number)
     * @param initialValue      starting value
     */
    public SlewRateLimiter(double positiveRateLimit, double negativeRateLimit, double initialValue) {
        this.positiveRateLimit = positiveRateLimit;
        this.negativeRateLimit = negativeRateLimit;
        prevVal = initialValue;
        prevTime = HAL.now();
    }

    /** Same limit both ways, units per second. */
    public SlewRateLimiter(double rateLimit) {
        this(rateLimit, -rateLimit, 0.0);
    }

    public double calculate(double input) {
        double now = HAL.now();
        double elapsed = now - prevTime;
        double change = input - prevVal;
        change = Math.max(negativeRateLimit * elapsed, Math.min(positiveRateLimit * elapsed, change));
        prevVal += change;
        prevTime = now;
        return prevVal;
    }

    /** The last output. */
    public double lastValue() {
        return prevVal;
    }

    /** Jumps straight to {@code value} (e.g. when a command starts). */
    public void reset(double value) {
        prevVal = value;
        prevTime = HAL.now();
    }
}
