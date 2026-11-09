package com.robojacks.lib.hardware;

/**
 * Several motors that act as one (WPILib's MotorControllerGroup). Usually a leader with
 * followers ({@link VexMotorConfig#follow}) is simpler; use a group when you want readings
 * averaged across every motor.
 */
public class VexMotorGroup {
    private final VexMotor[] motors;

    public VexMotorGroup(VexMotor... motors) {
        if (motors.length == 0) {
            throw new IllegalArgumentException("VexMotorGroup needs at least one motor");
        }
        this.motors = motors.clone();
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

    /** Configures every motor in the group with the same settings. */
    public void configure(VexMotorConfig config) {
        for (VexMotor m : motors) {
            m.configure(config);
        }
    }

    /** Average encoder position across the motors, in their configured units. */
    public double getPosition() {
        double sum = 0.0;
        for (VexMotor m : motors) {
            sum += m.getEncoder().getPosition();
        }
        return sum / motors.length;
    }

    /** Average encoder velocity across the motors, in their configured units. */
    public double getVelocity() {
        double sum = 0.0;
        for (VexMotor m : motors) {
            sum += m.getEncoder().getVelocity();
        }
        return sum / motors.length;
    }

    public void resetPosition() {
        for (VexMotor m : motors) {
            m.getEncoder().setPosition(0.0);
        }
    }

    public int size() {
        return motors.length;
    }

    public VexMotor get(int index) {
        return motors[index];
    }
}
