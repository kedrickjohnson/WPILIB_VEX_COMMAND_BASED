# VEX Command-Based for VS Code

Write VEX V5 robots in Java the way FRC teams do: `Robot`, `RobotContainer`, subsystems,
commands, triggers and odometry, running on the brain through
[Hydrozoa](https://github.com/vexide/Hydrozoa-Java). This extension creates projects, builds,
simulates and uploads them, manages vendor libraries the way WPILib does, and writes new
subsystems, commands and autos for you.

## Create a project

**Command Palette → VEX: Create a New Project**, then pick:

1. **Command-based template** (6-motor tank drive, intake, path-planner autos) or **V5 Clawbot**
2. The folder to create it in
3. A name (also the folder name and the name shown on the brain)
4. Your Java package, such as `com.team1234.robot`

You get a standalone Gradle project with the Gradle wrapper, the Hydrozoa runtime, and VexLib
(the command-based library) installed as a vendor library in `vendordeps/VexLib.json`.

## Build, simulate, upload

When a robot project is open, the status bar shows:

| Button | Runs |
| --- | --- |
| *Robot name · slot* | Choose the robot program (if the workspace has more than one) |
| **Build** | `gradlew assemble` |
| **Simulate** | `gradlew simulate`: runs your code against simulated motors on your computer |
| **Upload** | `gradlew hydrozoaUpload`: builds and uploads over USB |

They're also VS Code tasks (**Terminal → Run Task → VEX**), so you can put them in `tasks.json`:

```json
{ "type": "vex", "task": "upload" }
```

`task` is `build`, `simulate`, `simulateTune` or `upload`; add `"robot": ":template"` in a
multi-robot repository. Compile errors show in the Problems panel.

Shortcuts: **Ctrl+Shift+U** (Cmd+Shift+U) uploads, **Ctrl+Shift+F5** simulates.

## Vendor libraries

Libraries work like FRC vendordeps. Each library is one JSON file in the project's `vendordeps/`
folder that names its Maven repository and artifacts, and the build adds every file it finds
there. **VEX: Manage Vendor Libraries** (also on right-click of `build.gradle.kts`) can:

| Option | Does |
| --- | --- |
| Install VexLib | Installs the copy that comes with the extension (shown when it's missing or newer) |
| Install new library (online) | Paste a vendordep JSON URL, such as `https://kedrickjohnson.github.io/vex-command-template/vendordeps/VexLib.json` |
| Install new library (from file) | A vendordep `.json` on your computer |
| Check for updates (online) | Downloads each library's `jsonUrl` and offers newer versions |
| Remove installed libraries | Deletes their JSON files |

The extension also copies its bundled VexLib into `~/.vex-command/maven`, which every project
checks first, so new projects build without reaching the library's website (like WPILib's
offline vendordep folder). Older build files that don't read `vendordeps/` get the support added
when you first open the manager.

Anyone can publish a library in the same format:

```json
{
  "fileName": "MyLib.json",
  "name": "MyLib",
  "version": "1.0.0",
  "uuid": "a-unique-id-that-never-changes",
  "mavenUrls": ["https://example.com/maven"],
  "jsonUrl": "https://example.com/vendordeps/MyLib.json",
  "javaDependencies": [{ "groupId": "com.example", "artifactId": "mylib", "version": "1.0.0" }]
}
```

## New subsystems, commands and autos

Right-click a folder under `src/main/java` → **VEX**:

| Wizard | Creates |
| --- | --- |
| New Subsystem | An empty `SubsystemBase` with `periodic()` and a command factory |
| New Subsystem with a Motor | A one-motor mechanism configured REV-style; asks for the port, cartridge and idle mode, and adds `<Name>Constants` to your `Constants.java` |
| New Command | A `Command` class; pick the subsystem it requires from the ones in your project |
| New Auto | An auto routine built with `AutoBuilder` (field-centered meters) |

Java snippets: `vexbind`, `vexstartend`, `vexrunonce`, `vexnamed`, `vexmotorconfig`, `vexsequence`.

## What you need installed

- **JDK 21** (the Gradle build uses Java 21, like Hydrozoa). The
  [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)
  gives you code completion.
- **For uploading:** [Rust](https://rustup.rs), then the Hydrozoa CLI:
  `cargo install --git https://github.com/vexide/hydrozoa-cli.git`
  (or **VEX: Install the Hydrozoa CLI**). The extension finds it on your PATH or in `~/.cargo/bin`.

The first build downloads Gradle and the Hydrozoa plugin, so it needs internet once.

## Settings

| Setting | Default | |
| --- | --- | --- |
| `vexCommandBased.showStatusBar` | `true` | Show the status bar buttons |
| `vexCommandBased.saveBeforeRun` | `true` | Save all files before Build / Simulate / Upload |
| `vexCommandBased.defaultPackage` | `""` | Package suggested for new projects |
| `vexCommandBased.hydrozoaPath` | `""` | Where the `hydrozoa` CLI is, if not on PATH |
| `vexCommandBased.gradleArgs` | `[]` | Extra Gradle arguments, such as `--offline` |

## Status

Hydrozoa is experimental (alpha; its repository was archived in February 2026). The library and
templates compile against Hydrozoa's SDK and run in the simulator, but haven't been run on a
physical brain yet.

## Building the extension

```
node scripts/bundle.js          # pack the published VexLib, template/ and examples/clawbot/
node test/run.js                # tests (set HYDROZOA_CLASSES to also compile + simulate)
npx @vscode/vsce package        # makes vex-command-based-<version>.vsix
```

Run the bundle step after publishing a new VexLib version (see the repository README). Install a
`.vsix` with **Extensions → … → Install from VSIX**.
