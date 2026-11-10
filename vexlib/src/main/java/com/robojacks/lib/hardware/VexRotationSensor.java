package com.robojacks.lib.hardware;

import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.hal.RotationSensorIO;

/**
 * V5 Rotation Sensor: an absolute encoder, like a REV Through Bore or CANcoder. Use it on an
 * arm joint (absolute angle survives power cycles) or on an unpowered tracking wheel for
 * odometry.
 *
 * <p>Positions are in rotations until you set a conversion factor, then in your units:
 *
 * <pre>{@code
 * VexRotationSensor m_trackingWheel = new VexRotationSensor(kTrackingWheelPort);
 * m_trackingWheel.setPositionConversionFactor(Math.PI * Units.inchesToMeters(2.0)); // meters
 * double traveled = m_trackingWheel.getPosition();
 * }</pre>
 */
public class VexRotationSensor {
    private final RotationSensorIO io;
    private double positionFactor = 1.0;
    private double velocityFactor = 1.0;

    /** @param port smart port 1-21 */
    public VexRotationSensor(int port) {
        io = HAL.get().rotationSensor(port);
    }

    /** @param reversed count the other way (use when the sensor is mounted facing backwards) */
    public VexRotationSensor(int port, boolean reversed) {
        this(port);
        setInverted(reversed);
    }

    public void setInverted(boolean inverted) {
        io.setReversed(inverted);
    }

    /** Units per rotation for {@link #getPosition()} and {@link #setPosition}: 360 for degrees, wheel circumference for distance. */
    public void setPositionConversionFactor(double unitsPerRotation) {
        positionFactor = unitsPerRotation;
    }

    /** Units per (rotation per second) for {@link #getVelocity()}. Usually the same as the position factor. */
    public void setVelocityConversionFactor(double factor) {
        velocityFactor = factor;
    }

    /** Continuous position (keeps counting past one turn), in your units. */
    public double getPosition() {
        return io.getPositionRotations() * positionFactor;
    }

    /** Velocity in your units per second. */
    public double getVelocity() {
        return io.getVelocityRps() * velocityFactor;
    }

    public void setPosition(double position) {
        io.setPositionRotations(position / positionFactor);
    }

    public void reset() {
        io.setPositionRotations(0.0);
    }

    /**
     * Absolute angle within one turn, 0 to 1 rotation, regardless of conversion factor. It's kept
     * when the robot powers off, which is what makes the sensor useful on an arm: no homing needed.
     */
    public double getAbsolutePosition() {
        return io.getAbsoluteRotations();
    }

    public boolean isConnected() {
        return io.isConnected();
    }
}
