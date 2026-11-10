package com.robojacks.lib.controller;

/**
 * Feedforward for an arm that gravity pulls on, like WPILib's ArmFeedforward:
 * {@code volts = kS*sign(v) + kG*cos(angle) + kV*v + kA*a}.
 *
 * <p>Angles are radians from horizontal (0 = arm sticking straight out, pi/2 = straight up), so
 * kG is the voltage that holds the arm level.
 *
 * <pre>{@code
 * ArmFeedforward m_ff = new ArmFeedforward(0.3, 0.9, 1.5); // kS, kG, kV
 * m_motor.setVoltage(m_ff.calculate(angleRad, 0.0)); // just hold position
 * }</pre>
 */
public class ArmFeedforward {
    public final double ks;
    public final double kg;
    public final double kv;
    public final double ka;

    public ArmFeedforward(double ks, double kg, double kv, double ka) {
        this.ks = ks;
        this.kg = kg;
        this.kv = kv;
        this.ka = ka;
    }

    public ArmFeedforward(double ks, double kg, double kv) {
        this(ks, kg, kv, 0.0);
    }

    /**
     * @param positionRadians arm angle from horizontal
     * @param velocity        rad/s
     * @param acceleration    rad/s^2
     */
    public double calculate(double positionRadians, double velocity, double acceleration) {
        return ks * Math.signum(velocity) + kg * Math.cos(positionRadians) + kv * velocity + ka * acceleration;
    }

    public double calculate(double positionRadians, double velocity) {
        return calculate(positionRadians, velocity, 0.0);
    }

    /** Fastest the arm can move at this angle with {@code maxVoltage} available (rad/s). */
    public double maxAchievableVelocity(double maxVoltage, double angleRadians, double acceleration) {
        return (maxVoltage - ks - Math.cos(angleRadians) * kg - acceleration * ka) / kv;
    }
}
