package com.robojacks.lib.math;

/** Small math helpers, same names as WPILib's MathUtil. */
public final class MathUtil {
    private MathUtil() {}

    public static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(value, high));
    }

    /** Zeroes values inside the deadband and rescales the rest back to [-1, 1]. */
    public static double applyDeadband(double value, double deadband) {
        if (Math.abs(value) < deadband) {
            return 0.0;
        }
        double sign = value > 0 ? 1.0 : -1.0;
        return sign * (Math.abs(value) - deadband) / (1.0 - deadband);
    }

    /** Wraps a value into [min, max). */
    public static double inputModulus(double input, double min, double max) {
        double range = max - min;
        double n = Math.floor((input - min) / range);
        return input - n * range;
    }

    /** Wraps an angle into [-pi, pi). */
    public static double angleModulus(double radians) {
        return inputModulus(radians, -Math.PI, Math.PI);
    }

    public static boolean isNear(double expected, double actual, double tolerance) {
        return Math.abs(expected - actual) < tolerance;
    }
}
