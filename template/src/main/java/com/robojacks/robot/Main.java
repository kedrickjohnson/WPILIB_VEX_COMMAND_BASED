package com.robojacks.robot;

import com.robojacks.lib.hal.hydrozoa.HydrozoaRuntime;

/** Brain entry point (set as the Hydrozoa entrypoint in build.gradle.kts). */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        HydrozoaRuntime.start(Robot::new);
    }
}
