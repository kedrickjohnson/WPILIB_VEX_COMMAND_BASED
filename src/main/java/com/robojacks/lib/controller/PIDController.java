package com.robojacks.lib.controller;

import com.robojacks.lib.math.MathUtil;

/** Minimal PID controller with the same API shape as WPILib's. */
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

    /** Integral only accumulates while |error| < iZone. */
    public void setIZone(double iZone) {
        this.iZone = iZone;
    }

    public double getError() {
        return error;
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
