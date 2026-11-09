package com.robojacks.clawbot.subsystems;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.Commands;
import com.robojacks.lib.command.SubsystemBase;
import com.robojacks.lib.hal.MotorIO.NeutralMode;
import com.robojacks.lib.hardware.VexMotor;
import com.robojacks.lib.telemetry.Telemetry;

import static com.robojacks.clawbot.Constants.ArmConstants.*;

/**
 * The Clawbot arm. Manual up/down with soft limits, plus presets that use the motor's
 * built-in position PID. The motor HOLDs position whenever it's stopped, so the arm
 * doesn't sag.
 *
 * <p>Start the program with the arm all the way down: that position is 0 degrees.
 */
public class Arm extends SubsystemBase {
    private final VexMotor m_motor = new VexMotor(kMotorPort, kReversed, kCartridge);
    private double m_targetDegrees = kDownDegrees;

    public Arm() {
        super("Arm");
        m_motor.setNeutralMode(NeutralMode.HOLD);
        m_motor.resetPosition();
    }

    @Override
    public void periodic() {
        Telemetry.put("arm/deg", getAngleDegrees());
        Telemetry.put("arm/target", m_targetDegrees);
    }

    /** Arm angle in degrees above the starting (down) position. */
    public double getAngleDegrees() {
        return m_motor.getPosition() * kGearRatio * 360.0;
    }

    private static double degreesToMotorRotations(double degrees) {
        return degrees / 360.0 / kGearRatio;
    }

    /** Manual drive with soft limits: won't push past kMinDegrees or kMaxDegrees. */
    public void setVoltage(double volts) {
        double angle = getAngleDegrees();
        if ((volts > 0 && angle >= kMaxDegrees) || (volts < 0 && angle <= kMinDegrees)) {
            volts = 0.0;
        }
        m_motor.setVoltage(volts);
    }

    /** Stop and hold the current position. */
    public void hold() {
        m_targetDegrees = getAngleDegrees();
        m_motor.stopMotor(); // neutral mode is HOLD
    }

    /** Start a move to an angle with the motor's built-in PID (keeps holding when it gets there). */
    public void moveTo(double degrees) {
        m_targetDegrees = Math.max(kMinDegrees, Math.min(kMaxDegrees, degrees));
        m_motor.setPositionTarget(degreesToMotorRotations(m_targetDegrees), kPresetMaxRpm);
    }

    public boolean atTarget() {
        return Math.abs(getAngleDegrees() - m_targetDegrees) < kToleranceDegrees;
    }

    // =====================================================================
    // Commands
    // =====================================================================

    /** Raise while held, then hold position. */
    public Command raiseCommand() {
        return runEnd(() -> setVoltage(kManualVolts), this::hold).withName("ArmUp");
    }

    /** Lower while held, then hold position. */
    public Command lowerCommand() {
        return runEnd(() -> setVoltage(-kManualVolts), this::hold).withName("ArmDown");
    }

    /** Move to an angle and finish when there (or after a timeout). */
    public Command goToAngleCommand(double degrees) {
        return runOnce(() -> moveTo(degrees))
                .andThen(Commands.waitUntil(this::atTarget))
                .withTimeout(kMoveTimeoutSeconds)
                .withName("ArmTo(" + Math.round(degrees) + ")");
    }

    public Command downCommand() {
        return goToAngleCommand(kDownDegrees);
    }

    public Command carryCommand() {
        return goToAngleCommand(kCarryDegrees);
    }

    public Command highCommand() {
        return goToAngleCommand(kHighDegrees);
    }
}
