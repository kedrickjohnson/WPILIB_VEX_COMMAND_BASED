package com.robojacks.lib.math;

/** Same call as WPILib's: {@code VecBuilder.fill(0.1, 0.1, 999)} for x, y, heading standard deviations. */
public final class VecBuilder {
    private VecBuilder() {}

    public static Vector3 fill(double x, double y, double theta) {
        return new Vector3(x, y, theta);
    }
}
