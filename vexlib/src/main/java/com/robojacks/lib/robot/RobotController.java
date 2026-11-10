package com.robojacks.lib.robot;

import com.robojacks.lib.hal.HAL;

/**
 * Battery and timing information, like WPILib's RobotController.
 *
 * <pre>{@code
 * if (RobotController.getBatteryPercent() < 30) {
 *     m_driverController.setText(2, 0, "LOW BATTERY");
 * }
 * }</pre>
 */
public final class RobotController {
    private RobotController() {}

    /** Battery voltage, about 12.8 V full and 12 V nearly empty. */
    public static double getBatteryVoltage() {
        return HAL.get().power().getBatteryVolts();
    }

    /** Remaining battery charge, 0 to 100. */
    public static double getBatteryPercent() {
        return HAL.get().power().getBatteryPercent();
    }

    /** Current the whole robot draws, amps. */
    public static double getBatteryCurrent() {
        return HAL.get().power().getBatteryAmps();
    }

    public static double getBatteryTemperature() {
        return HAL.get().power().getBatteryTemperatureCelsius();
    }

    /** Seconds since the program started (WPILib's {@code getFPGATime} is microseconds; this is seconds). */
    public static double getTime() {
        return HAL.now();
    }
}
