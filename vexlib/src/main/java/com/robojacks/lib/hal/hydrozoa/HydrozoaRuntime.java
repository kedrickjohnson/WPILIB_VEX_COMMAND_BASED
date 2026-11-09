package com.robojacks.lib.hal.hydrozoa;

import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.robot.VexRobot;
import dev.vexide.hydrozoa.Peripherals;
import dev.vexide.hydrozoa.Platform;
import java.util.function.Supplier;

/**
 * Starts a VexRobot on the brain. Your Main.java is just:
 * <pre>{@code
 * public static void main(String[] args) {
 *     HydrozoaRuntime.start(Robot::new);
 * }
 * }</pre>
 * Uncaught exceptions are printed over serial and shown on the brain screen.
 */
public final class HydrozoaRuntime {
    private HydrozoaRuntime() {}

    public static void start(Supplier<VexRobot> robotFactory) {
        try {
            Peripherals peripherals = Peripherals.take()
                    .orElseThrow(() -> new IllegalStateException("Peripherals already taken"));
            HAL.set(new HydrozoaHardware(peripherals));
            VexRobot.startRobot(robotFactory.get());
        } catch (Throwable e) {
            Platform.handleException(e);
        }
    }
}
