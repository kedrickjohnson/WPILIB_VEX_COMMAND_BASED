package com.robojacks.lib.hardware;

import com.robojacks.lib.command.Trigger;
import com.robojacks.lib.hal.AdiPortIO;

/**
 * A limit switch, bumper switch or other on/off sensor in a 3-wire port. Same name as WPILib's,
 * but {@link #get()} returns true when PRESSED (WPILib's returns the raw, usually inverted, signal).
 *
 * <pre>{@code
 * DigitalInput m_armDown = new DigitalInput('A');
 * new Trigger(m_armDown::get).onTrue(m_arm.resetEncoderCommand());
 * }</pre>
 */
public class DigitalInput {
    private final AdiPortIO io;

    /** @param port 'A' to 'H' on the brain */
    public DigitalInput(char port) {
        this(0, port);
    }

    /** @param expanderPort smart port of a 3-Wire Expander */
    public DigitalInput(int expanderPort, char port) {
        io = AdiPort.open(expanderPort, port, AdiPortIO.Mode.DIGITAL_IN);
    }

    /** True while the switch is pressed. */
    public boolean get() {
        return io.getValue() != 0;
    }

    /** A trigger that's true while pressed, for command bindings. */
    public Trigger asTrigger() {
        return new Trigger(this::get);
    }
}
