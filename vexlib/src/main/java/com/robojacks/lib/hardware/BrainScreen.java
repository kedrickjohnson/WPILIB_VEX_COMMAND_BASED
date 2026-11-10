package com.robojacks.lib.hardware;

import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.hal.ScreenIO;

/**
 * Draws on the brain's touch screen. The drawing area is 480 x 240 pixels, (0, 0) at the top
 * left, colors as 0xRRGGBB. Drawing is slow in Hydrozoa's interpreter, so draw when something
 * changes, not every loop. {@code AutoSelector} uses this to show its menu.
 *
 * <pre>{@code
 * BrainScreen.clear(BrainScreen.kBlack);
 * BrainScreen.print(1, "Battery " + Math.round(RobotController.getBatteryPercent()) + "%");
 * }</pre>
 */
public final class BrainScreen {
    public static final int kWidth = ScreenIO.WIDTH;
    public static final int kHeight = ScreenIO.HEIGHT;

    public static final int kBlack = 0x000000;
    public static final int kWhite = 0xFFFFFF;
    public static final int kRed = 0xD7263D;
    public static final int kBlue = 0x1B6CA8;
    public static final int kGreen = 0x2BA84A;
    public static final int kYellow = 0xF2C14E;
    public static final int kGray = 0x3A3F44;

    /** Height of one line for {@link #print}. */
    public static final int kLineHeight = 24;

    private BrainScreen() {}

    private static ScreenIO io() {
        return HAL.get().screen();
    }

    public static void clear(int color) {
        io().clear(color);
    }

    public static void fillRect(int x, int y, int width, int height, int color) {
        io().fillRect(x, y, width, height, color);
    }

    public static void drawRect(int x, int y, int width, int height, int color) {
        io().drawRect(x, y, width, height, color);
    }

    public static void fillCircle(int x, int y, int radius, int color) {
        io().fillCircle(x, y, radius, color);
    }

    public static void drawText(int x, int y, String text, ScreenIO.TextSize size, int color, int background) {
        io().drawText(x, y, text, size, color, background);
    }

    /** Writes a line of white text on black. Line 0 is the top; 10 lines fit. */
    public static void print(int line, String text) {
        io().fillRect(0, line * kLineHeight, kWidth, kLineHeight, kBlack);
        io().drawText(8, line * kLineHeight + 2, text, ScreenIO.TextSize.MEDIUM, kWhite, kBlack);
    }

    public static int textWidth(String text, ScreenIO.TextSize size) {
        return io().textWidth(text, size);
    }

    public static boolean isPressed() {
        return io().getTouch().pressed;
    }

    public static ScreenIO.TouchState getTouch() {
        return io().getTouch();
    }
}
