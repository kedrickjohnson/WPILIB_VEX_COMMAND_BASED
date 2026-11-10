package com.robojacks.lib.hal;

/** Raw V5 controller access. Axes are -1..1 with VEX's native signs (stick up = +, right = +). */
public interface GamepadIO {
    int AXIS_LEFT_X = 0;   // VEX Axis4
    int AXIS_LEFT_Y = 1;   // VEX Axis3
    int AXIS_RIGHT_X = 2;  // VEX Axis1
    int AXIS_RIGHT_Y = 3;  // VEX Axis2

    int BUTTON_A = 0;
    int BUTTON_B = 1;
    int BUTTON_X = 2;
    int BUTTON_Y = 3;
    int BUTTON_UP = 4;
    int BUTTON_DOWN = 5;
    int BUTTON_LEFT = 6;
    int BUTTON_RIGHT = 7;
    int BUTTON_L1 = 8;
    int BUTTON_L2 = 9;
    int BUTTON_R1 = 10;
    int BUTTON_R2 = 11;

    double getAxis(int axis);

    boolean getButton(int button);

    default boolean isConnected() {
        return true;
    }

    /**
     * Shows text on the controller's screen: line 0-2, column 0-18. VEXos accepts one screen
     * update about every 50 ms, so the hardware layer queues updates and sends the latest.
     */
    default void setText(int line, int column, String text) {}

    /** Rumbles the controller: '.' short, '-' long, ' ' pause, e.g. ".-." (up to 8 symbols). */
    default void rumble(String pattern) {}
}
