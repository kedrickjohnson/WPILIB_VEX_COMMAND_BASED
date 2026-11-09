package com.robojacks.lib.hal;

/**
 * One V5 Smart Motor. Positions are rotations of the cartridge OUTPUT shaft,
 * which is what VEX reports, so only your external gearing needs converting.
 * Reversal is applied by the implementation, so callers always see "positive = forward".
 */
public interface MotorIO {
    /** -12..12 volts. */
    void setVoltage(double volts);

    /** Total rotations since the last reset. */
    double getPositionRotations();

    /** Rotations per minute. */
    double getVelocityRpm();

    void resetPosition();

    /** true = brake when stopped, false = coast. */
    void setBrakeMode(boolean brake);

    /** Smart port is plugged in and responding. */
    default boolean isConnected() {
        return true;
    }

    default void stop() {
        setVoltage(0.0);
    }
}
