package com.robojacks.clawbot.subsystems;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.Commands;
import com.robojacks.lib.command.FunctionalCommand;
import com.robojacks.lib.command.SubsystemBase;
import com.robojacks.lib.hardware.VexEncoder;
import com.robojacks.lib.hardware.VexMotor;
import com.robojacks.lib.hardware.VexMotorConfig;
import com.robojacks.lib.hardware.VexMotorConfig.IdleMode;
import com.robojacks.lib.robot.Timer;
import com.robojacks.lib.telemetry.Telemetry;

import static com.robojacks.clawbot.Constants.ClawConstants.*;

/**
 * The Clawbot claw. Open and close run the motor until it stalls (fully open, or gripping
 * something), so they work for any object size. After closing, a gentle squeeze stays on.
 */
public class Claw extends SubsystemBase {
    private final VexMotor m_motor = new VexMotor(kMotorPort, kCartridge);
    private final VexEncoder m_encoder = m_motor.getEncoder(); // RPM (no conversion factor)
    private final Timer m_timer = new Timer();
    private boolean m_closed = false;

    public Claw() {
        super("Claw");

        VexMotorConfig config = new VexMotorConfig();
        config.inverted(kReversed)
              .idleMode(IdleMode.kHold)
              .smartCurrentLimit(kCurrentLimitAmps);
        m_motor.configure(config);
    }

    @Override
    public void periodic() {
        Telemetry.put("claw/closed", m_closed);
    }

    public boolean isClosed() {
        return m_closed;
    }

    private boolean isStalled() {
        return m_timer.hasElapsed(kSpinUpSeconds) && Math.abs(m_encoder.getVelocity()) < kStallRpm;
    }

    /** Runs the claw motor at a voltage until it stalls or times out. */
    private Command runUntilStalled(double volts) {
        return new FunctionalCommand(
                () -> {
                    m_timer.restart();
                    m_motor.setVoltage(volts);
                },
                () -> {},
                interrupted -> {},
                () -> isStalled() || m_timer.hasElapsed(kMoveTimeoutSeconds),
                this);
    }

    // =====================================================================
    // Commands
    // =====================================================================

    public Command openCommand() {
        return runUntilStalled(kOpenVolts)
                .finallyDo(() -> {
                    m_motor.stopMotor(); // idle mode is kHold
                    m_closed = false;
                })
                .withName("OpenClaw");
    }

    public Command closeCommand() {
        return runUntilStalled(kCloseVolts)
                .finallyDo(() -> {
                    m_motor.setVoltage(kSqueezeVolts); // keep gripping
                    m_closed = true;
                })
                .withName("CloseClaw");
    }

    public Command toggleCommand() {
        return Commands.either(openCommand(), closeCommand(), this::isClosed).withName("ToggleClaw");
    }
}
