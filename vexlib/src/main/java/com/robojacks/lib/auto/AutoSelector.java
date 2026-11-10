package com.robojacks.lib.auto;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.Commands;
import com.robojacks.lib.command.SubsystemBase;
import com.robojacks.lib.command.Trigger;
import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.hal.ScreenIO;
import com.robojacks.lib.input.VexController;
import java.util.ArrayList;
import java.util.List;

/**
 * Picks the autonomous routine from a touch menu on the brain's screen: VEX's version of
 * WPILib's {@code SendableChooser<Command>} on the dashboard.
 *
 * <pre>{@code
 * private final AutoSelector m_autoSelector = new AutoSelector();
 *
 * public RobotContainer() {
 *     m_autoSelector.setDefaultOption("Do nothing", Commands.none());
 *     m_autoSelector.addOption("Left side", Autos.leftSide(m_robotDrive, m_intake));
 *     m_autoSelector.addOption("Right side", Autos.rightSide(m_robotDrive, m_intake));
 *     m_autoSelector.addControllerBindings(m_driverController); // D-pad left/right while disabled
 * }
 *
 * public Command getAutonomousCommand() {
 *     return m_autoSelector.getSelected();
 * }
 * }</pre>
 *
 * <p>The menu shows six buttons per page (arrows appear when there are more). Tap one to select
 * it; the selection turns green and shows at the top. Selection is locked during autonomous so a
 * bump of the screen can't change it mid-run. The menu only redraws when something changes, since
 * drawing is slow on the brain.
 */
public class AutoSelector extends SubsystemBase {
    private static final int HEADER_H = 40;
    private static final int COLS = 2;
    private static final int ROWS = 3;
    private static final int PER_PAGE = COLS * ROWS;
    private static final int GAP = 8;
    private static final int NAV_W = 40;

    private static final int BG = 0x101418;
    private static final int BUTTON = 0x2C333A;
    private static final int SELECTED = 0x2BA84A;
    private static final int TEXT = 0xFFFFFF;
    private static final int MUTED = 0x9AA5B1;

    private final String title;
    private final List<String> names = new ArrayList<>();
    private final List<Command> commands = new ArrayList<>();
    private int selected = -1;
    private int page;
    private boolean dirty = true;
    private boolean drawEnabled = true;
    private int lastPressCount = -1;
    private VexController controller;
    private int controllerLine = -1;

    public AutoSelector() {
        this("Autonomous");
    }

    /** @param title shown at the top of the screen */
    public AutoSelector(String title) {
        super("AutoSelector");
        this.title = title;
    }

    /** Adds an option and makes it the default. */
    public AutoSelector setDefaultOption(String name, Command command) {
        addOption(name, command);
        selected = names.size() - 1;
        page = selected / PER_PAGE;
        return this;
    }

    public AutoSelector addOption(String name, Command command) {
        if (names.contains(name)) {
            throw new IllegalArgumentException("Auto \"" + name + "\" was added twice");
        }
        names.add(name);
        commands.add(command);
        if (selected < 0) {
            selected = 0;
        }
        dirty = true;
        return this;
    }

    /** The chosen auto's command, or a do-nothing command if there are no options. */
    public Command getSelected() {
        return selected < 0 ? Commands.none() : commands.get(selected);
    }

    public String getSelectedName() {
        return selected < 0 ? "(none)" : names.get(selected);
    }

    /** Selects by name (e.g. from a saved setting). Returns false if there's no such option. */
    public boolean select(String name) {
        int i = names.indexOf(name);
        if (i < 0) {
            return false;
        }
        setSelected(i);
        return true;
    }

    public List<String> getOptionNames() {
        return new ArrayList<>(names);
    }

    /**
     * Lets the driver change the auto from the controller too: D-pad left/right cycles while the
     * robot is disabled, and the choice shows on the controller's screen (line 0).
     */
    public void addControllerBindings(VexController controller) {
        this.controller = controller;
        controllerLine = 0;
        Trigger disabled = new Trigger(() -> !HAL.get().competition().isEnabled());
        controller.left().and(disabled).onTrue(Commands.runOnce(() -> step(-1)).ignoringDisable(true));
        controller.right().and(disabled).onTrue(Commands.runOnce(() -> step(1)).ignoringDisable(true));
        showOnController();
    }

    /** Stops drawing (e.g. if another part of your code uses the screen during the match). */
    public void setScreenEnabled(boolean enabled) {
        drawEnabled = enabled;
        dirty = true;
    }

    private void step(int delta) {
        if (names.isEmpty() || locked()) {
            return;
        }
        setSelected((selected + delta + names.size()) % names.size());
    }

    private void setSelected(int index) {
        if (index != selected) {
            selected = index;
            page = index / PER_PAGE;
            dirty = true;
            showOnController();
        }
    }

