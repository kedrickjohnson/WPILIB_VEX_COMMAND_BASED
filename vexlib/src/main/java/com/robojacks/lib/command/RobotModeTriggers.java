package com.robojacks.lib.command;

import com.robojacks.lib.hal.HAL;

/**
 * Triggers for the robot's mode, like WPILib's RobotModeTriggers. Handy for things that should
 * happen when a match phase starts, without putting code in Robot.
 *
 * <pre>{@code
 * // Rumble when driver control starts
 * RobotModeTriggers.teleop().onTrue(Commands.runOnce(() -> m_driverController.rumble("-")));
 * // Retract the intake whenever the robot is disabled
 * RobotModeTriggers.disabled().onTrue(m_intake.retractCommand().ignoringDisable(true));
 * }</pre>
 */
public final class RobotModeTriggers {
    private RobotModeTriggers() {}

    /** True during autonomous. */
    public static Trigger autonomous() {
        return new Trigger(() -> HAL.get().competition().isEnabled() && HAL.get().competition().isAutonomous());
    }

    /** True during driver control (WPILib calls it teleop). */
    public static Trigger teleop() {
        return new Trigger(() -> HAL.get().competition().isEnabled() && !HAL.get().competition().isAutonomous());
    }

    /** VEX's name for {@link #teleop()}. */
    public static Trigger driverControl() {
        return teleop();
    }

    /** True while disabled (before the match, between periods, after it). */
    public static Trigger disabled() {
        return new Trigger(() -> !HAL.get().competition().isEnabled());
    }

    /** True while connected to field control or a competition switch. */
    public static Trigger fieldConnected() {
        return new Trigger(() -> HAL.get().competition().isConnectedToField());
    }
}
