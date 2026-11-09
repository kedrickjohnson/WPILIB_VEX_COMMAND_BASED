package com.robojacks.robot;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.CommandScheduler;
import com.robojacks.lib.robot.VexRobot;
import com.robojacks.lib.telemetry.Telemetry;

/** Almost line-for-line the FRC command-based Robot.java. */
public class Robot extends VexRobot {
    private RobotContainer container;
    private Command autonomousCommand;

    @Override
    public void robotInit() {
        container = new RobotContainer();
    }

    @Override
    public void robotPeriodic() {
        CommandScheduler.getInstance().run();
        Telemetry.flush();
    }

    @Override
    public void autonomousInit() {
        autonomousCommand = container.getAutonomousCommand();
        if (autonomousCommand != null) {
            autonomousCommand.schedule();
        }
    }

    @Override
    public void teleopInit() {
        if (autonomousCommand != null) {
            autonomousCommand.cancel();
        }
    }

    public RobotContainer getContainer() {
        return container;
    }
}