    private void showOnController() {
        if (controller != null && controllerLine >= 0) {
            controller.setText(controllerLine, 0, "Auto: " + getSelectedName());
        }
    }

    private boolean locked() {
        return HAL.get().competition().isEnabled() && HAL.get().competition().isAutonomous();
    }

    private int pages() {
        return Math.max(1, (names.size() + PER_PAGE - 1) / PER_PAGE);
    }

    @Override
    public void periodic() {
        if (!drawEnabled) {
            return;
        }
        ScreenIO screen = HAL.get().screen();
        handleTouch(screen.getTouch());
        if (dirty) {
            draw(screen);
            dirty = false;
        }
    }

    private void handleTouch(ScreenIO.TouchState touch) {
        if (lastPressCount < 0) {
            lastPressCount = touch.pressCount; // ignore a press left over from before
            return;
        }
        if (touch.pressCount == lastPressCount || !touch.pressed) {
            return;
        }
        lastPressCount = touch.pressCount;
        if (locked()) {
            return;
        }
        int x = touch.x;
        int y = touch.y;
        if (y < HEADER_H) {
            return;
        }
        boolean paged = pages() > 1;
        if (paged && x < NAV_W) {
            page = (page - 1 + pages()) % pages();
            dirty = true;
            return;
        }
        if (paged && x >= ScreenIO.WIDTH - NAV_W) {
            page = (page + 1) % pages();
            dirty = true;
            return;
        }
        int index = hit(x, y, paged);
        if (index >= 0) {
            setSelected(index);
        }
    }

    private int left(boolean paged) {
        return paged ? NAV_W : GAP;
    }

    private int buttonWidth(boolean paged) {
        int area = ScreenIO.WIDTH - 2 * left(paged);
        return (area - GAP * (COLS - 1)) / COLS;
    }

    private int buttonHeight() {
        return (ScreenIO.HEIGHT - HEADER_H - GAP * (ROWS + 1)) / ROWS;
    }

    private int hit(int x, int y, boolean paged) {
        int w = buttonWidth(paged);
        int h = buttonHeight();
        for (int slot = 0; slot < PER_PAGE; slot++) {
            int index = page * PER_PAGE + slot;
            if (index >= names.size()) {
                break;
            }
            int bx = left(paged) + (slot % COLS) * (w + GAP);
            int by = HEADER_H + GAP + (slot / COLS) * (h + GAP);
            if (x >= bx && x < bx + w && y >= by && y < by + h) {
                return index;
            }
        }
        return -1;
    }

    private void draw(ScreenIO s) {
        s.clear(BG);
        s.drawText(10, 10, title + ":", ScreenIO.TextSize.MEDIUM, MUTED, BG);
        int titleW = s.textWidth(title + ": ", ScreenIO.TextSize.MEDIUM);
        s.drawText(10 + titleW, 10, getSelectedName(), ScreenIO.TextSize.MEDIUM, SELECTED, BG);
        boolean paged = pages() > 1;
        int w = buttonWidth(paged);
        int h = buttonHeight();
        for (int slot = 0; slot < PER_PAGE; slot++) {
            int index = page * PER_PAGE + slot;
            if (index >= names.size()) {
                break;
            }
            int bx = left(paged) + (slot % COLS) * (w + GAP);
            int by = HEADER_H + GAP + (slot / COLS) * (h + GAP);
            int color = index == selected ? SELECTED : BUTTON;
            s.fillRect(bx, by, w, h, color);
            String label = fit(s, names.get(index), w - 16);
            int tw = s.textWidth(label, ScreenIO.TextSize.MEDIUM);
            s.drawText(bx + (w - tw) / 2, by + h / 2 - 9, label, ScreenIO.TextSize.MEDIUM, TEXT, color);
        }
        if (paged) {
            int mid = HEADER_H + (ScreenIO.HEIGHT - HEADER_H) / 2 - 10;
            s.drawText(12, mid, "<", ScreenIO.TextSize.LARGE, TEXT, BG);
            s.drawText(ScreenIO.WIDTH - 28, mid, ">", ScreenIO.TextSize.LARGE, TEXT, BG);
            String p = (page + 1) + "/" + pages();
            s.drawText(ScreenIO.WIDTH - 10 - s.textWidth(p, ScreenIO.TextSize.SMALL), 14, p, ScreenIO.TextSize.SMALL, MUTED, BG);
        }
    }

    private static String fit(ScreenIO s, String text, int maxWidth) {
        if (s.textWidth(text, ScreenIO.TextSize.MEDIUM) <= maxWidth) {
            return text;
        }
        String t = text;
        while (t.length() > 1 && s.textWidth(t + "...", ScreenIO.TextSize.MEDIUM) > maxWidth) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "...";
    }
}
