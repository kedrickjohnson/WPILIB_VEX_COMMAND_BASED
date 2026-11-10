package com.robojacks.lib.input;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.Commands;
import com.robojacks.lib.command.Trigger;
import com.robojacks.lib.hal.GamepadIO;
import com.robojacks.lib.hal.HAL;

/**
 * Like WPILib's CommandXboxController, for the V5 controller: buttons are {@link Trigger}s you
 * bind commands to, sticks are -1 to 1, and you can write to the controller's screen and rumble it.
 *
 * <pre>{@code
 * private final VexController m_driverController = new VexController(0);   // primary
 * private final VexController m_operatorController = new VexController(1); // partner
 *
 * m_driverController.r1().whileTrue(m_intake.runCommand());
 * m_driverController.axisMagnitudeGreaterThan(VexController.Axis.kRightX, 0.1)
 *         .whileTrue(m_turret.manualCommand(m_driverController::getRightX));
 * m_driverController.setText(0, 0, "Auto: " + m_autoSelector.getSelectedName());
 * }</pre>
 *
 * <p>NOTE: axes keep VEX's signs: stick UP is POSITIVE (FRC joysticks are the opposite),
 * stick RIGHT is positive. So "forward = getLeftY()" with no minus sign.
 */
public class VexController {
    /** The four stick axes. */
    public enum Axis {
        kLeftX(GamepadIO.AXIS_LEFT_X),
        kLeftY(GamepadIO.AXIS_LEFT_Y),
        kRightX(GamepadIO.AXIS_RIGHT_X),
        kRightY(GamepadIO.AXIS_RIGHT_Y);

        public final int value;

        Axis(int value) {
            this.value = value;
        }
    }

    private final GamepadIO io;

    public VexController(GamepadIO io) {
        this.io = io;
    }

    /** @param port 0 for the primary controller, 1 for the partner controller */
    public VexController(int port) {
        this(port == 0 ? HAL.get().primaryController() : HAL.get().partnerController());
        if (port != 0 && port != 1) {
            throw new IllegalArgumentException("Controller port must be 0 (primary) or 1 (partner)");
        }
    }

    /** -1 to 1. */
    public double getRawAxis(Axis axis) {
        return io.getAxis(axis.value);
    }

    /** True while the axis is above {@code threshold}. */
    public Trigger axisGreaterThan(Axis axis, double threshold) {
        return new Trigger(() -> io.getAxis(axis.value) > threshold);
    }

    /** True while the axis is below {@code threshold}. */
    public Trigger axisLessThan(Axis axis, double threshold) {
        return new Trigger(() -> io.getAxis(axis.value) < threshold);
    }

    /** True while the stick is pushed either way past {@code threshold}. */
    public Trigger axisMagnitudeGreaterThan(Axis axis, double threshold) {
        return new Trigger(() -> Math.abs(io.getAxis(axis.value)) > threshold);
    }

    /**
     * Writes text on the controller's screen. Line 0-2, column 0-18 (19 characters fit).
     * Updates are sent about every 50 ms, the fastest VEXos accepts; the latest text wins.
     */
    public void setText(int line, int column, String text) {
        io.setText(line, column, text);
    }

    /** Blanks one line of the controller's screen. */
    public void clearLine(int line) {
        io.setText(line, 0, "");
    }

    /** Blanks the whole controller screen. */
    public void clearScreen() {
        for (int i = 0; i < 3; i++) {
            io.setText(i, 0, "");
        }
    }

    /**
     * Rumbles the controller. Pattern: '.' short buzz, '-' long buzz, ' ' pause; up to 8
     * symbols, e.g. {@code "..-"}.
     */
    public void rumble(String pattern) {
        io.rumble(pattern);
    }

    /** A command that rumbles once; works while disabled too. */
    public Command rumbleCommand(String pattern) {
        return Commands.runOnce(() -> rumble(pattern)).ignoringDisable(true);
    }

    public double getLeftX() { return io.getAxis(GamepadIO.AXIS_LEFT_X); }
    public double getLeftY() { return io.getAxis(GamepadIO.AXIS_LEFT_Y); }
    public double getRightX() { return io.getAxis(GamepadIO.AXIS_RIGHT_X); }
    public double getRightY() { return io.getAxis(GamepadIO.AXIS_RIGHT_Y); }

    public Trigger button(int button) {
        return new Trigger(() -> io.getButton(button));
    }

    public Trigger a() { return button(GamepadIO.BUTTON_A); }
    public Trigger b() { return button(GamepadIO.BUTTON_B); }
    public Trigger x() { return button(GamepadIO.BUTTON_X); }
    public Trigger y() { return button(GamepadIO.BUTTON_Y); }
    public Trigger up() { return button(GamepadIO.BUTTON_UP); }
    public Trigger down() { return button(GamepadIO.BUTTON_DOWN); }
    public Trigger left() { return button(GamepadIO.BUTTON_LEFT); }
    public Trigger right() { return button(GamepadIO.BUTTON_RIGHT); }
    public Trigger l1() { return button(GamepadIO.BUTTON_L1); }
    public Trigger l2() { return button(GamepadIO.BUTTON_L2); }
    public Trigger r1() { return button(GamepadIO.BUTTON_R1); }
    public Trigger r2() { return button(GamepadIO.BUTTON_R2); }

    public boolean isConnected() {
        return io.isConnected();
    }
}
