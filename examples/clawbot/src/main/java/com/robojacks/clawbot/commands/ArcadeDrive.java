package com.robojacks.clawbot.commands;

import com.robojacks.lib.command.Command;
import com.robojacks.clawbot.subsystems.DriveSubsystem;
import java.util.function.DoubleSupplier;

/**
 * Drives the robot with arcade controls: one stick forward/back, the other turns.
 * This is the drivetrain's default command.
 */
public class ArcadeDrive extends Command {
    private final DriveSubsystem m_drive;
    private final DoubleSupplier m_forward;
    private final DoubleSupplier m_rotation;

    /**
     * @param drive    the drive subsystem
     * @param forward  forward speed, -1..1
     * @param rotation rotation, -1..1, counter-clockwise (left) positive
     */
    public ArcadeDrive(DriveSubsystem drive, DoubleSupplier forward, DoubleSupplier rotation) {
        m_drive = drive;
        m_forward = forward;
        m_rotation = rotation;
        addRequirements(m_drive);
        setName("ArcadeDrive");
    }

    @Override
    public void execute() {
        m_drive.arcadeDrive(m_forward.getAsDouble(), m_rotation.getAsDouble());
    }

    @Override
    public void end(boolean interrupted) {
        m_drive.stop();
    }
}
