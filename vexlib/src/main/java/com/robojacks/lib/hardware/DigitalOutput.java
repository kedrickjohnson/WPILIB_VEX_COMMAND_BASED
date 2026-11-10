package com.robojacks.lib.hardware;

import com.robojacks.lib.hal.AdiPortIO;

/** An on/off output in a 3-wire port (an LED, a relay). For pneumatics use {@link Solenoid}. */
public class DigitalOutput {
    private final AdiPortIO io;
    private boolean on;

    /** @param port 'A' to 'H' on the brain */
    public DigitalOutput(char port) {
        this(0, port);
    }

    /** @param expanderPort smart port of a 3-Wire Expander */
    public DigitalOutput(int expanderPort, char port) {
        io = AdiPort.open(expanderPort, port, AdiPortIO.Mode.DIGITAL_OUT);
        set(false);
    }

    public void set(boolean value) {
        on = value;
        io.setValue(value ? 1 : 0);
    }

    public boolean get() {
        return on;
    }
}
