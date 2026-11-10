package com.robojacks.lib.hal;

/**
 * The brain's touch screen. Drawing area is {@link #WIDTH} x {@link #HEIGHT} pixels below
 * VEXos's status bar; (0, 0) is the top left. Colors are 0xRRGGBB.
 */
public interface ScreenIO {
    int WIDTH = 480;
    int HEIGHT = 240;

    /** Text sizes VEXos has fonts for. */
    enum TextSize { SMALL, MEDIUM, LARGE }

    void clear(int color);

    void fillRect(int x, int y, int width, int height, int color);

    void drawRect(int x, int y, int width, int height, int color);

    void fillCircle(int centerX, int centerY, int radius, int color);

    /** Draws text with its top-left corner at (x, y) on an opaque background. */
    void drawText(int x, int y, String text, TextSize size, int color, int background);

    /** Width in pixels the text would take. */
    int textWidth(String text, TextSize size);

    /** The latest touch: whether the screen is pressed and where. */
    TouchState getTouch();

    /** A touch reading. */
    final class TouchState {
        public final boolean pressed;
        public final int x;
        public final int y;
        /** Counts presses since the program started; changes on each new press. */
        public final int pressCount;

        public TouchState(boolean pressed, int x, int y, int pressCount) {
            this.pressed = pressed;
            this.x = x;
            this.y = y;
            this.pressCount = pressCount;
        }
    }
}
