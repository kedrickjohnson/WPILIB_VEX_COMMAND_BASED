# VEX Command-Based Template (Java)

FRC-style command-based programming for VEX V5, in Java, running on the brain through
[Hydrozoa](https://github.com/vexide/Hydrozoa-Java-Template). If you know WPILib command-based,
you already know how to use this: `Robot`, `RobotContainer`, subsystems, commands, triggers,
`Pose2d`, odometry.

> **Status:** the framework, odometry and robot template compile and run in the desktop
> simulator. The Hydrozoa hardware layer (`HydrozoaHardware.java`) is **stubbed** and must be
> filled in against the Hydrozoa SDK before this runs on a real brain. Hydrozoa itself is
> experimental (alpha), so expect some rough edges there.

## Layout

```
src/main/java/com/robojacks/
├── lib/                         # "WPILib-lite" — reusable, hardware-independent
│   ├── command/                 # Command, Subsystem, CommandScheduler, Trigger, groups, Commands
│   ├── math/                    # Pose2d, Rotation2d, Translation2d, Twist2d, MathUtil
│   ├── controller/              # PIDController, SimpleMotorFeedforward
│   ├── kinematics/              # DifferentialDriveOdometry
│   ├── robot/                   # VexRobot (TimedRobot equivalent), Timer
│   ├── input/                   # VexController (CommandXboxController equivalent)
│   ├── telemetry/               # Telemetry (SmartDashboard stand-in, prints over serial)
│   └── hal/                     # Hardware interfaces + hydrozoa/HydrozoaHardware (TODO)
└── robot/                       # Your team code — edit this
    ├── Main.java                # Brain entry point
    ├── Robot.java
    ├── RobotContainer.java
    ├── Constants.java
    ├── FieldConstants.java      # Field coordinate convention lives here
    ├── subsystems/              # Drivetrain (with odometry), Intake (example)
    ├── commands/                # TurnToHeading, DriveToPoint
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
./gradlew simulate
```

On the brain (after finishing `HydrozoaHardware`): install the Hydrozoa CLI, then use the
Hydrozoa Gradle plugin's upload task (see the Hydrozoa template's README for the exact task name).

## Next steps

1. Fill in `lib/hal/hydrozoa/HydrozoaHardware.java` using the Hydrozoa SDK.
2. Set ports, gear ratio, wheel size and track width in `Constants.java`.
3. Drive straight 1 m and check `drive/x` in telemetry; fix `WHEEL_DIAMETER_M` / `GEAR_RATIO`.
4. Spin 10 turns in place by hand and check `drive/deg` returns to 0 (IMU drift check). Heading
   comes from the IMU, so track width only matters for the simulator.
5. Tune `TURN_KP` and `DRIVE_KP`, then build autos.

Keep loops light: Hydrozoa interprets WebAssembly, so it's slower than native PROS/vexide code.
