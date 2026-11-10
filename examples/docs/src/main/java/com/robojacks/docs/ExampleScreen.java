package com.robojacks.docs;

import com.robojacks.lib.hal.ScreenIO.TextSize;
import com.robojacks.lib.hardware.BrainScreen;
import com.robojacks.lib.robot.RobotController;

/** Drawing on the brain's screen. */
public final class ExampleScreen {
    private ExampleScreen() {}

    // [screen]
    private static long lastPercent = -1;

    /** Shows the battery as a bar. Call it from robotPeriodic(); it only redraws when the number changes. */
    public static void drawBattery() {
        long percent = Math.round(RobotController.getBatteryPercent());
        if (percent == lastPercent) {
            return; // drawing is slow on the brain: skip when nothing changed
        }
        lastPercent = percent;
        int color = percent > 50 ? BrainScreen.kGreen : percent > 25 ? BrainScreen.kYellow : BrainScreen.kRed;
        BrainScreen.fillRect(0, 200, BrainScreen.kWidth, 40, BrainScreen.kBlack);
        BrainScreen.drawRect(10, 208, 300, 24, BrainScreen.kWhite);
        BrainScreen.fillRect(12, 210, (int) (296 * percent / 100), 20, color);
        BrainScreen.drawText(324, 210, percent + "%", TextSize.MEDIUM, BrainScreen.kWhite, BrainScreen.kBlack);
    }
    // [/screen]
}
