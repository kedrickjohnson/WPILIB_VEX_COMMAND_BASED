#!/usr/bin/env python3
"""Publishes VexLib as a vendor library without Gradle.

    python3 tools/release_vexlib.py

Compiles against the Hydrozoa jars in docs/maven (tools/build_hydrozoa.py puts them there).

Does what `./gradlew :vexlib:publish` does, for machines where Gradle can't download its
plugins: compiles vexlib, then writes a Maven repository to docs/maven/ (jar, sources jar,
javadoc jar, pom, checksums, maven-metadata.xml) and docs/vendordeps/VexLib.json.
Version and URLs come from gradle.properties.
"""
import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "vexlib" / "src" / "main" / "java"


def props():
    out = {}
    for line in (ROOT / "gradle.properties").read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def java_files(root):
    return sorted(str(p) for p in root.rglob("*.java"))


def write_jar(path, base, files, manifest):
    """Deterministic jar: fixed timestamps, sorted entries, manifest first."""
    stamp = (2026, 1, 1, 0, 0, 0)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr(zipfile.ZipInfo("META-INF/", stamp), "")
        z.writestr(zipfile.ZipInfo("META-INF/MANIFEST.MF", stamp), manifest, zipfile.ZIP_DEFLATED)
        dirs = {"META-INF/"}
        for f in sorted(files):
            rel = f.relative_to(base).as_posix()
            parts = rel.split("/")[:-1]
            for i in range(1, len(parts) + 1):
                d = "/".join(parts[:i]) + "/"
                if d not in dirs:
                    dirs.add(d)
                    z.writestr(zipfile.ZipInfo(d, stamp), "")
            z.writestr(zipfile.ZipInfo(rel, stamp), f.read_bytes(), zipfile.ZIP_DEFLATED)


def checksums(path):
    data = path.read_bytes()
    for algo in ("md5", "sha1", "sha256", "sha512"):
        (path.parent / (path.name + "." + algo)).write_text(hashlib.new(algo, data).hexdigest())


def pom(p):
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>{p['vexlibGroup']}</groupId>
  <artifactId>vexlib</artifactId>
  <version>{p['vexlibVersion']}</version>
  <name>VexLib</name>
  <description>FRC-style command-based programming for VEX V5 in Java (Hydrozoa).</description>
  <url>{p['vexlibSiteUrl']}</url>
  <licenses>
    <license>
      <name>MIT License</name>
      <url>https://opensource.org/licenses/MIT</url>
    </license>
  </licenses>
  <dependencies>
    <dependency>
      <groupId>{p['hydrozoaGroup']}</groupId>
      <artifactId>hydrozoa</artifactId>
      <version>{p['hydrozoaVersion']}</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
</project>
"""


def metadata(p, versions):
    stamp = time.strftime("%Y%m%d%H%M%S", time.gmtime())
    vs = "".join(f"\n      <version>{v}</version>" for v in versions)
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>{p['vexlibGroup']}</groupId>
  <artifactId>vexlib</artifactId>
  <versioning>
    <latest>{versions[-1]}</latest>
    <release>{versions[-1]}</release>
    <versions>{vs}
    </versions>
    <lastUpdated>{stamp}</lastUpdated>
  </versioning>
</metadata>
"""


def vendordep(p):
    return {
        "fileName": "VexLib.json",
        "name": "VexLib",
        "version": p["vexlibVersion"],
        "uuid": p["vexlibUuid"],
        "hydrozoaVersion": p["hydrozoaVersion"],
        "hydrozoaPluginVersion": p["hydrozoaPluginVersion"],
        "mavenUrls": [p["vexlibSiteUrl"] + "/maven"],
        "jsonUrl": p["vexlibSiteUrl"] + "/vendordeps/VexLib.json",
        "javaDependencies": [
            {"groupId": p["vexlibGroup"], "artifactId": "vexlib", "version": p["vexlibVersion"]}
        ],
    }


def version_key(v):
    return [int(x) if x.isdigit() else x for x in v.replace("-", ".").split(".")]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--classpath", help="Hydrozoa classes or jars (default: the build in docs/maven)")
    ap.add_argument("--out", default=str(ROOT / "docs"), help="site folder (default docs/)")
    args = ap.parse_args()
    p = props()
    group, version = p["vexlibGroup"], p["vexlibVersion"]
    if not args.classpath:
        hz = ROOT / "docs" / "maven" / pathlib.Path(*p["hydrozoaGroup"].split("."))
        jars = [hz / a / p["hydrozoaVersion"] / f"{a}-{p['hydrozoaVersion']}.jar" for a in ("hydrozoa", "vex-sdk")]
        missing = [str(j) for j in jars if not j.exists()]
        if missing:
            sys.exit("Hydrozoa isn't in docs/maven yet; run tools/build_hydrozoa.py first. Missing: " + ", ".join(missing))
        args.classpath = ":".join(str(j) for j in jars)
    out = pathlib.Path(args.out)
    art_dir = out / "maven" / pathlib.Path(*group.split(".")) / "vexlib"
    ver_dir = art_dir / version
    if ver_dir.exists():
        shutil.rmtree(ver_dir)
    ver_dir.mkdir(parents=True)
    base = f"vexlib-{version}"
    manifest = (f"Manifest-Version: 1.0\nImplementation-Title: VexLib\nImplementation-Version: {version}\n"
                f"Automatic-Module-Name: com.robojacks.lib\n\n")

    with tempfile.TemporaryDirectory() as tmp:
        tmp = pathlib.Path(tmp)
        classes = tmp / "classes"
        listfile = tmp / "files.txt"
        listfile.write_text("\n".join(java_files(SRC)))
        subprocess.run(["javac", "--release", "21", "-nowarn", "-encoding", "UTF-8", "-d", str(classes),
                        "-cp", args.classpath, "@" + str(listfile)], check=True)
        write_jar(ver_dir / f"{base}.jar", classes, [f for f in classes.rglob("*") if f.is_file()], manifest)
        write_jar(ver_dir / f"{base}-sources.jar", SRC, [f for f in SRC.rglob("*") if f.is_file()], manifest)
        api = tmp / "javadoc"
        subprocess.run(["javadoc", "-quiet", "-Xdoclint:none", "-encoding", "UTF-8", "-d", str(api),
                        "-sourcepath", str(SRC), "-classpath", args.classpath,
                        "-subpackages", "com.robojacks.lib",
                        "-doctitle", f"VexLib {version}", "-windowtitle", f"VexLib {version}"],
                       check=True, stdout=subprocess.DEVNULL)
        write_jar(ver_dir / f"{base}-javadoc.jar", api, [f for f in api.rglob("*") if f.is_file()], manifest)

    (ver_dir / f"{base}.pom").write_text(pom(p))
    for f in list(ver_dir.iterdir()):
        checksums(f)

    versions = sorted((d.name for d in art_dir.iterdir() if d.is_dir()), key=version_key)
    meta = art_dir / "maven-metadata.xml"
    meta.write_text(metadata(p, versions))
    checksums(meta)

    vd = out / "vendordeps"
    vd.mkdir(parents=True, exist_ok=True)
    (vd / "VexLib.json").write_text(json.dumps(vendordep(p), indent=2) + "\n")
    print(f"published VexLib {version} to {art_dir.relative_to(ROOT) if art_dir.is_relative_to(ROOT) else art_dir}")
    print(f"vendordep: {(vd / 'VexLib.json')}")


if __name__ == "__main__":
    sys.exit(main())
