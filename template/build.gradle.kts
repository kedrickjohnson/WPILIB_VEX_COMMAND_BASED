import dev.vexide.hydrozoa.gradle.ProgramIcon

plugins {
    id("dev.vexide.hydrozoa")
}

dependencies {
    implementation(project(":vexlib"))
}

hydrozoa {
    entrypoint = "com.robojacks.robot.Main"
    runtime = rootProject.layout.projectDirectory.file("hydrozoa.bin")
    name = "Robot"
    slot = 1
    icon = ProgramIcon.ROBOT
}

// Desktop simulator: same robot code, fake hardware. Never uploaded to the brain.
sourceSets {
    create("sim") {
        java.srcDir("src/sim/java")
        compileClasspath += sourceSets["main"].output + sourceSets["main"].compileClasspath
        runtimeClasspath += sourceSets["main"].output + sourceSets["main"].runtimeClasspath
    }
}

tasks.register<JavaExec>("simulate") {
    group = "application"
    description = "Runs the robot code in a desktop simulator. --args=tune runs the tuning tests."
    classpath = sourceSets["sim"].runtimeClasspath
    mainClass.set("com.robojacks.robot.SimMain")
}
