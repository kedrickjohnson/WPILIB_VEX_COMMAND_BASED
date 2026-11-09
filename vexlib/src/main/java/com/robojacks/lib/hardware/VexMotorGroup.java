package com.robojacks.lib.hardware;

import com.robojacks.lib.hal.Cartridge;
import com.robojacks.lib.hal.MotorIO.NeutralMode;

/**
 * Several motors that act as one, like one side of a drivetrain
 * (WPILib's MotorControllerGroup). Readings are averaged across the motors.
 */
public class VexMotorGroup {
    private final VexMotor[] motors;

    public VexMotorGroup(VexMotor... motors) {
        if (motors.length == 0) {
            throw new IllegalArgumentException("VexMotorGroup needs at least one motor");
        }
        this.motors = motors.clone();
    }

    /** Builds a group from parallel arrays of ports and reversed flags. */
    public VexMotorGroup(int[] ports, boolean[] reversed, Cartridge cartridge) {
        if (ports.length != reversed.length) {
            throw new IllegalArgumentException("ports and reversed flags must be the same length");
        }
        motors = new VexMotor[ports.length];
        for (int i = 0; i < ports.length; i++) {
            motors[i] = new VexMotor(ports[i], reversed[i], cartridge);
        }
    }

    public void set(double speed) {
        setVoltage(12.0 * speed);
    }

    public void setVoltage(double volts) {
        for (VexMotor m : motors) {
            m.setVoltage(volts);
        }
    }

    public void stopMotor() {
        setVoltage(0.0);
    }

    public void setNeutralMode(NeutralMode mode) {
        for (VexMotor m : motors) {
            m.setNeutralMode(mode);
        }
    }

    /** Average rotations. */
    public double getPosition() {
        double sum = 0.0;
        for (VexMotor m : motors) {
            sum += m.getPosition();
        }
        return sum / motors.length;
    }

    /** Average RPM. */
    public double getVelocity() {
        double sum = 0.0;
        for (VexMotor m : motors) {
            sum += m.getVelocity();
        }
        return sum / motors.length;
    }

    public void resetPosition() {
        for (VexMotor m : motors) {
            m.resetPosition();
        }
    }

    public int size() {
        return motors.length;
    }

    public VexMotor get(int index) {
        return motors[index];
    }
}
