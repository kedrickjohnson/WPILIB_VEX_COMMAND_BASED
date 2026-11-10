package com.robojacks.lib.hal;

/**
 * One 3-wire (ADI) port: A to H on the brain, or on a 3-Wire Expander in a smart port.
 * Robot code uses {@code DigitalInput}, {@code Solenoid}, {@code AnalogInput} and the rest.
 */
public interface AdiPortIO {
    /** What the port is used for. The brain configures the port's hardware to match. */
    enum Mode {
        /** Limit switch, bumper, or any on/off input (reads 1 when pressed). */
        DIGITAL_IN,
        /** Pneumatic solenoid, LED, or any on/off output. */
        DIGITAL_OUT,
        /** Potentiometer, line tracker, light sensor: 12-bit value, 0 to 4095. */
        ANALOG_IN,
        /** Top wire of a quadrature encoder (360 counts per revolution); the bottom wire goes in the next port. */
        ENCODER
    }

    void configure(Mode mode);

    /** Digital: 0 or 1. Analog: 0 to 4095. Encoder: counts. */
    int getValue();

    /** Digital out: 0 or 1. Encoder: sets the count (0 to reset). */
    void setValue(int value);
}
