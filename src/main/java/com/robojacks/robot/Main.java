package com.robojacks.robot;

import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.hal.hydrozoa.HydrozoaHardware;
import com.robojacks.lib.robot.VexRobot;

/** Brain entry point. Hydrozoa runs this; see README for how it's wired into Gradle. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        HAL.set(new HydrozoaHardware());
        VexRobot.startRobot(new Robot());
    }
}
