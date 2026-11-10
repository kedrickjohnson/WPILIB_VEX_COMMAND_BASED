package com.robojacks.lib.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.robojacks.lib.SimTestBase;
import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.Commands;
import com.robojacks.lib.hal.GamepadIO;
import com.robojacks.lib.input.VexController;
import org.junit.Test;

public class AutoSelectorTest extends SimTestBase {
    @Test
    public void tapSelectsAndAutonomousLocks() {
        AutoSelector selector = new AutoSelector();
        Command a = Commands.none();
        Command b = Commands.none();
        selector.setDefaultOption("Left", a);
        selector.addOption("Right", b);
        loop();
        assertSame(a, selector.getSelected());
        assertEquals(0x2BA84A, sim.screen.getPixel(60, 70)); // the default button is drawn green

        // Tap the second button (right column, first row)
        sim.screen.press(360, 70);
        loop();
        sim.screen.release();
        loop();
        assertSame(b, selector.getSelected());
        assertEquals("Right", selector.getSelectedName());

        // During autonomous taps are ignored
        sim.competition.enabled = true;
        sim.competition.autonomous = true;
        sim.screen.press(60, 70);
        loop();
        assertEquals("Right", selector.getSelectedName());
    }

    @Test
    public void controllerCyclesWhileDisabled() {
        AutoSelector selector = new AutoSelector();
        selector.setDefaultOption("One", Commands.none());
        selector.addOption("Two", Commands.none());
        selector.addOption("Three", Commands.none());
        VexController controller = new VexController(0);
        selector.addControllerBindings(controller);
        loop();
        sim.primary.buttons[GamepadIO.BUTTON_LEFT] = true;
        loop();
        sim.primary.buttons[GamepadIO.BUTTON_LEFT] = false;
        loop();
        assertEquals("Three", selector.getSelectedName());
        assertEquals("Auto: Three", sim.primary.screenLines[0].trim());
    }

    @Test
    public void manyOptionsPage() {
        AutoSelector selector = new AutoSelector();
        for (int i = 1; i <= 8; i++) {
            selector.addOption("Auto " + i, Commands.none());
        }
        loop();
        sim.screen.press(460, 140); // next page arrow
        loop();
        sim.screen.release();
        loop();
        sim.screen.press(100, 70);  // first button on page 2 = Auto 7
        loop();
        assertEquals("Auto 7", selector.getSelectedName());
    }
}
