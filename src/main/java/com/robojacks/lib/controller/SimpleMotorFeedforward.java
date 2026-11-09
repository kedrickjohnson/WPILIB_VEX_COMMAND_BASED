package com.robojacks.lib.controller;

/** volts = kS * sign(v) + kV * v + kA * a  (same as WPILib). */
public class SimpleMotorFeedforward {
    private final double kS;
    private final double kV;
    private final double kA;

    public SimpleMotorFeedforward(double kS, double kV, double kA) {
        this.kS = kS;
        this.kV = kV;
        this.kA = kA;
    }

    public SimpleMotorFeedforward(double kS, double kV) {
        this(kS, kV, 0.0);
    }

    public double calculate(double velocity, double acceleration) {
        return kS * Math.signum(velocity) + kV * velocity + kA * acceleration;
    }

    public double calculate(double velocity) {
        return calculate(velocity, 0.0);
    }
}
