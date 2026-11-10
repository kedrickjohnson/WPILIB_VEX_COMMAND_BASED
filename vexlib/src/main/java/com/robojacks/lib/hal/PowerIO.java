package com.robojacks.lib.hal;

/** The robot battery, as the brain sees it. */
public interface PowerIO {
    double getBatteryVolts();

    /** Remaining charge, 0 to 100 percent. */
    double getBatteryPercent();

    double getBatteryAmps();

    double getBatteryTemperatureCelsius();
}
