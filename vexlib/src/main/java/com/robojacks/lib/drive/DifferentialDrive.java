package com.robojacks.lib.drive;

import com.robojacks.lib.hardware.VexMotor;
import com.robojacks.lib.hardware.VexMotorGroup;
import com.robojacks.lib.math.MathUtil;

/**
 * Arcade/tank drive helper, same methods as WPILib's DifferentialDrive.
 * zRotation is counter-clockwise positive (WPILib 2023+ convention).
 */
public class DifferentialDrive {
    private final VexMotorGroup left;
    private final VexMotorGroup right;
    private double deadband = 0.02;
    private double maxOutput = 1.0;

    public DifferentialDrive(VexMotorGroup left, VexMotorGroup right) {
        this.left = left;
        this.right = right;
    }

    /** For a two-motor drivetrain (one motor per side), like the Clawbot. */
    public DifferentialDrive(VexMotor left, VexMotor right) {
        this(new VexMotorGroup(left), new VexMotorGroup(right));
    }

    public void setDeadband(double deadband) {
        this.deadband = deadband;
    }

    /** Scales all output, e.g. 0.5 for a slow mode. */
    public void setMaxOutput(double maxOutput) {
        this.maxOutput = maxOutput;
    }

    public void arcadeDrive(double xSpeed, double zRotation) {
        arcadeDrive(xSpeed, zRotation, true);
    }

    /**
     * @param xSpeed       forward, -1..1
     * @param zRotation    counter-clockwise rotation, -1..1
     * @param squareInputs square the inputs for finer control at low speed
     */
    public void arcadeDrive(double xSpeed, double zRotation, boolean squareInputs) {
        xSpeed = MathUtil.applyDeadband(MathUtil.clamp(xSpeed, -1, 1), deadband);
        zRotation = MathUtil.applyDeadband(MathUtil.clamp(zRotation, -1, 1), deadband);
        if (squareInputs) {
            xSpeed = Math.copySign(xSpeed * xSpeed, xSpeed);
            zRotation = Math.copySign(zRotation * zRotation, zRotation);
        }
        double l = xSpeed - zRotation;
        double r = xSpeed + zRotation;
        double greater = Math.max(Math.abs(xSpeed), Math.abs(zRotation));
        double lesser = Math.min(Math.abs(xSpeed), Math.abs(zRotation));
        if (greater > 0.0) {
            double saturated = (greater + lesser) / greater;
            l /= saturated;
            r /= saturated;
        }
        left.set(l * maxOutput);
        right.set(r * maxOutput);
    }

    public void tankDrive(double leftSpeed, double rightSpeed) {
        tankDrive(leftSpeed, rightSpeed, true);
    }

    public void tankDrive(double leftSpeed, double rightSpeed, boolean squareInputs) {
        leftSpeed = MathUtil.applyDeadband(MathUtil.clamp(leftSpeed, -1, 1), deadband);
        rightSpeed = MathUtil.applyDeadband(MathUtil.clamp(rightSpeed, -1, 1), deadband);
        if (squareInputs) {
            leftSpeed = Math.copySign(leftSpeed * leftSpeed, leftSpeed);
            rightSpeed = Math.copySign(rightSpeed * rightSpeed, rightSpeed);
        }
        left.set(leftSpeed * maxOutput);
        right.set(rightSpeed * maxOutput);
    }

    public void stopMotor() {
        left.stopMotor();
        right.stopMotor();
    }
}
