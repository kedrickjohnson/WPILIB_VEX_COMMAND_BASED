#!/usr/bin/env python3
"""Builds the documentation site.

    python3 docs-src/build.py            # writes docs/index.html
    python3 docs-src/build.py --javadoc  # also regenerates docs/api with javadoc

docs/index.html is a single page: the guides, the path planner and the dashboard are views
switched by the #hash, so it works on GitHub Pages (serve the docs/ folder) and anywhere else.

Guides live in docs-src/guides/NN-name.html, one page per file, ordered by the number. Each
starts with a header comment:

    <!-- page: id | Group | Title | One-line summary -->

followed by the page body (no <section> wrapper). The build adds the section, the group label,
heading ids, an "On this page" box, the sidebar, previous/next links and the search index.

Placeholders usable in guides:
    {{SRC:path}}           a whole source file from the repo, escaped
    {{SNIP:path#region}}   the lines between "// [region]" and "// [/region]" in a source file
    {{API:fully.qualified.Class}}  a link to that class's Javadoc page
"""
import html
import json
import pathlib
import re
import subprocess
import sys
import textwrap

SRC = pathlib.Path(__file__).resolve().parent
ROOT = SRC.parent
DOCS = ROOT / "docs"
GUIDES = SRC / "guides"

HEAD = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<style>:root{padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}</style>
"""

HEADER_RE = re.compile(r"^\s*<!--\s*page:\s*(.*?)\s*-->\s*", re.S)


def slug(text):
    s = re.sub(r"<[^>]+>", "", text)
    s = html.unescape(s).lower()
    s = re.sub(r"[^a-z0-9]+", "-", s).strip("-")
    return s or "section"


def snippet(path, region):
    lines = (ROOT / path).read_text().split("\n")
    start = end = None
    for i, line in enumerate(lines):
        if re.search(r"//\s*\[" + re.escape(region) + r"\]\s*$", line):
            start = i + 1
        elif start is not None and re.search(r"//\s*\[/" + re.escape(region) + r"\]\s*$", line):
            end = i
            break
    if start is None or end is None:
        raise SystemExit(f"snippet {path}#{region} not found")
    body = "\n".join(l for l in lines[start:end] if not re.search(r"//\s*\[/?[\w-]+\]\s*$", l))
    return textwrap.dedent(body).strip("\n")


def api_link(fq):
    path = fq.replace(".", "/")
    # Nested classes: com.x.Outer.Inner -> com/x/Outer.Inner.html
    parts = fq.split(".")
    pkg = [p for p in parts if p[:1].islower()]
    cls = [p for p in parts if p[:1].isupper()]
    href = "api/" + "/".join(pkg) + "/" + ".".join(cls) + ".html"
    if not (DOCS / href).exists() and (DOCS / "api").exists():
        print(f"warning: API page for {fq} not found ({href})", file=sys.stderr)
    return f'<a href="{href}">{".".join(cls)}</a>'


def expand(text):
    text = re.sub(r"\{\{SRC:([^}]+)\}\}", lambda m: html.escape((ROOT / m.group(1)).read_text().rstrip()), text)
    text = re.sub(r"\{\{SNIP:([^#}]+)#([\w-]+)\}\}", lambda m: html.escape(snippet(m.group(1), m.group(2))), text)
    text = re.sub(r"\{\{API:([\w.]+)\}\}", lambda m: api_link(m.group(1)), text)
    return text


def load_guides():
    pages = []
    for f in sorted(GUIDES.glob("[0-9]*.html")):
        raw = f.read_text()
        m = HEADER_RE.match(raw)
        if not m:
            raise SystemExit(f"{f.name}: missing <!-- page: id | Group | Title | Summary --> header")
        fields = [x.strip() for x in m.group(1).split("|")]
        if len(fields) != 4:
            raise SystemExit(f"{f.name}: header needs 4 fields separated by |")
        pid, group, title, summary = fields
        pages.append({"id": pid, "group": group, "title": title, "summary": summary, "body": expand(raw[m.end():])})
    ids = [p["id"] for p in pages]
    dupes = {i for i in ids if ids.count(i) > 1}
    if dupes:
        raise SystemExit(f"duplicate page ids: {dupes}")
    return pages


def add_heading_ids(page):
    """Gives every h2/h3 an id (page-slug) and returns [(level, id, text)]."""
    heads = []
    used = set()

    def repl(m):
        level, attrs, inner = m.group(1), m.group(2), m.group(3)
        idm = re.search(r'\bid="([^"]+)"', attrs)
        if idm:
            hid = idm.group(1)
        else:
            hid = page["id"] + "-" + slug(inner)
            n = 2
            while hid in used:
                hid = page["id"] + "-" + slug(inner) + "-" + str(n)
                n += 1
            attrs = attrs + f' id="{hid}"'
        used.add(hid)
        heads.append((int(level), hid, re.sub(r"<[^>]+>", "", inner)))
        return f"<h{level}{attrs}>{inner}</h{level}>"

    page["body"] = re.sub(r"<h([23])([^>]*)>(.*?)</h\1>", repl, page["body"], flags=re.S)
    return heads


def plain(html_text):
    t = re.sub(r"<pre[\s\S]*?</pre>", " ", html_text)
    t = re.sub(r"<[^>]+>", " ", t)
    return re.sub(r"\s+", " ", html.unescape(t)).strip()


def render_guides(pages):
    nav, sections, index = [], [], []
    groups = []
    for p in pages:
        if p["group"] not in groups:
            groups.append(p["group"])
    for g in groups:
        items = "".join(f'        <li><a href="#{p["id"]}" data-nav>{html.escape(p["title"])}</a></li>\n'
                        for p in pages if p["group"] == g)
        nav.append(f"    <div>\n      <h2>{html.escape(g)}</h2>\n      <ul>\n{items}      </ul>\n    </div>")

    for i, p in enumerate(pages):
        heads = add_heading_ids(p)
        h2s = [h for h in heads if h[0] == 2]
        body = p["body"].strip()
        # "On this page" after the lede paragraph, for longer pages
        if len(h2s) >= 4:
            otp = ('<nav class="on-this-page" aria-label="On this page"><strong>On this page</strong><ul>'
                   + "".join(f'<li><a href="#{hid}">{html.escape(t)}</a></li>' for _, hid, t in h2s)
                   + "</ul></nav>")
            m = re.search(r'<p class="lede">.*?</p>', body, re.S)
            body = body[:m.end()] + "\n" + otp + body[m.end():] if m else otp + "\n" + body
        prev_p = pages[i - 1] if i > 0 else None
        next_p = pages[i + 1] if i + 1 < len(pages) else None
        pager = '<nav class="pager" aria-label="More pages">'
        if prev_p:
            pager += f'<a href="#{prev_p["id"]}"><span>Previous</span>{html.escape(prev_p["title"])}</a>'
        if next_p:
            pager += f'<a href="#{next_p["id"]}"><span>Next</span>{html.escape(next_p["title"])}</a>'
        pager += "</nav>"
        sections.append(
            f'<!-- ----------------------------------------------------------------------- {p["id"]} -->\n'
            f'<section class="view doc" id="{p["id"]}">\n'
            f'  <p class="eyebrow">{html.escape(p["group"])}</p>\n'
            f'  <h1>{html.escape(p["title"])}</h1>\n{body}\n  {pager}\n</section>\n')

        # Search index: the page, and each heading with the text under it.
        index.append({"id": p["id"], "kind": "page", "title": p["title"], "page": p["group"],
                      "text": p["summary"] + " " + plain(body)[:600]})
        parts = re.split(r'(<h[23][^>]*\bid="[^"]+"[^>]*>.*?</h[23]>)', body, flags=re.S)
        for j in range(1, len(parts), 2):
            hm = re.match(r'<h[23][^>]*\bid="([^"]+)"[^>]*>(.*?)</h[23]>', parts[j], re.S)
            text = plain(parts[j + 1]) if j + 1 < len(parts) else ""
            index.append({"id": hm.group(1), "kind": "section", "title": plain(hm.group(2)),
                          "page": p["title"], "text": text[:400]})
    return "\n".join(nav), "\n".join(sections), index


def page_fragment():
    """The page without <html>/<head>/<body> wrappers, scripts inlined."""
    page = (SRC / "site.html").read_text()
    pages = load_guides()
    nav, sections, index = render_guides(pages)
    page = page.replace("{{GUIDE_NAV}}", nav).replace("{{GUIDES}}", sections)
    sample = SRC / "sample-telemetry.log"
    page = page.replace("{{SAMPLE_LOG}}", html.escape(sample.read_text()) if sample.exists() else "")
    scripts = "<script>window.SEARCH_INDEX = " + json.dumps(index, separators=(",", ":")).replace("</", "<\\/") + ";</script>\n"
    scripts += "".join(
        "<script>\n" + (SRC / name).read_text() + "\n</script>\n"
        for name in ("planner-model.js", "site.js", "planner-ui.js", "dashboard.js")
    )
    page = page.replace("{{SCRIPTS}}", scripts)
    # {{SRC_BLOCK}}: the vendor-library block new projects get, straight from the VS Code extension.
    if "{{SRC_BLOCK}}" in page:
        block = subprocess.run(
            ["node", "-e", "process.stdout.write(require('./vscode-extension/src/generator').VENDORDEP_BLOCK)"],
            cwd=ROOT, check=True, capture_output=True, text=True).stdout
        page = page.replace("{{SRC_BLOCK}}", html.escape(block.rstrip()))
    page = expand(page)
    # Every internal #link must point at something.
    ids = set(re.findall(r'\bid="([^"]+)"', page))
    missing = sorted({h for h in re.findall(r'href="#([\w-]+)"', page) if h not in ids})
    if missing:
        raise SystemExit("broken internal links: " + ", ".join(missing))
    return page


def full_page(fragment):
    split = fragment.index('<header class="topbar">')
    head, body = fragment[:split], fragment[split:]
    return HEAD + head + "</head>\n<body>\n" + body + "</body>\n</html>\n"


def javadoc():
    out = DOCS / "api"
    source_roots = ["vexlib/src/main/java", "template/src/main/java", "examples/clawbot/src/main/java"]
    cmd = [
        "javadoc", "-quiet", "-Xdoclint:none", "-d", str(out),
        "-sourcepath", ":".join(str(ROOT / r) for r in source_roots),
        "-group", "Commands and robot", "com.robojacks.lib.command*:com.robojacks.lib.robot*:com.robojacks.lib.input*:com.robojacks.lib.auto*",
        "-group", "Hardware", "com.robojacks.lib.hardware*:com.robojacks.lib.hal*",
        "-group", "Control and math", "com.robojacks.lib.controller*:com.robojacks.lib.math*:com.robojacks.lib.trajectory*",
        "-group", "Driving and odometry", "com.robojacks.lib.drive*:com.robojacks.lib.kinematics*:com.robojacks.lib.estimator*",
        "-group", "Tools", "com.robojacks.lib.sim*:com.robojacks.lib.telemetry*",
        "-group", "Template robot", "com.robojacks.robot*",
        "-group", "Clawbot example", "com.robojacks.clawbot*",
        "-doctitle", "VEX Command-Based API",
        "-windowtitle", "VEX Command-Based API",
        "-overview", str(SRC / "javadoc-overview.html"),
        "-bottom", '<a href="{@docRoot}/../index.html">Guides, path planner and dashboard</a>',
        "--add-stylesheet", str(SRC / "javadoc.css"),
        "-subpackages", "com.robojacks",
    ]
    classpath = sys.argv[sys.argv.index("--classpath") + 1] if "--classpath" in sys.argv else None
    if classpath:
        cmd += ["-classpath", classpath]
    subprocess.run(cmd, check=True)


def main():
    DOCS.mkdir(exist_ok=True)
    if "--javadoc" in sys.argv:
        javadoc()  # first, so {{API:...}} links can be checked
    fragment = page_fragment()
    (DOCS / "index.html").write_text(full_page(fragment))
    (DOCS / ".nojekyll").write_text("")
    if "--fragment" in sys.argv:
        target = pathlib.Path(sys.argv[sys.argv.index("--fragment") + 1])
        target.write_text(fragment)
    print("built", DOCS / "index.html")


if __name__ == "__main__":
    main()
