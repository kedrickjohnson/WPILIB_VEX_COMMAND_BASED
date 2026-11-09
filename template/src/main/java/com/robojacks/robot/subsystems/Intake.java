package com.robojacks.robot.subsystems;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.SubsystemBase;
import com.robojacks.lib.hardware.VexMotor;
import com.robojacks.lib.hardware.VexMotorConfig;
import com.robojacks.lib.hardware.VexMotorConfig.IdleMode;

import static com.robojacks.robot.Constants.IntakeConstants.*;

/** Example single-motor mechanism. Copy this pattern for rollers, flywheels, etc. */
public class Intake extends SubsystemBase {
    private final VexMotor m_motor = new VexMotor(kMotorPort, kCartridge);

    public Intake() {
        super("Intake");

        VexMotorConfig config = new VexMotorConfig();
        config.inverted(kReversed)
              .idleMode(IdleMode.kCoast)
              .smartCurrentLimit(kCurrentLimitAmps);
        m_motor.configure(config);
    }

    public void setVoltage(double volts) {
        m_motor.setVoltage(volts);
    }

    public void stop() {
        m_motor.stopMotor();
    }

    /** Runs while held; stops when interrupted. */
    public Command intakeCommand() {
        return startEnd(() -> setVoltage(kIntakeVolts), this::stop).withName("Intake");
    }

    public Command outtakeCommand() {
        return startEnd(() -> setVoltage(kOuttakeVolts), this::stop).withName("Outtake");
    }
}
