package com.robojacks.docs;

import static com.robojacks.docs.ExampleConstants.IntakeConstants.*;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.SubsystemBase;
import com.robojacks.lib.command.Trigger;
import com.robojacks.lib.hardware.VexDistanceSensor;
import com.robojacks.lib.hardware.VexMotor;
import com.robojacks.lib.hardware.VexMotorConfig;
import com.robojacks.lib.hardware.VexMotorConfig.IdleMode;
import com.robojacks.lib.hardware.VexOpticalSensor;
import com.robojacks.lib.telemetry.Telemetry;

// [intake]
/** A roller intake with a distance sensor that sees a game piece and a color sensor that reads it. */
public class ExampleIntake extends SubsystemBase {
    private final VexMotor m_motor = new VexMotor(kMotorPort, kCartridge);
    private final VexDistanceSensor m_pieceSensor = new VexDistanceSensor(kDistancePort);
    private final VexOpticalSensor m_colorSensor = new VexOpticalSensor(kOpticalPort);

    /** True while a game piece is in the intake. Use it to trigger commands. */
    public final Trigger hasPiece = new Trigger(this::hasPiece).debounce(0.05);

    public ExampleIntake() {
        super("Intake");
        VexMotorConfig config = new VexMotorConfig();
        config.idleMode(IdleMode.kCoast).smartCurrentLimit(2.0);
        m_motor.configure(config);
        m_colorSensor.setLedPower(1.0); // consistent color readings under any field lighting
    }

    @Override
    public void periodic() {
        Telemetry.put("intake/hasPiece", hasPiece());
        Telemetry.put("intake/hue", m_colorSensor.getHue());
    }

    public boolean hasPiece() {
        return m_pieceSensor.isWithin(kPieceDistanceMeters);
    }

    public boolean pieceIsRed() {
        return m_colorSensor.isColor(VexOpticalSensor.kRedHue, 25);
    }

    /** Runs the rollers in until a piece arrives (or the command is interrupted). */
    public Command intakeCommand() {
        return startEnd(() -> m_motor.setVoltage(kIntakeVolts), m_motor::stopMotor)
                .until(this::hasPiece)
                .withName("Intake");
    }

    /** Spits the piece out for half a second. */
    public Command ejectCommand() {
        return startEnd(() -> m_motor.setVoltage(-kIntakeVolts), m_motor::stopMotor)
                .withTimeout(0.5)
                .withName("Eject");
    }
}
// [/intake]
