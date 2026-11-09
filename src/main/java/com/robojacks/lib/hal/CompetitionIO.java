package com.robojacks.lib.hal;

/** Field-control state. Equivalent of FRC's DriverStation mode checks. */
public interface CompetitionIO {
    boolean isEnabled();

    boolean isAutonomous();

    default boolean isConnectedToField() {
        return false;
    }
}
