package com.robojacks.lib.controller;

import com.robojacks.lib.math.MathUtil;

/**
 * PID controller with the same API as WPILib's: {@code output = kP*error + kI*integral + kD*derivative}.
 *
 * <pre>{@code
 * PIDController m_turnPid = new PIDController(0.8, 0.0, 0.05);
 * m_turnPid.enableContinuousInput(-Math.PI, Math.PI); // angles wrap
 * m_turnPid.setTolerance(Units.degreesToRadians(2));
 * double volts = m_turnPid.calculate(heading.getRadians(), target.getRadians());
 * }</pre>
 *
 * <p>Call {@code calculate} once per loop: the derivative and integral assume the loop period
 * given to the constructor (10 ms by default, matching {@code VexRobot}).
 */
public class PIDController {
    private double kP;
    private double kI;
    private double kD;
    private final double period;

    private double setpoint;
    private double error;
    private double prevError;
    private double totalError;
    private double errorDerivative;

    private boolean continuous;
    private double minInput;
    private double maxInput;

    private double errorTolerance = 0.05;
    private double errorDerivativeTolerance = Double.POSITIVE_INFINITY;
    private double iZone = Double.POSITIVE_INFINITY;
    private boolean haveMeasurement;

    /** @param period loop period in seconds (match VexRobot's period, default 0.010) */
    public PIDController(double kP, double kI, double kD, double period) {
        this.kP = kP;
        this.kI = kI;
        this.kD = kD;
        this.period = period;
    }

    public PIDController(double kP, double kI, double kD) {
        this(kP, kI, kD, 0.010);
    }

    public void setPID(double kP, double kI, double kD) {
        this.kP = kP;
        this.kI = kI;
        this.kD = kD;
    }

    public void setSetpoint(double setpoint) {
        this.setpoint = setpoint;
    }

    public double getSetpoint() {
        return setpoint;
    }

    /** Treat input as circular, e.g. (-pi, pi) for headings. */
    public void enableContinuousInput(double min, double max) {
        continuous = true;
        minInput = min;
        maxInput = max;
    }

    public void setTolerance(double errorTolerance, double derivativeTolerance) {
        this.errorTolerance = errorTolerance;
        this.errorDerivativeTolerance = derivativeTolerance;
    }

    public void setTolerance(double errorTolerance) {
        setTolerance(errorTolerance, Double.POSITIVE_INFINITY);
    }

    /** Integral only accumulates while |error| is below iZone. */
    public void setIZone(double iZone) {
        this.iZone = iZone;
    }

    public double getError() {
        return error;
    }

    /** WPILib's name for {@link #getError()}. */
    public double getPositionError() {
        return error;
    }

    /** Rate of change of the error, units per second. */
    public double getVelocityError() {
        return errorDerivative;
    }

    public double getP() {
        return kP;
    }

    public double getI() {
        return kI;
    }

    public double getD() {
        return kD;
    }

    public void setP(double p) {
        kP = p;
    }

    public void setI(double i) {
        kI = i;
    }

    public void setD(double d) {
        kD = d;
    }

    public double getPeriod() {
        return period;
    }

    public void disableContinuousInput() {
        continuous = false;
    }

    public boolean isContinuousInputEnabled() {
        return continuous;
    }

    public boolean atSetpoint() {
        return haveMeasurement
                && Math.abs(error) < errorTolerance
                && Math.abs(errorDerivative) < errorDerivativeTolerance;
    }

    public double calculate(double measurement, double setpoint) {
        this.setpoint = setpoint;
        return calculate(measurement);
    }

    public double calculate(double measurement) {
        prevError = error;
        if (continuous) {
            double half = (maxInput - minInput) / 2.0;
            error = MathUtil.inputModulus(setpoint - measurement, -half, half);
        } else {
            error = setpoint - measurement;
        }

        // First call: no derivative kick.
        errorDerivative = haveMeasurement ? (error - prevError) / period : 0.0;
        haveMeasurement = true;

        if (Math.abs(error) > iZone) {
            totalError = 0.0;
        } else if (kI != 0.0) {
            totalError = MathUtil.clamp(totalError + error * period, -1.0 / kI, 1.0 / kI);
        }

        return kP * error + kI * totalError + kD * errorDerivative;
    }

    public void reset() {
        error = 0.0;
        prevError = 0.0;
        totalError = 0.0;
        errorDerivative = 0.0;
        haveMeasurement = false;
    }
}
