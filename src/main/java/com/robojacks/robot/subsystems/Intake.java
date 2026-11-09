package com.robojacks.robot.subsystems;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.SubsystemBase;
import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.hal.MotorIO;

import static com.robojacks.robot.Constants.IntakeConstants.*;

/** Example single-motor mechanism. Copy this pattern for arms, lifts, flywheels, etc. */
public class Intake extends SubsystemBase {
    private final MotorIO motor;

    public Intake() {
        super("Intake");
        motor = HAL.get().motor(PORT, REVERSED);
        motor.setBrakeMode(false);
    }

    public void setVoltage(double volts) {
        motor.setVoltage(volts);
    }

    public void stop() {
        motor.stop();
    }

    /** Runs while held; stops when interrupted. */
    public Command intakeCommand() {
        return startEnd(() -> setVoltage(INTAKE_VOLTS), this::stop).withName("Intake");
    }

    public Command outtakeCommand() {
        return startEnd(() -> setVoltage(OUTTAKE_VOLTS), this::stop).withName("Outtake");
    }
}
