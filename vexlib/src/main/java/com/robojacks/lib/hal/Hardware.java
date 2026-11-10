package com.robojacks.lib.hal;

/**
 * Everything robot code needs from the brain. One implementation talks to Hydrozoa,
 * another is a desktop simulator. Robot code never imports a vendor API directly.
 */
public interface Hardware {
    Clock clock();

    /**
     * @param port      smart port 1-21 (each port can only be used once)
     * @param reversed  flip the motor's direction
     * @param cartridge the cartridge actually installed; sets encoder scaling and speed
     */
    MotorIO motor(int port, boolean reversed, Cartridge cartridge);

    ImuIO imu(int port);

    GamepadIO primaryController();

    GamepadIO partnerController();

    CompetitionIO competition();

    // ---- Smart sensors (each takes its smart port, 1-21) ----

    RotationSensorIO rotationSensor(int port);

    DistanceSensorIO distanceSensor(int port);

    OpticalSensorIO opticalSensor(int port);

    GpsIO gps(int port);

    // ---- 3-wire ports ----

    /**
     * @param expanderPort 0 for the brain's own 3-wire ports, or the smart port of a 3-Wire Expander
     * @param adiPort      1-8 for ports A-H
     */
    AdiPortIO adiPort(int expanderPort, int adiPort);

    // ---- Brain ----

    ScreenIO screen();

    PowerIO power();

    /** Called once per robot loop, after the robot code runs. Lets the hardware layer do housekeeping. */
    default void periodic() {}
}
