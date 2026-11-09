package com.robojacks.clawbot;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.CommandScheduler;
import com.robojacks.lib.robot.VexRobot;
import com.robojacks.lib.telemetry.Telemetry;

/** Same as an FRC command-based Robot.java. */
public class Robot extends VexRobot {
    private RobotContainer m_robotContainer;
    private Command m_autonomousCommand;

    @Override
    public void robotInit() {
        m_robotContainer = new RobotContainer();
    }

    @Override
    public void robotPeriodic() {
        CommandScheduler.getInstance().run();
        Telemetry.flush();
    }

    @Override
    public void autonomousInit() {
        m_autonomousCommand = m_robotContainer.getAutonomousCommand();
        if (m_autonomousCommand != null) {
            m_autonomousCommand.schedule();
        }
    }

    @Override
    public void teleopInit() {
        if (m_autonomousCommand != null) {
            m_autonomousCommand.cancel();
        }
    }

    public RobotContainer getRobotContainer() {
        return m_robotContainer;
    }
}
