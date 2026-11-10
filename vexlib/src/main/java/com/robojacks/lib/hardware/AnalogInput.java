package com.robojacks.lib.hardware;

import com.robojacks.lib.hal.AdiPortIO;

/** A raw analog 3-wire sensor (line tracker, light sensor): 12-bit value, 0 to 4095. */
public class AnalogInput {
    /** Full-scale reading. */
    public static final int kMaxValue = 4095;

    private final AdiPortIO io;

    /** @param port 'A' to 'H' on the brain */
    public AnalogInput(char port) {
        this(0, port);
    }

    /** @param expanderPort smart port of a 3-Wire Expander */
    public AnalogInput(int expanderPort, char port) {
        io = AdiPort.open(expanderPort, port, AdiPortIO.Mode.ANALOG_IN);
    }

    /** 0 to 4095. */
    public int getValue() {
        return io.getValue();
    }

    /** 0 to 1. */
    public double getRatio() {
        return io.getValue() / (double) kMaxValue;
    }
}
