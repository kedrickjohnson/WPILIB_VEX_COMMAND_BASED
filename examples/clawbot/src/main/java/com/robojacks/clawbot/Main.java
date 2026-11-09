package com.robojacks.clawbot;

import com.robojacks.lib.hal.hydrozoa.HydrozoaRuntime;

/** Brain entry point (the Hydrozoa entrypoint in build.gradle.kts). */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        HydrozoaRuntime.start(Robot::new);
    }
}
