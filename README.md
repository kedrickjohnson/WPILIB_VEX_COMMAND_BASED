# VEX Command-Based (Java)

FRC-style command-based programming for VEX V5, in Java, running on the brain through
[Hydrozoa](https://github.com/vexide/Hydrozoa-Java). If you know WPILib command-based, you
already know how to use this: `Robot`, `RobotContainer`, `Constants`, subsystems that own their
motors, command factories, triggers, `Pose2d` and odometry.

| Project | What it is |
| --- | --- |
| `vexlib/` | The shared library ("WPILib-lite"). Teams don't edit it. |
| `template/` | Starting point for your own robot: 6-motor tank drive + example intake. |
| `examples/clawbot/` | A complete V5 Clawbot: drive, arm, claw, autos. |

> **Status:** Everything compiles against Hydrozoa's real SDK source and runs in the desktop
> simulator. It has **not yet been run on a physical brain.** Hydrozoa itself is experimental
> (alpha, and its repo was archived in Feb 2026), so expect to find a few rough edges on hardware.

## What robot code looks like

A subsystem owns its hardware, like in FRC:

```java
public class Drivetrain extends SubsystemBase implements TankDrive {
    private final VexMotorGroup m_leftMotors =
            new VexMotorGroup(kLeftMotorPorts, kLeftMotorsReversed, kCartridge);
    private final VexMotorGroup m_rightMotors =
            new VexMotorGroup(kRightMotorPorts, kRightMotorsReversed, kCartridge);
    private final DifferentialDrive m_drive = new DifferentialDrive(m_leftMotors, m_rightMotors);
    private final VexInertial m_gyro = new VexInertial(kImuPort);
    private final DifferentialDriveOdometry m_odometry;
    ...
    public Command driveDistanceCommand(double meters) { ... }
    public Command turnToAngleCommand(double degrees) { ... }
}
```

| FRC / WPILib | Here |
| --- | --- |
| `TimedRobot` | `VexRobot` (10 ms loop) |
| `CANSparkMax`, `TalonFX` | `VexMotor` (V5 Smart Motor) |
| `MotorControllerGroup` | `VexMotorGroup` |
| `Pigeon2`, `AHRS` | `VexInertial` (`getAngle()` CW, `getRotation2d()` CCW) |
| `DifferentialDrive`, `DifferentialDriveOdometry` | Same names |
| `CommandXboxController` | `VexController` — **stick up is positive** (FRC is negative) |
| SmartDashboard | `Telemetry`, printed over USB serial |

`TankDrive` is a small interface: implement it on your drivetrain and you get the library's
`TurnToHeading`, `DriveToPoint`, `DriveDistance` and tuning commands for free.

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
