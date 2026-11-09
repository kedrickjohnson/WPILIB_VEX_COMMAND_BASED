/*
 * Java source templates for "VEX: New Subsystem / Command / Auto". Plain Node (no vscode import).
 */
'use strict';
const fs = require('fs');
const path = require('path');
const { JAVA_KEYWORDS } = require('./generator');

/** Returns an error message, or null if this is a usable Java class name. */
function validateClassName(name) {
  if (!name || !name.trim()) return 'Enter a class name.';
  if (!/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name)) return 'A class name uses letters, digits and _, and can\'t start with a digit.';
  if (JAVA_KEYWORDS.has(name)) return '"' + name + '" is a Java keyword.';
  if (!/^[A-Z]/.test(name)) return 'Java class names start with a capital letter, like ' + name.charAt(0).toUpperCase() + name.slice(1) + '.';
  return null;
}

/**
 * Finds the Java package for a folder: the part of the path after src/<set>/java/.
 * Returns null if the folder isn't inside a Java source folder.
 */
function packageForFolder(folder) {
  const parts = path.resolve(folder).split(path.sep);
  for (let i = parts.length - 1; i >= 2; i--) {
    if (parts[i] === 'java' && parts[i - 2] === 'src') {
      return { sourceRoot: parts.slice(0, i + 1).join(path.sep) || path.sep, pkg: parts.slice(i + 1).join('.') };
    }
  }
  return null;
}

function header(pkg) {
  return pkg ? 'package ' + pkg + ';\n\n' : '';
}

function camel(name) {
  return name.charAt(0).toLowerCase() + name.slice(1);
}

function imports(list, pkg) {
  const own = (fq) => fq.slice(0, fq.lastIndexOf('.')) === pkg;
  const uniq = Array.from(new Set(list.filter((fq) => fq && !own(fq)))).sort();
  return uniq.map((fq) => 'import ' + fq + ';\n').join('') + (uniq.length ? '\n' : '');
}

function subsystem(opts) {
  const { name, pkg } = opts;
  return header(pkg) + imports([
    'com.robojacks.lib.command.Command',
    'com.robojacks.lib.command.SubsystemBase'
  ], pkg) +
`/** ${opts.description || 'TODO: describe what this subsystem controls.'} */
public class ${name} extends SubsystemBase {

    public ${name}() {
        super("${name}");
        // Create and configure motors and sensors here.
    }

    @Override
    public void periodic() {
        // Runs every 10 ms, whether or not a command is using this subsystem.
    }

    /** Example command factory. Replace with what this mechanism does. */
    public Command exampleCommand() {
        return runOnce(() -> {
            // one-shot action
        }).withName("${name}Example");
    }
}
`;
}

/** The nested Constants class added for a motor subsystem. */
function motorConstantsBlock(opts) {
  return `
    public static final class ${opts.name}Constants {
        public static final int kMotorPort = ${opts.port};
        public static final boolean kReversed = false;
        public static final Cartridge kCartridge = Cartridge.${opts.cartridge};
        /** Amps. 2.5 is the motor's maximum; less leaves more for the drive. */
        public static final double kCurrentLimitAmps = ${opts.currentLimit.toFixed(1)};
        public static final double kForwardVolts = 8.0;
        public static final double kReverseVolts = -8.0;
    }
`;
}

/**
 * A subsystem that owns one motor, configured REV-style.
 * opts.constantsClass: fully qualified Constants class to use (its <Name>Constants nested class
 * gets added by addToConstants), or null to keep the constants in the subsystem itself.
 */
function motorSubsystem(opts) {
  const { name, pkg, constantsClass } = opts;
  const inline = !constantsClass;
  const imps = [
    'com.robojacks.lib.command.Command',
    'com.robojacks.lib.command.SubsystemBase',
    'com.robojacks.lib.hardware.VexMotor',
    'com.robojacks.lib.hardware.VexMotorConfig',
    'com.robojacks.lib.hardware.VexMotorConfig.IdleMode'
  ];
  if (inline) imps.push('com.robojacks.lib.hal.Cartridge');
  let staticImport = '';
  if (!inline) staticImport = 'import static ' + constantsClass + '.' + name + 'Constants.*;\n\n';
  const constants = inline ? `    private static final int kMotorPort = ${opts.port};
    private static final boolean kReversed = false;
    private static final Cartridge kCartridge = Cartridge.${opts.cartridge};
    private static final double kCurrentLimitAmps = ${opts.currentLimit.toFixed(1)};
    private static final double kForwardVolts = 8.0;
    private static final double kReverseVolts = -8.0;

` : '';
  return header(pkg) + imports(imps, pkg) + staticImport +
`/** ${opts.description || 'One-motor mechanism (roller, intake, flywheel...).'} */
public class ${name} extends SubsystemBase {
${constants}    private final VexMotor m_motor = new VexMotor(kMotorPort, kCartridge);

    public ${name}() {
        super("${name}");

        VexMotorConfig config = new VexMotorConfig();
        config.inverted(kReversed)
              .idleMode(IdleMode.${opts.idleMode || 'kCoast'})
              .smartCurrentLimit(kCurrentLimitAmps);
        m_motor.configure(config);
    }

    public void setVoltage(double volts) {
        m_motor.setVoltage(volts);
    }

    public void stop() {
        m_motor.stopMotor();
    }

    /** Runs forward while held; stops when released or interrupted. */
    public Command forwardCommand() {
        return startEnd(() -> setVoltage(kForwardVolts), this::stop).withName("${name}Forward");
    }

    /** Runs in reverse while held. */
    public Command reverseCommand() {
        return startEnd(() -> setVoltage(kReverseVolts), this::stop).withName("${name}Reverse");
    }
}
`;
}

