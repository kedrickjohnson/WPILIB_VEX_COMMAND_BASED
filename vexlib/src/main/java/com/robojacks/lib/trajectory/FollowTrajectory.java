package com.robojacks.lib.trajectory;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.drive.DriveGains;
import com.robojacks.lib.drive.TankDriveSubsystem;
import com.robojacks.lib.math.MathUtil;
import com.robojacks.lib.robot.Timer;
import com.robojacks.lib.telemetry.Telemetry;

/**
 * Drives a tank drive along a {@link Trajectory}. Each loop it samples the trajectory, corrects
 * for drift with a {@link RamseteController}, splits the result into left and right wheel
 * speeds, and turns those into volts with feedforward (kS + kV * speed + kA * accel) plus feedback
 * on the measured wheel speed.
 *
 * <p>Needs kS, kV and kA in your DriveGains: run tuning test A (characterization) first.
 */
public class FollowTrajectory extends Command {
    private static final double LOOKAHEAD = 0.02;

    private final TankDriveSubsystem drive;
    private final Trajectory trajectory;
    private final RamseteController controller;
    private final Timer timer = new Timer();

    public FollowTrajectory(TankDriveSubsystem drive, Trajectory trajectory, RamseteController controller) {
        this.drive = drive;
        this.trajectory = trajectory;
        this.controller = controller;
        addRequirements(drive);
        setName("FollowTrajectory");
    }

    public FollowTrajectory(TankDriveSubsystem drive, Trajectory trajectory) {
        this(drive, trajectory, new RamseteController());
    }

    @Override
    public void initialize() {
        timer.restart();
    }

    @Override
    public void execute() {
        Trajectory.State goal = trajectory.sample(timer.get());
        RamseteController.Output out = controller.calculate(
                drive.getPose(), goal.pose, goal.velocity, goal.angularVelocity);

        double half = drive.getTrackWidthMeters() / 2.0;
        double leftSpeed = out.velocity - out.angularVelocity * half;
        double rightSpeed = out.velocity + out.angularVelocity * half;

        // Planned wheel accelerations, from the trajectory one sample ahead.
        Trajectory.State next = trajectory.sample(timer.get() + LOOKAHEAD);
        double leftAccel = ((next.velocity - next.angularVelocity * half) - (goal.velocity - goal.angularVelocity * half)) / LOOKAHEAD;
        double rightAccel = ((next.velocity + next.angularVelocity * half) - (goal.velocity + goal.angularVelocity * half)) / LOOKAHEAD;

        DriveGains g = drive.getGains();
        double leftVolts = g.feedforward(leftSpeed, leftAccel) + g.pathKP * (leftSpeed - drive.getLeftEncoderRate());
        double rightVolts = g.feedforward(rightSpeed, rightAccel) + g.pathKP * (rightSpeed - drive.getRightEncoderRate());
        drive.tankDriveVolts(MathUtil.clamp(leftVolts, -12, 12), MathUtil.clamp(rightVolts, -12, 12));

        Telemetry.put("path/t", timer.get());
        Telemetry.put("path/errorM", goal.pose.getTranslation().getDistance(drive.getPose().getTranslation()));
    }

    @Override
    public boolean isFinished() {
        return timer.hasElapsed(trajectory.getTotalTimeSeconds());
    }

    @Override
    public void end(boolean interrupted) {
        drive.stop();
    }
}
