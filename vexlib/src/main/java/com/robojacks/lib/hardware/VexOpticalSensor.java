package com.robojacks.lib.hardware;

import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.hal.OpticalSensorIO;

/**
 * V5 Optical Sensor: color (hue), brightness and proximity at close range (a few cm). Used to
 * sort game pieces by alliance color or to detect that a piece has arrived.
 *
 * <pre>{@code
 * VexOpticalSensor m_color = new VexOpticalSensor(kColorSensorPort);
 * m_color.setLedPower(1.0); // light the object so color reads the same anywhere on the field
 *
 * Trigger redPiece = new Trigger(() -> m_color.isColor(VexOpticalSensor.kRedHue, 25));
 * }</pre>
 */
public class VexOpticalSensor {
    /** Typical hue of a red game piece, degrees. Check yours with {@link #getHue()}. */
    public static final double kRedHue = 10.0;
    /** Typical hue of a blue game piece, degrees. */
    public static final double kBlueHue = 220.0;
    /** Proximity above this means something is right in front of the sensor. */
    public static final int kDefaultProximityThreshold = 120;

    private final OpticalSensorIO io;

    /** @param port smart port 1-21 */
    public VexOpticalSensor(int port) {
        io = HAL.get().opticalSensor(port);
    }

    /** Degrees, 0 to 360. */
    public double getHue() {
        return io.getHue();
    }

    /** 0 to 1. Low saturation means grey/white: the hue isn't meaningful. */
    public double getSaturation() {
        return io.getSaturation();
    }

    /** 0 to 1. */
    public double getBrightness() {
        return io.getBrightness();
    }

    /** 0 (nothing near) to 255 (touching). */
    public int getProximity() {
        return io.getProximity();
    }

    /** True when something is right in front of the sensor. */
    public boolean isObjectNear() {
        return io.getProximity() >= kDefaultProximityThreshold;
    }

    /**
     * True when an object is near and its hue is within {@code toleranceDegrees} of
     * {@code hueDegrees} (wrapping around 0/360, so red at 355 matches 10).
     */
    public boolean isColor(double hueDegrees, double toleranceDegrees) {
        if (!isObjectNear() || io.getSaturation() < 0.2) {
            return false;
        }
        double diff = Math.abs(io.getHue() - hueDegrees) % 360.0;
        return Math.min(diff, 360.0 - diff) <= toleranceDegrees;
    }

    /** White LED, 0 to 1. Turn it on for consistent color readings. */
    public void setLedPower(double power) {
        io.setLedPower(power);
    }

    public boolean isConnected() {
        return io.isConnected();
    }
}
