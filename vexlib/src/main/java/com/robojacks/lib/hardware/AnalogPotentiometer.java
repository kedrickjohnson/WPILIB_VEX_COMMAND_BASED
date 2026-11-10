package com.robojacks.lib.hardware;

/**
 * A potentiometer in a 3-wire port, scaled like WPILib's AnalogPotentiometer:
 * {@code get() = ratio * fullRange + offset}.
 *
 * <pre>{@code
 * // V2 potentiometer: 333 degrees of travel; arm is level at a reading of 150 degrees
 * AnalogPotentiometer m_armAngle = new AnalogPotentiometer('C', 333.0, -150.0);
 * double degreesFromLevel = m_armAngle.get();
 * }</pre>
 */
public class AnalogPotentiometer {
    /** Travel of the VEX Potentiometer V2, degrees. */
    public static final double kV2RangeDegrees = 333.0;
    /** Travel of the original VEX Potentiometer, degrees. */
    public static final double kV1RangeDegrees = 250.0;

    private final AnalogInput input;
    private final double fullRange;
    private final double offset;

    public AnalogPotentiometer(char port, double fullRange, double offset) {
        this(0, port, fullRange, offset);
    }

    public AnalogPotentiometer(int expanderPort, char port, double fullRange, double offset) {
        input = new AnalogInput(expanderPort, port);
        this.fullRange = fullRange;
        this.offset = offset;
    }

    /** Degrees (or whatever fullRange is in) of a V2 potentiometer with no offset. */
    public AnalogPotentiometer(char port) {
        this(port, kV2RangeDegrees, 0.0);
    }

    public double get() {
        return input.getRatio() * fullRange + offset;
    }
}
