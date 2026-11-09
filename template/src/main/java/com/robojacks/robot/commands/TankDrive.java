package com.robojacks.robot.commands;

import com.robojacks.lib.command.Command;
import com.robojacks.robot.subsystems.DriveSubsystem;
import java.util.function.DoubleSupplier;

/**
 * Drives the robot with tank controls: left stick runs the left side, right stick the right.
 * This is the drivetrain's default command.
 */
public class TankDrive extends Command {
    private final DriveSubsystem m_drive;
    private final DoubleSupplier m_left;
    private final DoubleSupplier m_right;

    /**
     * @param drive the drive subsystem
     * @param left  left side speed, -1..1
     * @param right right side speed, -1..1
     */
    public TankDrive(DriveSubsystem drive, DoubleSupplier left, DoubleSupplier right) {
        m_drive = drive;
        m_left = left;
        m_right = right;
        addRequirements(m_drive);
        setName("TankDrive");
    }

    @Override
    public void execute() {
        m_drive.tankDrive(m_left.getAsDouble(), m_right.getAsDouble());
    }

    @Override
    public void end(boolean interrupted) {
        m_drive.stop();
    }
}
