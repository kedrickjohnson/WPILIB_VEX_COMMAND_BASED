# VEX Command-Based (Java)

FRC-style command-based programming for VEX V5, in Java, running on the brain through
[Hydrozoa](https://github.com/vexide/Hydrozoa-Java). If you know WPILib command-based, you
already know how to use this: `Robot`, `RobotContainer`, `Constants`, subsystems that own their
motors, command factories, triggers, `Pose2d` and odometry.

| Project | What it is |
| --- | --- |
| `vexlib/` | The shared library, VexLib ("WPILib-lite"), published as a vendor library. Teams don't edit it. |
| `template/` | Starting point for your own robot: 6-motor tank drive + example intake. |
| `examples/clawbot/` | A complete V5 Clawbot: drive, arm, claw, autos. |
| `examples/docs/` | The code quoted in the documentation, compiled and run in CI. |
| `docs/` | The website: guides, the path planner, and the API reference. |
| `tools/` | `release_vexlib.py`: publishes VexLib without Gradle. |
| `vscode-extension/` | VS Code extension: new projects, Build / Simulate / Upload buttons, class wizards. |

## What's included

| Area | Classes |
| --- | --- |
| Robot and commands | `VexRobot`, `Command`, `Commands`, `SubsystemBase`, `CommandScheduler`, all the WPILib decorators |
| Controllers and triggers | `VexController` (screen text, rumble, axis triggers), `Trigger` (debounce, rising/falling, toggles), `RobotModeTriggers` |
| Motors | `VexMotor`, `VexMotorConfig` (REV-style), `VexEncoder`, `VexMotorGroup` |
| Smart sensors | `VexInertial`, `VexRotationSensor`, `VexDistanceSensor`, `VexOpticalSensor`, `VexGps` |
| 3-wire and pneumatics | `DigitalInput`, `DigitalOutput`, `Solenoid`, `DoubleSolenoid`, `AnalogInput`, `AnalogPotentiometer`, `AdiEncoder` |
| Brain | `BrainScreen`, `RobotController` (battery), `Timer` |
| Control | `PIDController`, `ProfiledPIDController`, `TrapezoidProfile`, `SimpleMotorFeedforward`, `ArmFeedforward`, `ElevatorFeedforward`, `SlewRateLimiter`, `Debouncer`, `LinearFilter`, `MedianFilter` |
| Position | `DifferentialDriveOdometry`, `TrackingWheelOdometry`, `DifferentialDrivePoseEstimator`, `TrackingWheelPoseEstimator` |
| Vision (GPS) | `GpsPoseEstimator`, `EstimatedRobotPose`, `EstimateConsumer`: the PhotonVision pattern, with the GPS as the camera |
| Autonomous | `AutoBuilder`, `NamedCommands`, `AutoSelector` (brain-screen chooser), path following |
| Tools | Desktop simulator with every device, `Telemetry` + live dashboard, JUnit tests, GitHub Actions |

**Website:** 28 guide pages, a PathPlanner-style path planner, a telemetry dashboard and the full API reference live in `docs/`.
To publish it, go to the repository's **Settings → Pages**, choose **Deploy from a branch**, and pick
the `main` branch and the `/docs` folder. Rebuild it after changing the library with
`python3 docs-src/build.py --javadoc`.

> **Status:** Everything has unit tests and runs in the desktop simulator, and a generated project
> compiles all the way to the brain's WebAssembly (`generateWasm`) on Windows. It has **not yet been
> run on a physical brain.** Hydrozoa itself is experimental
> (alpha, and its repo was archived in Feb 2026), so expect to find a few rough edges on hardware.

## What robot code looks like

Current WPILib command-based, with motors set up the way REV does it: create the motor,
build a config, `configure()` it. The drive subsystem uses a leader and followers per side:

```java
public class DriveSubsystem extends SubsystemBase implements TankDriveSubsystem {
    // Left side: the front motor leads, the others follow it
    private final VexMotor m_leftLeader = new VexMotor(kLeftFrontMotorPort, kCartridge);
    private final VexMotor m_leftFollower1 = new VexMotor(kLeftMiddleMotorPort, kCartridge);
    private final VexMotor m_leftFollower2 = new VexMotor(kLeftRearMotorPort, kCartridge);
    ...
    private final VexEncoder m_leftEncoder = m_leftLeader.getEncoder();
    private final DifferentialDrive m_drive = new DifferentialDrive(m_leftLeader, m_rightLeader);
    private final VexInertial m_gyro = new VexInertial(kImuPort);

    public DriveSubsystem() {
        VexMotorConfig globalConfig = new VexMotorConfig();
        globalConfig
                .idleMode(IdleMode.kBrake)
                .smartCurrentLimit(kCurrentLimitAmps);
        globalConfig.encoder
                .positionConversionFactor(kMetersPerMotorRotation)
                .velocityConversionFactor(kMetersPerMotorRotation / 60.0);

        leftLeaderConfig.apply(globalConfig).inverted(kLeftFrontMotorReversed);
        leftFollower1Config.apply(globalConfig).inverted(kLeftMiddleMotorReversed).follow(m_leftLeader);
        ...
        m_leftLeader.configure(leftLeaderConfig);
        m_leftFollower1.configure(leftFollower1Config);
        ...
    }

    public void tankDrive(double leftSpeed, double rightSpeed) { m_drive.tankDrive(leftSpeed, rightSpeed); }
    public void arcadeDrive(double forward, double rotation) { m_drive.arcadeDrive(forward, rotation); }
}
```

`VexMotorConfig` settings: `inverted`, `idleMode` (`kCoast` / `kBrake` / `kHold`),
`smartCurrentLimit` (amps), `follow(leader)` / `follow(leader, invert)`,
`encoder.positionConversionFactor` / `velocityConversionFactor`, and `apply(other)` to layer a
shared config. Only settings you call are applied. With a conversion factor, the encoder and
`setPositionTarget` work in your units: the Clawbot arm's encoder reads degrees.

Differences from a SPARK MAX: there is no persist/reset mode (V5 motors don't store settings),
and a follower's own `inverted` setting still applies, so set each motor's direction for how
it's mounted.

Driver control is a command class, set as the default command in `RobotContainer`:

```java
m_robotDrive.setDefaultCommand(new TankDrive(
        m_robotDrive,
        () -> m_driverController.getLeftY(),
        () -> m_driverController.getRightY()));
```

Each robot has `commands/TankDrive.java` and `commands/ArcadeDrive.java`; the template
defaults to tank, the Clawbot to arcade.

| FRC / WPILib | Here |
| --- | --- |
| `TimedRobot` | `VexRobot` (10 ms loop) |
| `SparkMax` / `SparkMaxConfig` | `VexMotor` / `VexMotorConfig` |
| `RelativeEncoder` | `VexEncoder` (from `motor.getEncoder()`) |
| `MotorControllerGroup` | `VexMotorGroup` (or leader + followers) |
| `Pigeon2`, `AHRS` | `VexInertial` (`getAngle()` CW, `getRotation2d()` CCW) |
| `DifferentialDrive`, `DifferentialDriveOdometry`, `DifferentialDrivePoseEstimator` | Same names |
| `PhotonCamera` + `PhotonPoseEstimator` | `VexGps` + `GpsPoseEstimator`, in a `Vision` subsystem |
| `DigitalInput`, `Solenoid`, `DoubleSolenoid` | Same names, on 3-wire ports `'A'`–`'H'` |
| `SendableChooser<Command>` | `AutoSelector` (touch menu on the brain screen) |
| `edu.wpi.first.math.util.Units` | `com.robojacks.lib.math.util.Units` (same methods, plus tiles, mm, mV, mA) |
| `CommandXboxController` | `VexController` — **stick up is positive** (FRC is negative) |
| SmartDashboard | `Telemetry` over USB serial, viewed on the docs site's dashboard |

`TankDriveSubsystem` is a small interface: implement it on your drive subsystem and you get the
library's `TurnToHeading`, `DriveToPoint`, `DriveDistance` and tuning commands for free.

## What the hardware actually supports

Every `VexMotorConfig` and `VexInertial` setting maps to a V5 SDK call that PROS and VEXcode
C++ also use. Nothing here is emulated in software except followers.

| Here | V5 SDK call | PROS | VEXcode C++ |
| --- | --- | --- | --- |
| `inverted` | `vexDeviceMotorReverseFlagSet` | `motor_set_reversed` | `setReversed` |
| `idleMode` kCoast/kBrake/kHold | `vexDeviceMotorBrakeModeSet` + velocity 0 | `motor_set_brake_mode` + `motor_brake` | `setStopping` |
| `smartCurrentLimit` (0-2.5 A) | `vexDeviceMotorCurrentLimitSet` (mA) | `motor_set_current_limit` (default 2500 mA) | `setMaxTorque(x, amp)` |
| `setPositionTarget` | `vexDeviceMotorAbsoluteTargetSet` (built-in PID, 10 ms) | `motor_move_absolute` | `spinToPosition` |
| `setVoltage` | `vexDeviceMotorVoltageSet` (mV) | `motor_move_voltage` | `spin(..., volt)` |
| encoder position / velocity | `vexDeviceMotorPositionGet` / `ActualVelocityGet` | `motor_get_position` / `get_actual_velocity` | `position` / `velocity` |
| `getOutputCurrent` | `vexDeviceMotorCurrentGet` (mA) | `motor_get_current_draw` | `current(amp)` |
| `getMotorTemperature` | `vexDeviceMotorTemperatureGet` (°C, 5° steps) | `motor_get_temperature` | `temperature` |
| `VexInertial.getAngle` | `vexDeviceImuHeadingGet` (unbounded, CW+) | `imu_get_rotation` | `rotation` |
| `VexInertial` calibration | `vexDeviceImuReset` + status bit 0x01 | `imu_reset` (same 1 s / 3 s waits) | `calibrate` / `isCalibrating` |
| follower | — (software: leader copies its command to followers) | `MotorGroup` | `motor_group` |

Things the hardware does on its own that you should know about:

- **Current:** 2.5 A is the motor's maximum and default. With **more than 8 motors**, VEXos
  lowers every motor's limit so the battery isn't overloaded. Capping motors that don't need full
  current (claws, intakes) leaves more for the drive.
- **Heat:** a hot motor cuts its own current in steps (to 50%, 25%, 12.5%, then 0%).
- **Encoders:** 1800 / 900 / 300 ticks per output revolution for red / green / blue. The
  cartridge passed to `VexMotor` must match the one installed, or distances will be wrong.

## The Clawbot example

Standard Clawbot wiring (change ports in `Constants.java` if yours differ):
left drive 1, right drive 10, claw 3, arm 8. **Start the program with the arm all the way down.**

| Control | Action |
| --- | --- |
| Left stick up/down | Drive |
| Right stick left/right | Turn |
| L1 / L2 (hold) | Arm up / down (soft limits, holds position when released) |
| R1 / R2 | Open / close claw (runs until it stalls, then keeps a light grip) |
| D-pad up / right / down | Arm presets: high / carry / down |
| A | Grab: close claw, lift to carry |
| B | Place: lower arm, open claw |
| Y (hold) | Slow drive |

Auto `pickUpAndMove` grabs an object in front of it, carries it forward and left, sets it down
and backs away.

The kit Clawbot has no Inertial Sensor, so heading comes from the wheel encoders. **Measure
`kTrackWidthInches`**; turn accuracy depends on it. Adding an Inertial Sensor is one constant
(`kHasImu = true`).

Things to check on your own Clawbot: `ArmConstants.kGearRatio` (count the gear teeth) and the
claw direction (`ClawConstants.kReversed` if R1 closes instead of opens).

## Field coordinates (template)

- Origin **(0, 0) at the center of the field**, same as the VEX GPS sensor (and FTC).
- Meters internally; the field spans about ±1.8 m.
- Heading: 0 along +X, **counter-clockwise positive** (WPILib convention).
- `FieldConstants.mirror(pose)` flips a pose to the other side; `fromGps(...)` converts GPS
  readings (verify `GPS_HEADING_OFFSET_DEG` on a real field).

## Tuning without writing code

Set `kTuningMode = true` in `Constants.java`, upload, enable in driver control and press:

| Button | Test | Paste into `DriveConstants` |
| --- | --- | --- |
| **A** | Feedforward characterization (~1.5 m clear) | `kS`, `kV` |
| **B** | Turn PID auto-tune (rocks in place) | `kTurnP`, `kTurnD` |
| **X** | Drive 1 m, then measure with a tape | corrected `kWheelDiameterInches` |
| **Y** | Turn test, 4 × 90° | (check error) |
| **L1** | Abort | |

Run **A first**: `kS` is the voltage needed to beat friction, and with too little, turns stall a
few degrees short.

## VexLib as a vendor library

Teams use VexLib the way FRC teams use REVLib: a vendordep JSON in their project's `vendordeps/`
folder, resolved from a Maven repository. This repository hosts both on GitHub Pages:

- JSON: `https://kedrickjohnson.github.io/WPILIB_VEX_COMMAND_BASED/vendordeps/VexLib.json`
- Maven: `https://kedrickjohnson.github.io/WPILIB_VEX_COMMAND_BASED/maven`
  (`io.github.kedrickjohnson:vexlib:0.1.1`, with sources and javadoc jars)

The robot build reads every `vendordeps/*.json` and adds its repositories and dependencies; the
VS Code extension writes that build code into new projects and has a **Manage Vendor Libraries**
command. The `template/` and `examples/clawbot/` projects in this repository still use the
library from source (`project(":vexlib")`), so library changes can be tested before release.

To release a version: bump `vexlibVersion` in `gradle.properties`, run `./gradlew :vexlib:publish`
(writes `docs/maven/` and `docs/vendordeps/VexLib.json`; `python3 tools/release_vexlib.py` does
the same without Gradle), commit, and push.

VexLib depends on a build of Hydrozoa's main branch (`io.github.kedrickjohnson.hydrozoa:hydrozoa`,
in `docs/maven/`), because the releases on Maven Central don't have the sensor and 3-wire APIs yet.
`hydrozoaCommit` in `gradle.properties` says which commit; `tools/build_hydrozoa.py` rebuilds it.
`hydrozoa.bin` must come from the same commit. Old versions stay in
`docs/maven/` so teams that haven't updated keep building.

## VS Code extension

`vscode-extension/` is a VS Code extension for teams using this library:

- **VEX: Create a New Project**: a standalone project from the template or the Clawbot, with your
  name and Java package, and VexLib as a vendordep
- **VEX: Manage Vendor Libraries**: install (bundled, by URL, or from a file), check for updates, remove
- **Build / Simulate / Upload** buttons in the status bar (also `vex` tasks for `tasks.json`)
- Right-click a folder → **VEX → New Subsystem / Subsystem with a Motor / Command / Auto**

Build it with `node scripts/bundle.js` then `npx @vscode/vsce package` (or
`python3 scripts/package-vsix.py` without npm), and install the `.vsix` with
**Extensions → … → Install from VSIX**. See `vscode-extension/README.md`.

## Tests and CI

`vexlib/src/test` has JUnit tests that run the library against the simulator (`./gradlew test`).
`.github/workflows/build.yml` builds everything, runs the tests and all three simulators, tests the
VS Code extension and checks the docs site on every push; `release.yml` publishes a VexLib release
when you push a `v*` tag.

## Running

Simulator (no robot needed):

```
./gradlew :template:simulate
./gradlew :template:simulate --args=tune
./gradlew :examples:clawbot:simulate
```

The simulator models motor lag, static friction and hard stops, but not wheel slip or backlash,
so use it to check logic, not final gains.

On a robot:

1. Install the Hydrozoa CLI: `cargo install --git https://github.com/vexide/hydrozoa-cli.git`
2. Plug the brain (or controller) in over USB.
3. `./gradlew :examples:clawbot:hydrozoaUpload` (slot 2) or `./gradlew :template:hydrozoaUpload` (slot 1).

`hydrozoa.bin` in the repo root is the Hydrozoa runtime that gets uploaded alongside your
program (from vexide/Hydrozoa-Java, MIT license).

Keep loops light: Hydrozoa interprets WebAssembly, so it's slower than native PROS/vexide code.
