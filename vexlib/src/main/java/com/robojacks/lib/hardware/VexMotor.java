package com.robojacks.lib.hardware;

import com.robojacks.lib.hal.Cartridge;
import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.hal.MotorIO;
import com.robojacks.lib.hal.MotorIO.NeutralMode;

/**
 * A V5 Smart Motor. Use it like a vendor motor class in FRC:
 * <pre>{@code
 * private final VexMotor armMotor = new VexMotor(8, false, Cartridge.GREEN);
 * }</pre>
 * Position is in rotations of the cartridge output shaft (VEX reports position after the
 * cartridge), velocity in rpm.
 */
public class VexMotor {
    private final MotorIO io;
    private final int port;
    private final Cartridge cartridge;

    public VexMotor(int port, boolean reversed, Cartridge cartridge) {
        this.port = port;
        this.cartridge = cartridge;
        this.io = HAL.get().motor(port, reversed, cartridge);
    }

    /** Percent output, -1..1. */
    public void set(double speed) {
        setVoltage(12.0 * speed);
    }

    /** -12..12 volts. 0 applies the neutral mode (coast/brake/hold). */
    public void setVoltage(double volts) {
        io.setVoltage(Math.max(-12.0, Math.min(12.0, volts)));
    }

    public void stopMotor() {
        io.setVoltage(0.0);
    }

    public void setNeutralMode(NeutralMode mode) {
        io.setNeutralMode(mode);
    }

    /** Move to a position with the motor's built-in PID. */
    public void setPositionTarget(double rotations, double maxRpm) {
        io.setPositionTarget(rotations, maxRpm);
    }

    /** Rotations since the last reset. */
    public double getPosition() {
        return io.getPositionRotations();
    }

    /** RPM. */
    public double getVelocity() {
        return io.getVelocityRpm();
    }

    public void resetPosition() {
        io.resetPosition();
    }

    public boolean isConnected() {
        return io.isConnected();
    }

    public int getPort() {
        return port;
    }

    public Cartridge getCartridge() {
        return cartridge;
    }
}
