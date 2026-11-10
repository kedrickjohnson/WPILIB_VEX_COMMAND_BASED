package com.robojacks.lib.hal;

/** V5 Distance Sensor. Raw access; robot code uses {@code VexDistanceSensor}. */
public interface DistanceSensorIO {
    /** Distance to the object in meters, or {@code Double.NaN} when nothing is in range (about 2 m). */
    double getDistanceMeters();

    /** How sure the sensor is, 0 to 63. Readings under about 2 m with confidence above 30 are reliable. */
    int getConfidence();

    /** Rough size of the object, 0 to 400 (bigger and more reflective objects read higher). */
    int getObjectSize();

    /** Speed of the object toward (negative) or away from (positive) the sensor, m/s. */
    double getObjectVelocityMps();

    boolean isConnected();
}
