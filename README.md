# VEX Command-Based Template (Java)

FRC-style command-based programming for VEX V5, in Java, running on the brain through
[Hydrozoa](https://github.com/vexide/Hydrozoa-Java-Template). If you know WPILib command-based,
you already know how to use this: `Robot`, `RobotContainer`, subsystems, commands, triggers,
`Pose2d`, odometry.

**Teams get a working tank drive by editing one block in `Constants.java`.** The drivetrain,
odometry, turn-to-heading, drive-to-point and tuning tests are all built in.

> **Status:** the framework, drivetrain and tuning tools compile and run in the desktop
> simulator. The Hydrozoa hardware layer (`HydrozoaHardware.java`) is **stubbed** and must be
> filled in against the Hydrozoa SDK before this runs on a real brain. Hydrozoa itself is
> experimental (alpha), so expect some rough edges there.

## Setting up a drivetrain

Everything team-specific is in `Constants.DRIVE`:

```java
public static final DrivetrainConfig DRIVE = DrivetrainConfig.builder()
        .leftMotors(-1, -2, -3)      // smart ports; negative = reversed
        .rightMotors(4, 5, 6)
        .imuPort(10)
        .cartridge(Cartridge.BLUE)   // RED / GREEN / BLUE
        .gearRatio(36, 48)           // motor gear teeth, wheel gear teeth (or .directDrive())
        .wheelDiameterInches(3.25)
        .trackWidthInches(11.5)
        // optional tuning, filled in from the tests below
        .turnPID(9.0, 0.5)
        .drivePID(25.0, 1.0)
        .feedforward(0.5, 0.0)
        .build();
```

`build()` checks the config at startup: ports must be 1-21, no port used twice, IMU not on a
motor port, and track width set. Mistakes fail with a message that names the problem.

## The Drivetrain subsystem

`robot/subsystems/Drivetrain.java` is a normal FRC-style subsystem with its commands in it.
The motors, IMU and odometry come from the library base class (`TankDrivetrain`), so the file
holds only commands and your own additions:

| Command | Use |
| --- | --- |
| `arcadeDriveCommand(fwd, turn)` | Default teleop drive |
| `tankDriveCommand(left, right)` | Tank-style teleop |
| `resetPoseCommand(pose)` | Set the starting position for auto |
| `driveDistanceCommand(m)` | Straight line, holds heading |
| `turnToAngleCommand(deg)` | Turn to a field heading |
| `driveToPointCommand(x, y)` | Drive to a field point, steering as it goes |
| `backToPointCommand(x, y)` | Reverse into a field point |
| `turnAndDriveToPointCommand(x, y)` | Face the point first, then drive |
| `stopCommand()` | Stop |

Add your own commands the same way. Autos read like FRC:

```java
Commands.sequence(
    drive.resetPoseCommand(StartPoses.LEFT_START),
    drive.driveToPointCommand(-0.9, 0.6),
    drive.turnToAngleCommand(180),
    drive.backToPointCommand(-1.2, 0.0));
```

## Tuning without writing code

Set `TUNING_MODE = true` in `Constants.java`, upload, enable in driver control and press:

| Button | Test | What it gives you |
| --- | --- | --- |
| **A** | Feedforward characterization (needs ~1.5 m clear) | `.feedforward(kS, kV)` |
| **B** | Turn auto-tune (rocks back and forth in place) | `.turnPID(kP, kD)` |
| **X** | Drive 1 m test (measure with a tape) | corrected `.wheelDiameterInches(...)` |
| **Y** | Turn test, 4 × 90° | error at each stop |
| **L1** | Abort the current test | |

Each test prints a ready-to-paste line over USB serial. Recommended order: **A → B → X → Y**.
Set `TUNING_MODE` back to `false` before competing.

**Why kS matters:** it's the voltage needed to beat friction. With kS = 0, small end-of-turn
corrections can't move the robot and turns stall a few degrees short. Run test A first.

## Layout

```
src/main/java/com/robojacks/
├── lib/                         # "WPILib-lite" — teams don't edit this
│   ├── drive/                   # DrivetrainConfig, TankDrivetrain (base class), TurnToHeading,
│   │                            # DriveToPoint, DriveDistance, TuningCommands
│   ├── command/                 # Command, Subsystem, CommandScheduler, Trigger, groups, Commands
│   ├── math/                    # Pose2d, Rotation2d, Translation2d, Twist2d, MathUtil
│   ├── controller/              # PIDController, SimpleMotorFeedforward
│   ├── kinematics/              # DifferentialDriveOdometry
│   ├── robot/                   # VexRobot (TimedRobot equivalent), Timer
│   ├── input/                   # VexController (CommandXboxController equivalent)
│   ├── telemetry/               # Telemetry (SmartDashboard stand-in, prints over serial)
│   └── hal/                     # Hardware interfaces + hydrozoa/HydrozoaHardware (TODO)
└── robot/                       # Team code — edit this
    ├── Main.java                # Brain entry point
    ├── Robot.java
    ├── RobotContainer.java      # Bindings (match or tuning mode)
    ├── Constants.java           # ← the drivetrain config lives here
    ├── FieldConstants.java      # Field coordinate convention + start poses
    ├── subsystems/              # Drivetrain (commands live here), Intake (example)
    └── autos/                   # Autos.exampleAuto

src/sim/java/com/robojacks/sim/  # Desktop simulator (not uploaded)
```

## Field coordinates

- Origin **(0, 0) at the center of the field**, same as the VEX GPS sensor (and FTC).
- Meters internally; the field spans about ±1.8 m.
- Heading: 0 along +X, **counter-clockwise positive** (WPILib convention).
- `FieldConstants.mirror(pose)` flips a pose to the opposite side (180° about the center).
- `FieldConstants.fromGps(...)` converts GPS readings. **Verify `GPS_HEADING_OFFSET_DEG` on a real field.**

## Differences from FRC

| FRC | Here |
| --- | --- |
| `TimedRobot` | `VexRobot` (default 10 ms loop) |
| `CommandXboxController` | `VexController` — **stick up is positive** (FRC is negative) |
| DriverStation modes | VEX competition switch / field control |
| SmartDashboard / NetworkTables | `Telemetry` printed over USB serial |
| `edu.wpi.first.math` | `com.robojacks.lib.math` (same class and method names) |

## Running

Simulator (no robot needed):

```
./gradlew simulate                 # match: disabled -> 15 s auto -> teleop
./gradlew simulate --args=tune     # runs the tuning tests and checks the gains they suggest
```

The simulator models motor lag and static friction, but not wheel slip or backlash, so real
tuning numbers will differ. Use it to check logic, not final gains.

On the brain (after finishing `HydrozoaHardware`): install the Hydrozoa CLI, then use the
Hydrozoa Gradle plugin's upload task (see the Hydrozoa template's README for the exact task name).

## Next steps

1. Fill in `lib/hal/hydrozoa/HydrozoaHardware.java` using the Hydrozoa SDK.
2. Set up `Constants.DRIVE` for your robot.
3. Turn on `TUNING_MODE` and run tests A → B → X → Y.
4. Spin the robot 10 turns by hand and check `drive/deg` returns to 0 (IMU drift check).
5. Build autos.

Keep loops light: Hydrozoa interprets WebAssembly, so it's slower than native PROS/vexide code.
