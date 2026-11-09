pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "vex-command-template"

include("vexlib")              // shared library: commands, math, drive, hardware layer
include("template")            // starting point for your own robot
include("examples:clawbot")    // complete V5 Clawbot
