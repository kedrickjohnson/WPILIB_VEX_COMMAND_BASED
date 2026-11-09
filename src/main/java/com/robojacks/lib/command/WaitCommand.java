package com.robojacks.lib.command;

import com.robojacks.lib.robot.Timer;

/** Finishes after a fixed time. */
public class WaitCommand extends Command {
    private final double seconds;
    private final Timer timer = new Timer();

    public WaitCommand(double seconds) {
        this.seconds = seconds;
        setName("Wait(" + seconds + ")");
    }

    @Override
    public void initialize() {
        timer.restart();
    }

    @Override
    public boolean isFinished() {
        return timer.hasElapsed(seconds);
    }

    @Override
    public void end(boolean interrupted) {
        timer.stop();
    }

    @Override
    public boolean runsWhenDisabled() {
        return true;
    }
}
