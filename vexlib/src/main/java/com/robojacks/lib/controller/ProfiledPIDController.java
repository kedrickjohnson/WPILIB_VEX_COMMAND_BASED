package com.robojacks.lib.controller;

import com.robojacks.lib.trajectory.TrapezoidProfile;

/**
 * A PID controller that follows a {@link TrapezoidProfile} to the goal instead of jumping
 * straight to it, like WPILib's ProfiledPIDController. Good for arms and lifts: the mechanism
 * speeds up and slows down smoothly.
 *
 * <pre>{@code
 * private final ProfiledPIDController m_controller = new ProfiledPIDController(
 *         8.0, 0.0, 0.2, new TrapezoidProfile.Constraints(Math.PI, 2 * Math.PI)); // rad/s, rad/s^2
 * private final ArmFeedforward m_feedforward = new ArmFeedforward(0.3, 0.6, 1.2);
 *
 * // every loop, in a command:
 * double volts = m_controller.calculate(getAngleRadians(), goalRadians)
 *         + m_feedforward.calculate(getAngleRadians(), m_controller.getSetpoint().velocity);
 * }</pre>
 */
public class ProfiledPIDController {
    private final PIDController pid;
    private final TrapezoidProfile profile;
    private TrapezoidProfile.Constraints constraints;
    private TrapezoidProfile.State goal = new TrapezoidProfile.State();
    private TrapezoidProfile.State setpoint = new TrapezoidProfile.State();
    private boolean continuous;
    private double minInput;
    private double maxInput;

    public ProfiledPIDController(double kP, double kI, double kD, TrapezoidProfile.Constraints constraints, double period) {
        pid = new PIDController(kP, kI, kD, period);
        this.constraints = constraints;
        profile = new TrapezoidProfile(constraints);
    }

    public ProfiledPIDController(double kP, double kI, double kD, TrapezoidProfile.Constraints constraints) {
        this(kP, kI, kD, constraints, 0.010);
    }

    public void setGoal(TrapezoidProfile.State goal) {
        this.goal = goal;
    }

    /** Goal position, stopped. */
    public void setGoal(double position) {
        goal = new TrapezoidProfile.State(position, 0.0);
    }

    public TrapezoidProfile.State getGoal() {
        return goal;
    }

    /** Where the profile says the mechanism should be right now. Use its velocity for feedforward. */
    public TrapezoidProfile.State getSetpoint() {
        return setpoint;
    }

    public TrapezoidProfile.Constraints getConstraints() {
        return constraints;
    }

    public void setTolerance(double positionTolerance) {
        pid.setTolerance(positionTolerance);
    }

    public void setTolerance(double positionTolerance, double velocityTolerance) {
        pid.setTolerance(positionTolerance, velocityTolerance);
    }

    /** For angles: treat min and max as the same point (e.g. -pi and pi). */
    public void enableContinuousInput(double min, double max) {
        continuous = true;
        minInput = min;
        maxInput = max;
        pid.enableContinuousInput(min, max);
    }

    /** True when the profile has reached the goal and the mechanism is at it. */
    public boolean atGoal() {
        return atSetpoint() && goal.equals(setpoint);
    }

    public boolean atSetpoint() {
        return pid.atSetpoint();
    }

    public double getPositionError() {
        return pid.getPositionError();
    }

    public double calculate(double measurement, double goalPosition) {
        setGoal(goalPosition);
        return calculate(measurement);
    }

    public double calculate(double measurement, TrapezoidProfile.State goal) {
        setGoal(goal);
        return calculate(measurement);
    }

    /** Advances the profile one loop and returns the PID output toward the new setpoint. */
    public double calculate(double measurement) {
        if (continuous) {
            // Take the short way around: move the goal and setpoint near the measurement.
            double range = maxInput - minInput;
            double half = range / 2.0;
            double goalErr = wrap(goal.position - measurement, -half, half);
            double setpointErr = wrap(setpoint.position - measurement, -half, half);
            goal = new TrapezoidProfile.State(goalErr + measurement, goal.velocity);
            setpoint = new TrapezoidProfile.State(setpointErr + measurement, setpoint.velocity);
        }
        setpoint = profile.calculate(pid.getPeriod(), setpoint, goal);
        return pid.calculate(measurement, setpoint.position);
    }

    private static double wrap(double v, double min, double max) {
        double range = max - min;
        double r = (v - min) % range;
        return (r < 0 ? r + range : r) + min;
    }

    /**
     * Restarts the profile from where the mechanism is now. Call it when a command starts, or the
     * profile will begin from wherever it last ended.
     */
    public void reset(double measuredPosition, double measuredVelocity) {
        pid.reset();
        setpoint = new TrapezoidProfile.State(measuredPosition, measuredVelocity);
    }

    public void reset(double measuredPosition) {
        reset(measuredPosition, 0.0);
    }

    public PIDController getPIDController() {
        return pid;
    }
}
