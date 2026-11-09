package com.robojacks.lib.hardware;

/**
 * A V5 motor's built-in encoder, like REVLib's RelativeEncoder. Get it from
 * {@link VexMotor#getEncoder()}. Readings use the motor's configured conversion factors,
 * so with {@code positionConversionFactor(kMetersPerMotorRotation)} position is in meters.
 */
public class VexEncoder {
    private final VexMotor motor;
    private double offset;

    VexEncoder(VexMotor motor) {
        this.motor = motor;
    }

    /** Position in converted units (motor rotations if no factor is set). */
    public double getPosition() {
        return motor.io().getPositionRotations() * motor.positionFactor() + offset;
    }

    /** Velocity in converted units (RPM if no factor is set). */
    public double getVelocity() {
        return motor.io().getVelocityRpm() * motor.velocityFactor();
    }

    double offset() {
        return offset;
    }

    /** Makes the encoder read {@code position} right now. */
    public void setPosition(double position) {
        motor.io().resetPosition();
        offset = position;
    }
}
