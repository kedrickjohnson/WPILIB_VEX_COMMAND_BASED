package com.robojacks.lib.input;

import com.robojacks.lib.command.Trigger;
import com.robojacks.lib.hal.GamepadIO;

/**
 * Like WPILib's CommandXboxController, for the V5 controller.
 *
 * <p>NOTE: axes keep VEX's signs: stick UP is POSITIVE (FRC joysticks are the opposite),
 * stick RIGHT is positive. So "forward = getLeftY()" with no minus sign.
 */
public class VexController {
    private final GamepadIO io;

    public VexController(GamepadIO io) {
        this.io = io;
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
