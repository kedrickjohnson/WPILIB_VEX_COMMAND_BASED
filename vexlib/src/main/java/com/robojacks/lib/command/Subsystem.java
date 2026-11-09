package com.robojacks.lib.command;

/** Same idea as WPILib's Subsystem: owns hardware, at most one command uses it at a time. */
public interface Subsystem {
    /** Runs every loop, before commands execute. */
    default void periodic() {}

    default String getName() {
        return "Subsystem";
    }

    default void register() {
        CommandScheduler.getInstance().registerSubsystem(this);
    }

    default void setDefaultCommand(Command command) {
        CommandScheduler.getInstance().setDefaultCommand(this, command);
    }

    default void removeDefaultCommand() {
        CommandScheduler.getInstance().removeDefaultCommand(this);
    }

    default Command getDefaultCommand() {
        return CommandScheduler.getInstance().getDefaultCommand(this);
    }

    default Command getCurrentCommand() {
        return CommandScheduler.getInstance().requiring(this);
    }

    // ----- Command factories (same names as WPILib) -----

    default Command runOnce(Runnable action) {
        return Commands.runOnce(action, this);
    }

    default Command run(Runnable action) {
        return Commands.run(action, this);
    }

    default Command startEnd(Runnable start, Runnable end) {
        return Commands.startEnd(start, end, this);
    }

    default Command runEnd(Runnable run, Runnable end) {
        return Commands.runEnd(run, end, this);
    }
}
