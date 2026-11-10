package com.robojacks.lib.hardware;

import com.robojacks.lib.hal.AdiPortIO;

/**
 * The 3-wire Optical Shaft Encoder (360 counts per revolution), like WPILib's Encoder. Plug the
 * top wire into an odd port (A, C, E, G) and the bottom wire into the next one.
 *
 * <pre>{@code
 * AdiEncoder m_trackingWheel = new AdiEncoder('A', 'B', false);
 * m_trackingWheel.setDistancePerPulse(Math.PI * Units.inchesToMeters(2.75) / 360.0);
 * }</pre>
 *
 * <p>The V5 Rotation Sensor ({@link VexRotationSensor}) is more accurate and doesn't need two
 * ports; prefer it for new robots.
 */
public class AdiEncoder {
    /** Counts per revolution of the VEX Optical Shaft Encoder. */
    public static final int kCountsPerRevolution = 360;

    private final AdiPortIO top;
    private final boolean reversed;
    private double distancePerPulse = 1.0;
    private double lastDistance;
    private double lastTime = Double.NaN;
    private double rate;

    public AdiEncoder(char topPort, char bottomPort, boolean reversed) {
        this(0, topPort, bottomPort, reversed);
    }

    public AdiEncoder(int expanderPort, char topPort, char bottomPort, boolean reversed) {
        int t = AdiPort.number(topPort);
        if (t % 2 == 0 || AdiPort.number(bottomPort) != t + 1) {
            throw new IllegalArgumentException("Encoder ports must be a pair like 'A','B' or 'C','D'");
        }
        top = AdiPort.open(expanderPort, topPort, AdiPortIO.Mode.ENCODER);
        this.reversed = reversed;
    }

    /** Raw counts. */
    public int get() {
        int v = top.getValue();
        return reversed ? -v : v;
    }

    public void reset() {
        top.setValue(0);
    }

    /** E.g. wheel circumference / 360 for meters. */
    public void setDistancePerPulse(double distance) {
        distancePerPulse = distance;
    }

    public double getDistance() {
        return get() * distancePerPulse;
    }

    /**
     * Speed in distance per second, from the change since the last call (call it once per loop).
     */
    public double getRate() {
        double now = com.robojacks.lib.hal.HAL.now();
        double d = getDistance();
        if (!Double.isNaN(lastTime) && now > lastTime) {
            rate = (d - lastDistance) / (now - lastTime);
        }
        lastTime = now;
        lastDistance = d;
        return rate;
    }
}
