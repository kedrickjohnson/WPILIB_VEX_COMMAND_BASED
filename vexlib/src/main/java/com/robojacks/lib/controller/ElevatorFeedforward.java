package com.robojacks.lib.controller;

/**
 * Feedforward for a lift that gravity pulls straight down, like WPILib's ElevatorFeedforward:
 * {@code volts = kS*sign(v) + kG + kV*v + kA*a}. kG is the voltage that holds the lift still.
 */
public class ElevatorFeedforward {
    public final double ks;
    public final double kg;
    public final double kv;
    public final double ka;

    public ElevatorFeedforward(double ks, double kg, double kv, double ka) {
        this.ks = ks;
        this.kg = kg;
        this.kv = kv;
        this.ka = ka;
    }

    public ElevatorFeedforward(double ks, double kg, double kv) {
        this(ks, kg, kv, 0.0);
    }

    public double calculate(double velocity, double acceleration) {
        return ks * Math.signum(velocity) + kg + kv * velocity + ka * acceleration;
    }

    public double calculate(double velocity) {
        return calculate(velocity, 0.0);
    }
}
