package com.robojacks.docs;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.SubsystemBase;
import com.robojacks.lib.hardware.DigitalInput;
import com.robojacks.lib.hardware.Solenoid;

// [clamp]
/** A pneumatic clamp (port B) with a limit switch (port A) that's pressed when a goal is in place. */
public class ExampleClamp extends SubsystemBase {
    private final Solenoid m_clamp = new Solenoid('B');
    private final DigitalInput m_goalSwitch = new DigitalInput('A');

    public ExampleClamp() {
        super("Clamp");
    }

    public boolean isGoalPresent() {
        return m_goalSwitch.get();
    }

    public Command clampCommand() {
        return runOnce(() -> m_clamp.set(true)).withName("Clamp");
    }

    public Command releaseCommand() {
        return runOnce(() -> m_clamp.set(false)).withName("Release");
    }

    public Command toggleCommand() {
        return runOnce(m_clamp::toggle).withName("ToggleClamp");
    }

    /** Waits for a goal to hit the switch, then clamps it. */
    public Command clampWhenGoalArrivesCommand() {
        return idle().until(this::isGoalPresent).andThen(clampCommand()).withName("AutoClamp");
    }
}
// [/clamp]
