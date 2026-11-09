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
| `DifferentialDrive`, `DifferentialDriveOdometry` | Same names |
| `CommandXboxController` | `VexController` — **stick up is positive** (FRC is negative) |
| SmartDashboard | `Telemetry`, printed over USB serial |

`TankDriveSubsystem` is a small interface: implement it on your drive subsystem and you get the
library's `TurnToHeading`, `DriveToPoint`, `DriveDistance` and tuning commands for free.

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
