plugins {
    java
    // Builds the program and uploads it to the brain (needs the Hydrozoa CLI installed).
    id("dev.vexide.hydrozoa") version "0.1.0-alpha.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.vexide:hydrozoa:0.1.0-alpha.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// TODO: if the Hydrozoa plugin needs the entry class configured, point it at
// com.robojacks.robot.Main. Copy the exact block from vexide/Hydrozoa-Java-Template.

// ---------------------------------------------------------------------------
// Desktop simulator: same robot code, fake hardware. Never uploaded to the brain.
// ---------------------------------------------------------------------------
sourceSets {
    create("sim") {
        java.srcDir("src/sim/java")
        compileClasspath += sourceSets["main"].output + sourceSets["main"].compileClasspath
        runtimeClasspath += sourceSets["main"].output + sourceSets["main"].runtimeClasspath
    }
}

tasks.register<JavaExec>("simulate") {
    group = "application"
    description = "Runs the robot code in a desktop simulator (disabled -> auto -> teleop)."
    classpath = sourceSets["sim"].runtimeClasspath
    mainClass.set("com.robojacks.sim.SimMain")
}
