package com.robojacks.lib.hal;

/** V5 Optical Sensor. Raw access; robot code uses {@code VexOpticalSensor}. */
public interface OpticalSensorIO {
    /** Hue in degrees, 0 to 360 (red near 0/360, green 120, blue 240). */
    double getHue();

    /** Saturation, 0 to 1. */
    double getSaturation();

    /** Brightness, 0 to 1. */
    double getBrightness();

    /** Proximity, 0 (far) to 255 (touching). */
    int getProximity();

    /** Built-in white LED, 0 (off) to 1 (full). */
    void setLedPower(double power);

    boolean isConnected();
}