/**
 * Adds <Name>Constants to an existing Constants.java (before its last closing brace),
 * and the Cartridge import if it's missing. Returns the new text, or null if the class
 * is already there or the file doesn't look like a Constants class.
 */
function addToConstants(constantsText, opts) {
  if (new RegExp('class\\s+' + opts.name + 'Constants\\b').test(constantsText)) return null;
  const end = constantsText.lastIndexOf('}');
  if (end < 0) return null;
  let text = constantsText.slice(0, end).replace(/\s*$/, '\n') + motorConstantsBlock(opts) + constantsText.slice(end);
  if (!/import\s+com\.robojacks\.lib\.hal\.Cartridge\s*;/.test(text)) {
    const m = text.match(/^package [^;]+;\s*\n/m);
    const at = m ? m.index + m[0].length : 0;
    text = text.slice(0, at) + '\nimport com.robojacks.lib.hal.Cartridge;\n' + text.slice(at);
  }
  return text;
}

/**
 * A command class. opts.requirement: { name, fq } of a subsystem it uses, or null.
 */
function command(opts) {
  const { name, pkg, requirement } = opts;
  const req = requirement;
  const field = req ? 'm_' + camel(req.name) : null;
  return header(pkg) + imports(['com.robojacks.lib.command.Command', req && req.fq], pkg) +
`/** ${opts.description || 'TODO: describe what this command does and when it finishes.'} */
public class ${name} extends Command {
${req ? `    private final ${req.name} ${field};

` : ''}    public ${name}(${req ? req.name + ' ' + camel(req.name) : ''}) {
${req ? `        ${field} = ${camel(req.name)};
        addRequirements(${field});
` : ''}        setName("${name}");
    }

    /** Called once when the command is scheduled. */
    @Override
    public void initialize() {}

    /** Called every 10 ms while the command runs. */
    @Override
    public void execute() {}

    /** Called once when the command ends or is interrupted. Stop motors here. */
    @Override
    public void end(boolean interrupted) {}

    /** Return true when the command is done. false = runs until interrupted. */
    @Override
    public boolean isFinished() {
        return false;
    }
}
`;
}

/** An autonomous routine as a static factory, like an FRC Autos class. */
function auto(opts) {
  const { name, pkg } = opts;
  const method = camel(name);
  return header(pkg) + imports([
    'com.robojacks.lib.auto.AutoBuilder',
    'com.robojacks.lib.command.Command',
    'com.robojacks.lib.command.Commands',
    'com.robojacks.lib.drive.TankDriveSubsystem'
  ], pkg) +
`/**
 * ${opts.description || 'Autonomous routine.'}
 * Coordinates are field-centered meters; heading 0 = +X, counter-clockwise positive.
 * Use it from RobotContainer: {@code return ${name}.${method}(m_robotDrive);}
 */
public final class ${name} {
    private ${name}() {}

    public static Command ${method}(TankDriveSubsystem drive) {
        return Commands.sequence(
                AutoBuilder.resetPose(drive, ${opts.startX ?? -1.2}, ${opts.startY ?? 0.0}, ${opts.startHeading ?? 0}),
                AutoBuilder.driveTo(drive, ${((opts.startX ?? -1.2) + 0.6).toFixed(1)}, ${opts.startY ?? 0.0}),
                AutoBuilder.turnTo(drive, 90),
                // NamedCommands registered in RobotContainer can run here:
                // NamedCommands.get("intake").withTimeout(1.0),
                AutoBuilder.backTo(drive, ${opts.startX ?? -1.2}, ${opts.startY ?? 0.0}),
                Commands.print("${name} done")
        ).withName("${name}");
    }
}
`;
}

/**
 * Finds the subsystems in a project: classes that extend SubsystemBase (or implement
 * Subsystem / TankDriveSubsystem) under any src/main/java folder below root.
 * Returns [{ name, fq, file }].
 */
function findSubsystems(root) {
  const found = [];
  const skip = new Set(['build', '.gradle', 'node_modules', '.git', 'out', 'vexlib']);
  (function walk(dir, depth) {
    if (depth > 14) return;
    let entries;
    try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { return; }
    for (const e of entries) {
      const full = path.join(dir, e.name);
      if (e.isDirectory()) { if (!skip.has(e.name)) walk(full, depth + 1); continue; }
      if (!e.name.endsWith('.java') || !full.includes(path.join('src', 'main', 'java'))) continue;
      let text;
      try { text = fs.readFileSync(full, 'utf8'); } catch (err) { continue; }
      const cls = text.match(/public\s+(?:final\s+)?class\s+(\w+)\s+(?:extends\s+SubsystemBase|implements\s+[^{]*\bSubsystem\b)/);
      if (!cls) continue;
      const pkg = (text.match(/^\s*package\s+([\w.]+)\s*;/m) || [])[1];
      found.push({ name: cls[1], fq: pkg ? pkg + '.' + cls[1] : cls[1], file: full });
    }
  })(root, 0);
  return found.sort((a, b) => a.name.localeCompare(b.name));
}

/** Finds Constants.java for a package: walks up the package from the folder, nearest first. */
function findConstants(sourceRoot, pkg) {
  const parts = pkg ? pkg.split('.') : [];
  for (let n = parts.length; n >= 0; n--) {
    const file = path.join(sourceRoot, ...parts.slice(0, n), 'Constants.java');
    if (fs.existsSync(file)) return { file, fq: parts.slice(0, n).concat('Constants').join('.') };
  }
  return null;
}

module.exports = {
  validateClassName, packageForFolder, subsystem, motorSubsystem, addToConstants,
  command, auto, findSubsystems, findConstants
};
