package com.robojacks.lib.hal;

/**
 * Everything robot code needs from the brain. One implementation talks to Hydrozoa,
 * another is a desktop simulator. Robot code never imports a vendor API directly.
 */
public interface Hardware {
    Clock clock();

    /** @param port smart port 1-21 */
    MotorIO motor(int port, boolean reversed);

    ImuIO imu(int port);

    GamepadIO primaryController();

    GamepadIO partnerController();

    CompetitionIO competition();
}
