package com.robojacks.lib.robot;

import com.robojacks.lib.hal.HAL;

/** Stopwatch with the same API as WPILib's Timer. */
public class Timer {
    private double startTime;
    private double accumulated;
    private boolean running;

    /** Seconds since program start (like Timer.getFPGATimestamp()). */
    public static double getTimestamp() {
        return HAL.now();
    }

    public double get() {
        return running ? accumulated + (getTimestamp() - startTime) : accumulated;
    }

    public void reset() {
        accumulated = 0.0;
        startTime = getTimestamp();
    }

    public void start() {
        if (!running) {
            startTime = getTimestamp();
            running = true;
        }
    }

    public void restart() {
        reset();
        running = false;
        start();
    }

    public void stop() {
        accumulated = get();
        running = false;
    }

    public boolean hasElapsed(double seconds) {
        return get() >= seconds;
    }

    public boolean isRunning() {
        return running;
    }
}
