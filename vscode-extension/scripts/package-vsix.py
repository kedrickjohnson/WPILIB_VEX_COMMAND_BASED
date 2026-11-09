#!/usr/bin/env python3
"""Builds the .vsix without npm (same layout `vsce package` produces).

    python3 scripts/package-vsix.py

Prefer `npx @vscode/vsce package` when you have npm; this is for machines without it.
"""
import fnmatch, json, pathlib, zipfile
from xml.sax.saxutils import escape

EXT = pathlib.Path(__file__).resolve().parent.parent
pkg = json.loads((EXT / "package.json").read_text())
ignore = [l.strip() for l in (EXT / ".vscodeignore").read_text().splitlines() if l.strip()]

def ignored(rel):
    return any(fnmatch.fnmatch(rel, pat) or fnmatch.fnmatch(rel + "/", pat) for pat in ignore)

files = sorted(p for p in EXT.rglob("*") if p.is_file() and not ignored(p.relative_to(EXT).as_posix()))
out = EXT / f"{pkg['name']}-{pkg['version']}.vsix"

manifest = f"""<?xml version="1.0" encoding="utf-8"?>
<PackageManifest Version="2.0.0" xmlns="http://schemas.microsoft.com/developer/vsx-schema/2011" xmlns:d="http://schemas.microsoft.com/developer/vsx-schema-design/2011">
  <Metadata>
    <Identity Language="en-US" Id="{pkg['name']}" Version="{pkg['version']}" Publisher="{pkg['publisher']}"/>
    <DisplayName>{escape(pkg['displayName'])}</DisplayName>
    <Description xml:space="preserve">{escape(pkg['description'])}</Description>
    <Tags>{escape(','.join(pkg.get('keywords', [])))}</Tags>
    <Categories>{escape(','.join(pkg.get('categories', [])))}</Categories>
    <GalleryFlags>Public</GalleryFlags>
    <Properties>
      <Property Id="Microsoft.VisualStudio.Code.Engine" Value="{pkg['engines']['vscode']}"/>
      <Property Id="Microsoft.VisualStudio.Code.ExtensionDependencies" Value=""/>
      <Property Id="Microsoft.VisualStudio.Code.ExtensionPack" Value=""/>
      <Property Id="Microsoft.VisualStudio.Code.ExtensionKind" Value="workspace"/>
      <Property Id="Microsoft.VisualStudio.Services.Links.Source" Value="{pkg['repository']['url']}"/>
    </Properties>
    <License>extension/LICENSE</License>
    <Icon>extension/{pkg['icon']}</Icon>
  </Metadata>
  <Installation>
    <InstallationTarget Id="Microsoft.VisualStudio.Code"/>
  </Installation>
  <Dependencies/>
  <Assets>
    <Asset Type="Microsoft.VisualStudio.Code.Manifest" Path="extension/package.json" Addressable="true"/>
    <Asset Type="Microsoft.VisualStudio.Services.Content.Details" Path="extension/README.md" Addressable="true"/>
    <Asset Type="Microsoft.VisualStudio.Services.Content.Changelog" Path="extension/CHANGELOG.md" Addressable="true"/>
    <Asset Type="Microsoft.VisualStudio.Services.Content.License" Path="extension/LICENSE" Addressable="true"/>
    <Asset Type="Microsoft.VisualStudio.Services.Icons.Default" Path="extension/{pkg['icon']}" Addressable="true"/>
  </Assets>
</PackageManifest>
"""
types = {".json": "application/json", ".js": "application/javascript", ".md": "text/markdown",
         ".png": "image/png", ".vsixmanifest": "text/xml", ".py": "text/x-python", "": "text/plain"}
exts = sorted({p.suffix for p in files} | {".vsixmanifest"})
content_types = '<?xml version="1.0" encoding="utf-8"?>\n<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' + \
    "".join(f'<Default Extension="{e or ".txt"}" ContentType="{types.get(e, "application/octet-stream")}"/>' for e in exts if e) + "</Types>\n"

with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("extension.vsixmanifest", manifest)
    z.writestr("[Content_Types].xml", content_types)
    for p in files:
        z.write(p, "extension/" + p.relative_to(EXT).as_posix())
print(f"wrote {out.name} ({out.stat().st_size // 1024} KB, {len(files)} files)")
