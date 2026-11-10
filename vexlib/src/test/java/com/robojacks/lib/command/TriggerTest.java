package com.robojacks.lib.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.robojacks.lib.SimTestBase;
import com.robojacks.lib.hal.GamepadIO;
import com.robojacks.lib.input.VexController;
import org.junit.Test;

public class TriggerTest extends SimTestBase {
    private boolean condition;
    private int runs;

    private Command counter() {
        return Commands.runOnce(() -> runs++);
    }

    @Test
    public void onTrueFiresOncePerPress() {
        new Trigger(() -> condition).onTrue(counter());
        loop();
        condition = true;
        run(0.1);
        condition = false;
        loop();
        condition = true;
        loop();
        assertEquals(2, runs);
    }

    @Test
    public void whileTrueCancelsOnRelease() {
        Command held = Commands.run(() -> {});
        new Trigger(() -> condition).whileTrue(held);
        condition = true;
        loop();
        assertTrue(held.isScheduled());
        condition = false;
        loop();
        assertFalse(held.isScheduled());
    }

    @Test
    public void toggleOnFalseTogglesOnRelease() {
        Command c = Commands.run(() -> {});
        new Trigger(() -> condition).toggleOnFalse(c);
        condition = true;
        loop();
        assertFalse(c.isScheduled());
        condition = false;
        loop();
        assertTrue(c.isScheduled());
        condition = true;
        loop();
        condition = false;
        loop();
        assertFalse(c.isScheduled());
    }

    @Test
    public void debounceNeedsTheConditionToHold() {
        new Trigger(() -> condition).debounce(0.1).onTrue(counter());
        condition = true;
        run(0.05);
        condition = false;
        run(0.05);
        assertEquals(0, runs);
        condition = true;
        run(0.2);
        assertEquals(1, runs);
    }

    @Test
    public void risingIsTrueForOneLoop() {
        Trigger rising = new Trigger(() -> condition).rising();
        assertFalse(rising.getAsBoolean());
        condition = true;
        assertTrue(rising.getAsBoolean());
        assertFalse(rising.getAsBoolean());
    }

    @Test
    public void axisTriggersAndControllerScreen() {
        VexController controller = new VexController(0);
        Trigger pushed = controller.axisMagnitudeGreaterThan(VexController.Axis.kRightX, 0.5);
        sim.primary.axes[GamepadIO.AXIS_RIGHT_X] = -0.8;
        assertTrue(pushed.getAsBoolean());
        sim.primary.axes[GamepadIO.AXIS_RIGHT_X] = 0.2;
        assertFalse(pushed.getAsBoolean());

        controller.setText(1, 2, "Hello");
        assertEquals("  Hello", sim.primary.screenLines[1].substring(0, 7));
        controller.rumble("..-");
        assertEquals("..-", sim.primary.lastRumble);
    }

    @Test
    public void robotModeTriggers() {
        RobotModeTriggers.autonomous().onTrue(counter());
        sim.competition.enabled = true;
        sim.competition.autonomous = true;
        loop();
        assertEquals(1, runs);
        assertTrue(RobotModeTriggers.autonomous().getAsBoolean());
        assertFalse(RobotModeTriggers.teleop().getAsBoolean());
        sim.competition.enabled = false;
        assertTrue(RobotModeTriggers.disabled().getAsBoolean());
    }
}
