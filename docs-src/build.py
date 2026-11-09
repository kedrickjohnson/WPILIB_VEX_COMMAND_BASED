#!/usr/bin/env python3
"""Builds the documentation site.

    python3 docs-src/build.py            # writes docs/index.html
    python3 docs-src/build.py --javadoc  # also regenerates docs/api with javadoc

docs/index.html is a single page: the guides and the path planner are views switched by the
#hash, so it works on GitHub Pages (serve the docs/ folder) and anywhere else with no server.
"""
import pathlib
import subprocess
import sys

SRC = pathlib.Path(__file__).resolve().parent
ROOT = SRC.parent
DOCS = ROOT / "docs"

HEAD = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<style>:root{padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}</style>
"""


def page_fragment():
    """The page without <html>/<head>/<body> wrappers, scripts inlined."""
    html = (SRC / "site.html").read_text()
    scripts = "".join(
        "<script>\n" + (SRC / name).read_text() + "\n</script>\n"
        for name in ("planner-model.js", "site.js", "planner-ui.js")
    )
    return html.replace("{{SCRIPTS}}", scripts)


def full_page(fragment):
    split = fragment.index('<header class="topbar">')
    head, body = fragment[:split], fragment[split:]
    return HEAD + head + "</head>\n<body>\n" + body + "</body>\n</html>\n"


def javadoc():
    sources = [str(p) for p in (ROOT / "vexlib/src/main/java").rglob("*.java")]
    out = DOCS / "api"
    cmd = [
        "javadoc", "-quiet", "-Xdoclint:none", "-d", str(out),
        "-sourcepath", str(ROOT / "vexlib/src/main/java"),
        "-doctitle", "VEX Command-Based API",
        "-windowtitle", "VEX Command-Based API",
        "-bottom", '<a href="../index.html">Guides and path planner</a>',
        "--add-stylesheet", str(SRC / "javadoc.css"),
        "-subpackages", "com.robojacks.lib",
    ]
    classpath = sys.argv[sys.argv.index("--classpath") + 1] if "--classpath" in sys.argv else None
    if classpath:
        cmd += ["-classpath", classpath]
    subprocess.run(cmd, check=True)


def main():
    DOCS.mkdir(exist_ok=True)
    fragment = page_fragment()
    (DOCS / "index.html").write_text(full_page(fragment))
    (DOCS / ".nojekyll").write_text("")
    if "--fragment" in sys.argv:
        target = pathlib.Path(sys.argv[sys.argv.index("--fragment") + 1])
        target.write_text(fragment)
    if "--javadoc" in sys.argv:
        javadoc()
    print("built", DOCS / "index.html")


if __name__ == "__main__":
    main()
