package com.robojacks.lib.robot;

import com.robojacks.lib.command.CommandScheduler;
import com.robojacks.lib.hal.Clock;
import com.robojacks.lib.hal.CompetitionIO;
import com.robojacks.lib.hal.HAL;

/**
 * The VEX equivalent of WPILib's TimedRobot. Override the same lifecycle methods.
 *
 * <p>Modes come from field control (or the brain's competition switch):
 * disabled -> autonomous -> teleop (VEX "driver control").
 * Each loop runs the current mode's periodic, then robotPeriodic().
 */
public abstract class VexRobot {
    /** Loop period in seconds. V5 motors update about every 10 ms, so 10 ms is a good default. */
    public static final double kDefaultPeriod = 0.010;

    public enum Mode { DISABLED, AUTONOMOUS, TELEOP }

    private final double period;
    private Mode lastMode;

    protected VexRobot(double periodSeconds) {
        this.period = periodSeconds;
    }

    protected VexRobot() {
        this(kDefaultPeriod);
    }

    public double getPeriod() {
        return period;
    }

    // ----- Lifecycle hooks (same names as TimedRobot) -----
    public void robotInit() {}
    public void robotPeriodic() {}
    public void disabledInit() {}
    public void disabledPeriodic() {}
    public void disabledExit() {}
    public void autonomousInit() {}
    public void autonomousPeriodic() {}
    public void autonomousExit() {}
    public void teleopInit() {}
    public void teleopPeriodic() {}
    public void teleopExit() {}

    public static boolean isEnabled() {
        return HAL.get().competition().isEnabled();
    }

    public static boolean isAutonomous() {
        return HAL.get().competition().isAutonomous();
    }

    private Mode currentMode() {
        CompetitionIO comp = HAL.get().competition();
        if (!comp.isEnabled()) {
            return Mode.DISABLED;
        }
        return comp.isAutonomous() ? Mode.AUTONOMOUS : Mode.TELEOP;
    }

    /** One loop iteration. Public so a simulator or test can step it manually. */
    public final void loopFunc() {
        Mode mode = currentMode();

        if (mode != lastMode) {
            if (lastMode != null) {
                switch (lastMode) {
                    case DISABLED: disabledExit(); break;
                    case AUTONOMOUS: autonomousExit(); break;
                    case TELEOP: teleopExit(); break;
                }
            }
            CommandScheduler.getInstance().setRobotEnabled(mode != Mode.DISABLED);
            switch (mode) {
                case DISABLED: disabledInit(); break;
                case AUTONOMOUS: autonomousInit(); break;
                case TELEOP: teleopInit(); break;
            }
            lastMode = mode;
        }

        switch (mode) {
            case DISABLED: disabledPeriodic(); break;
            case AUTONOMOUS: autonomousPeriodic(); break;
            case TELEOP: teleopPeriodic(); break;
        }
        robotPeriodic();
    }

    /**
     * Runs the robot forever (or until {@code maxSeconds} in sim). Call from main()
     * after {@code HAL.set(...)}.
     */
    public static void startRobot(VexRobot robot, double maxSeconds) {
        Clock clock = HAL.get().clock();
        robot.robotInit();
        double start = clock.nowSeconds();
        double next = start;
        while (clock.nowSeconds() - start < maxSeconds) {
            robot.loopFunc();
            next += robot.period;
            // If a loop overran, don't try to "catch up" with a burst of loops.
            if (next < clock.nowSeconds()) {
                next = clock.nowSeconds();
            }
            clock.sleepUntil(next);
        }
    }

    public static void startRobot(VexRobot robot) {
        startRobot(robot, Double.POSITIVE_INFINITY);
    }
}
