# Changelog

## 0.3.1

- Fixes builds on a real machine: new projects use VexLib 0.1.1, which brings the Hydrozoa build it
  needs (Hydrozoa's main branch, installed offline with VexLib). 0.1.0 pulled in the old Hydrozoa
  release from Maven Central and failed in `generateWasm`
- Build files work with the hydrozoa Gradle plugin that's actually on the plugin portal (no
  `name`/`icon`/`ProgramIcon`); `hydrozoaUpload` is now a task in the build file that passes the
  program name, slot and icon to the hydrozoa CLI
- Projects include `gradle/gradle-daemon-jvm.properties`, so Gradle runs on Java 21 even when
  `JAVA_HOME` points at an older Java
- Links point to the WPILIB_VEX_COMMAND_BASED repository

## 0.3.0

- New projects include the latest VexLib: smart sensors, 3-wire and pneumatics, GPS vision,
  auto selector, motion profiles, filters, and the dashboard telemetry format
- The template's RobotContainer has an AutoSelector and a Vision subsystem (turned on by setting
  a GPS port)

## 0.2.0

- VexLib is now a vendor library: new projects list it in `vendordeps/VexLib.json` instead of
  copying its source
- VEX: Manage Vendor Libraries: install (bundled, online URL, or file), check for updates, remove
- Bundled VexLib is copied to `~/.vex-command/maven` so builds work offline

## 0.1.0

- VEX: Create a New Project, from the command-based template or the V5 Clawbot
- Build, Simulate, Simulate Tuning Tests and Upload, as commands, status bar buttons and `vex` tasks
- Wizards: New Subsystem, New Subsystem with a Motor, New Command, New Auto
- Java snippets for bindings, commands and motor configs
