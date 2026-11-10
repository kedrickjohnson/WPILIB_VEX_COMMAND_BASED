package com.robojacks.lib.hardware;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.Commands;
import com.robojacks.lib.command.Subsystem;

/**
 * A pneumatic solenoid valve in a 3-wire port, like WPILib's Solenoid. VEX pneumatics are
 * single-acting or use one valve per cylinder: {@code set(true)} opens the valve (extends the
 * cylinder), {@code set(false)} closes it.
 *
 * <pre>{@code
 * private final Solenoid m_clamp = new Solenoid('B');
 *
 * public Command clampCommand() { return runOnce(() -> m_clamp.set(true)); }
 * public Command releaseCommand() { return runOnce(() -> m_clamp.set(false)); }
 * }</pre>
 *
 * <p>Air is limited (a VEX tank holds roughly 15-20 actuations of a 2-inch-stroke cylinder at
 * 100 psi), so count your uses in autonomous.
 */
public class Solenoid {
    private final DigitalOutput out;
    private final boolean invert;
    private int actuations;

    /** @param port 'A' to 'H' on the brain */
    public Solenoid(char port) {
        this(0, port, false);
    }

    /** @param expanderPort smart port of a 3-Wire Expander */
    public Solenoid(int expanderPort, char port) {
        this(expanderPort, port, false);
    }

    /**
     * @param inverted true if the valve is plumbed so that "off" extends the cylinder; then
     *                 {@code set(true)} still means extended
     */
    public Solenoid(int expanderPort, char port, boolean inverted) {
        out = new DigitalOutput(expanderPort, port);
        invert = inverted;
        out.set(inverted);
    }

    /** true = extended. */
    public void set(boolean on) {
        if (on != get()) {
            actuations++;
        }
        out.set(on != invert);
    }

    public boolean get() {
        return out.get() != invert;
    }

    public void toggle() {
        set(!get());
    }

    /** How many times the solenoid changed state since the program started (a rough air-use counter). */
    public int getActuationCount() {
        return actuations;
    }

    /** A command that sets the solenoid once. Pass the subsystem that owns it. */
    public Command setCommand(boolean on, Subsystem owner) {
        return Commands.runOnce(() -> set(on), owner);
    }
}
