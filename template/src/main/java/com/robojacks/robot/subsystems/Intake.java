package com.robojacks.robot.subsystems;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.SubsystemBase;
import com.robojacks.lib.hal.MotorIO.NeutralMode;
import com.robojacks.lib.hardware.VexMotor;

import static com.robojacks.robot.Constants.IntakeConstants.*;

/** Example single-motor mechanism. Copy this pattern for rollers, flywheels, etc. */
public class Intake extends SubsystemBase {
    private final VexMotor m_motor = new VexMotor(kMotorPort, kReversed, kCartridge);

    public Intake() {
        super("Intake");
        m_motor.setNeutralMode(NeutralMode.COAST);
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
