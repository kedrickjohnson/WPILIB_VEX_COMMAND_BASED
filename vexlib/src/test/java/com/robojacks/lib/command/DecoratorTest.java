package com.robojacks.lib.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.robojacks.lib.SimTestBase;
import org.junit.Test;

public class DecoratorTest extends SimTestBase {
    private int count;

    @Test
    public void repeatedlyRestarts() {
        Command c = Commands.runOnce(() -> count++).repeatedly();
        c.schedule();
        run(0.05);
        assertTrue(count >= 4);
        assertTrue(c.isScheduled());
    }

    @Test
    public void unlessSkips() {
        Command c = Commands.runOnce(() -> count++).unless(() -> true);
        c.schedule();
        run(0.02);
        assertEquals(0, count);
        Commands.runOnce(() -> count++).onlyIf(() -> true).schedule();
        run(0.02);
        assertEquals(1, count);
    }

    @Test
    public void cancelIncomingKeepsTheRunningCommand() {
        SubsystemBase sub = new SubsystemBase("Sub") {};
        Command first = Commands.run(() -> {}, sub).withInterruptBehavior(Command.InterruptionBehavior.kCancelIncoming);
        Command second = Commands.run(() -> {}, sub);
        first.schedule();
        loop();
        second.schedule();
        loop();
        assertTrue(first.isScheduled());
        assertFalse(second.isScheduled());
    }

    @Test
    public void handleInterruptOnlyOnInterrupt() {
        Command c = Commands.waitSeconds(1.0).handleInterrupt(() -> count++);
        c.schedule();
        loop();
        c.cancel();
        assertEquals(1, count);
        Command d = Commands.waitSeconds(0.02).handleInterrupt(() -> count++);
        d.schedule();
        run(0.1);
        assertEquals(1, count);
    }
}
