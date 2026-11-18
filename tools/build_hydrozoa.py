#!/usr/bin/env python3
"""Builds Hydrozoa (the Java runtime library for the brain) from its GitHub source and publishes
it to docs/maven/, next to VexLib.

    git clone https://github.com/vexide/Hydrozoa-Java.git
    git -C Hydrozoa-Java checkout <hydrozoaCommit from gradle.properties>
    python3 tools/build_hydrozoa.py Hydrozoa-Java

Why this exists: VexLib needs Hydrozoa's sensor, 3-wire and exception-handling APIs, which are on
Hydrozoa's main branch but not in any release on Maven Central (the newest there, 0.1.0-alpha.2,
has motors, the controller and the screen only). So VexLib depends on a build of a specific main
commit, published under our own group ({hydrozoaGroup} in gradle.properties) so it can never be
mixed up with vexide's releases. The hydrozoa.bin runtime in this repo comes from the same commit;
the jar and the runtime must match.

Hydrozoa is MIT licensed (Copyright (c) 2024 vexide Contributors); its LICENSE goes in each jar.

Compile-only stubs for the two annotation libraries Hydrozoa uses live in tools/hydrozoa-stubs/.
They are not packaged: on the brain, TeaVM uses the real org.teavm:teavm-interop, which the
vex-sdk POM depends on.
"""
import argparse
import pathlib
import subprocess
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from release_vexlib import ROOT, props, java_files, write_jar, checksums, version_key  # noqa: E402

STUBS = ROOT / "tools" / "hydrozoa-stubs"
TEAVM_VERSION = "0.10.2"  # the TeaVM the hydrozoa Gradle plugin uses


def pom(group, artifact, version, name, description, deps):
    dep_xml = "".join(f"""
    <dependency>
      <groupId>{g}</groupId>
      <artifactId>{a}</artifactId>
      <version>{v}</version>
      <scope>compile</scope>
    </dependency>""" for g, a, v in deps)
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>{group}</groupId>
  <artifactId>{artifact}</artifactId>
  <version>{version}</version>
  <name>{name}</name>
  <description>{description}</description>
  <url>https://github.com/vexide/Hydrozoa-Java</url>
  <licenses>
    <license>
      <name>The MIT License</name>
      <url>https://opensource.org/licenses/MIT</url>
    </license>
  </licenses>
  <scm>
    <url>https://github.com/vexide/Hydrozoa-Java</url>
  </scm>
  <dependencies>{dep_xml}
  </dependencies>
</project>
"""


def metadata(group, artifact, versions):
    vs = "".join(f"\n      <version>{v}</version>" for v in versions)
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>{group}</groupId>
  <artifactId>{artifact}</artifactId>
  <versioning>
    <latest>{versions[-1]}</latest>
    <release>{versions[-1]}</release>
    <versions>{vs}
    </versions>
  </versioning>
</metadata>
"""


def publish(out, group, artifact, version, classes, src, license_file, pom_text, title):
    art_dir = out / "maven" / pathlib.Path(*group.split(".")) / artifact
    ver_dir = art_dir / version
    ver_dir.mkdir(parents=True, exist_ok=True)
    for f in ver_dir.iterdir():
        f.unlink()
    manifest = (f"Manifest-Version: 1.0\nImplementation-Title: {title}\n"
                f"Implementation-Version: {version}\n\n")
    base = f"{artifact}-{version}"
    lic = classes / "META-INF" / "LICENSE"
    lic.parent.mkdir(parents=True, exist_ok=True)
    lic.write_bytes(license_file.read_bytes())
    write_jar(ver_dir / f"{base}.jar", classes, [f for f in classes.rglob("*") if f.is_file()], manifest)
    write_jar(ver_dir / f"{base}-sources.jar", src, [f for f in src.rglob("*") if f.is_file()], manifest)
    (ver_dir / f"{base}.pom").write_text(pom_text)
    for f in list(ver_dir.iterdir()):
        checksums(f)
    versions = sorted((d.name for d in art_dir.iterdir() if d.is_dir()), key=version_key)
    meta = art_dir / "maven-metadata.xml"
    meta.write_text(metadata(group, artifact, versions))
    checksums(meta)
    print(f"published {group}:{artifact}:{version}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("source", help="a Hydrozoa-Java checkout at hydrozoaCommit")
    ap.add_argument("--out", default=str(ROOT / "docs"), help="site folder (default docs/)")
    args = ap.parse_args()
    p = props()
    group, version, commit = p["hydrozoaGroup"], p["hydrozoaVersion"], p["hydrozoaCommit"]
    srcroot = pathlib.Path(args.source).resolve()
    head = subprocess.run(["git", "-C", str(srcroot), "rev-parse", "HEAD"], capture_output=True, text=True,
                          check=True).stdout.strip()
    if not head.startswith(commit):
        sys.exit(f"{srcroot} is at {head[:7]}, but gradle.properties says hydrozoaCommit={commit}. "
                 f"Run: git -C {srcroot} checkout {commit}")
    out = pathlib.Path(args.out)
    sdk_src = srcroot / "vex-sdk" / "src" / "main" / "java"
    lib_src = srcroot / "src" / "main" / "java"
    license_file = srcroot / "LICENSE"

    with tempfile.TemporaryDirectory() as tmp:
        tmp = pathlib.Path(tmp)
        stub_classes = tmp / "stubs"
        subprocess.run(["javac", "--release", "21", "-nowarn", "-d", str(stub_classes)] + java_files(STUBS),
                       check=True)
        sdk_classes = tmp / "sdk"
        subprocess.run(["javac", "--release", "21", "-nowarn", "-encoding", "UTF-8", "-d", str(sdk_classes),
                        "-cp", str(stub_classes)] + java_files(sdk_src), check=True)
        lib_files = [f for f in java_files(lib_src) if not f.endswith("module-info.java")]
        lib_classes = tmp / "lib"
        subprocess.run(["javac", "--release", "21", "-nowarn", "-encoding", "UTF-8", "-d", str(lib_classes),
                        "-cp", f"{stub_classes}:{sdk_classes}"] + lib_files, check=True)

        desc = f"Built from vexide/Hydrozoa-Java commit {commit} for VexLib."
        publish(out, group, "vex-sdk", version, sdk_classes, sdk_src, license_file,
                pom(group, "vex-sdk", version, "Hydrozoa VEX SDK", desc,
                    [("org.teavm", "teavm-interop", TEAVM_VERSION)]), "Hydrozoa VEX SDK")
        publish(out, group, "hydrozoa", version, lib_classes, lib_src, license_file,
                pom(group, "hydrozoa", version, "Hydrozoa", desc,
                    [(group, "vex-sdk", version)]), "Hydrozoa")


if __name__ == "__main__":
    sys.exit(main())
