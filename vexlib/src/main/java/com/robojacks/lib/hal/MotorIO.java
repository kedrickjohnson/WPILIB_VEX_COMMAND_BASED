package com.robojacks.lib.hal;

/**
 * One V5 Smart Motor. Positions are rotations of the cartridge OUTPUT shaft,
 * which is what VEX reports, so only your external gearing needs converting.
 * Reversal is applied by the implementation, so callers always see "positive = forward".
 */
public interface MotorIO {
    /** What the motor does when told 0 volts. */
    enum NeutralMode {
        /** Spin freely. */
        COAST,
        /** Short the windings: stops quickly, can be back-driven slowly. */
        BRAKE,
        /** Actively hold the current position (arms, lifts). */
        HOLD
    }

    /** -12..12 volts. Exactly 0 applies the neutral mode. */
    void setVoltage(double volts);

    void setNeutralMode(NeutralMode mode);

    /**
     * Move to an absolute position using the motor's built-in PID.
     *
     * @param rotations target, in output-shaft rotations since the last reset
     * @param maxRpm    speed limit for the move
     */
    void setPositionTarget(double rotations, double maxRpm);

    /** Total rotations since the last reset. */
    double getPositionRotations();

    /** Rotations per minute. */
    double getVelocityRpm();

    void resetPosition();

    /** Smart port is plugged in and responding. */
    default boolean isConnected() {
        return true;
    }

    default void stop() {
        setVoltage(0.0);
    }
}
