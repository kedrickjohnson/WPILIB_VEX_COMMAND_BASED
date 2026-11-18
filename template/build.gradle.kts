plugins {
    id("dev.vexide.hydrozoa")
}

dependencies {
    implementation(project(":vexlib"))
}

val programName = "Robot"   // shown on the brain's screen
val programSlot = 1            // program slot on the brain, 1-8
val programIcon = "robot"     // hydrozoa CLI icon: robot, clawbot, cool-x, alien, alien-in-ufo, pizza...

hydrozoa {
    entrypoint = "com.robojacks.robot.Main"
    runtime = rootProject.layout.projectDirectory.file("hydrozoa.bin")
    slot = programSlot
}

// Builds, then uploads to the brain with the hydrozoa CLI (USB or controller radio). This is used
// instead of the plugin's own "upload" task, which doesn't pass the program name the CLI needs.
// --hash keeps this runtime separate from other Hydrozoa programs' runtimes on the brain.
tasks.register<Exec>("hydrozoaUpload") {
    group = "vex"
    description = "Builds the program and uploads it to the brain."
    dependsOn("assemble")
    val wasm = layout.buildDirectory.file("generated/teavm/wasm/robot.wasm")
    val runtimeBin = rootProject.layout.projectDirectory.file("hydrozoa.bin")
    commandLine("hydrozoa", "upload", wasm.get().asFile.path,
        "--name", programName, "--slot", programSlot.toString(), "--icon", programIcon,
        "--hash", "--runtime", runtimeBin.asFile.path)
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
