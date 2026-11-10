package com.robojacks.docs;

import static com.robojacks.docs.ExampleConstants.ArmConstants.*;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.SubsystemBase;
import com.robojacks.lib.controller.ArmFeedforward;
import com.robojacks.lib.controller.ProfiledPIDController;
import com.robojacks.lib.hardware.VexMotor;
import com.robojacks.lib.hardware.VexMotorConfig;
import com.robojacks.lib.hardware.VexMotorConfig.IdleMode;
import com.robojacks.lib.hardware.VexRotationSensor;
import com.robojacks.lib.math.util.Units;
import com.robojacks.lib.telemetry.Telemetry;
import com.robojacks.lib.trajectory.TrapezoidProfile;

// [arm]
/**
 * An arm on a Rotation Sensor, moved with a motion profile plus gravity feedforward.
 * Angles are radians from horizontal: 0 = straight out, pi/2 = straight up.
 */
public class ExampleArm extends SubsystemBase {
    private final VexMotor m_motor = new VexMotor(kMotorPort, kCartridge);
    private final VexRotationSensor m_angleSensor = new VexRotationSensor(kRotationSensorPort);

    private final ProfiledPIDController m_controller = new ProfiledPIDController(
            kP, 0.0, kD, new TrapezoidProfile.Constraints(kMaxVelocity, kMaxAcceleration));
    private final ArmFeedforward m_feedforward = new ArmFeedforward(kS, kG, kV);

    public ExampleArm() {
        super("Arm");
        VexMotorConfig config = new VexMotorConfig();
        config.idleMode(IdleMode.kBrake).smartCurrentLimit(2.5);
        m_motor.configure(config);
        // The sensor is on the arm's axle, so one sensor rotation is one arm rotation.
        m_angleSensor.setPositionConversionFactor(2 * Math.PI);
        m_angleSensor.setVelocityConversionFactor(2 * Math.PI);
        m_controller.setTolerance(Units.degreesToRadians(2));
    }

    public double getAngleRadians() {
        return m_angleSensor.getPosition();
    }

    @Override
    public void periodic() {
        Telemetry.put("arm/degrees", Units.radiansToDegrees(getAngleRadians()));
        Telemetry.put("arm/goalDegrees", Units.radiansToDegrees(m_controller.getGoal().position));
    }

    /** Moves to {@code goalRadians} along a smooth profile, then holds there until interrupted. */
    public Command goToAngleCommand(double goalRadians) {
        return startRun(
                () -> m_controller.reset(getAngleRadians(), m_angleSensor.getVelocity()),
                () -> {
                    double pid = m_controller.calculate(getAngleRadians(), goalRadians);
                    double ff = m_feedforward.calculate(
                            m_controller.getSetpoint().position, m_controller.getSetpoint().velocity);
                    m_motor.setVoltage(pid + ff);
                })
                .withName("ArmTo" + Math.round(Units.radiansToDegrees(goalRadians)));
    }

    public boolean atGoal() {
        return m_controller.atGoal();
    }
}
// [/arm]
