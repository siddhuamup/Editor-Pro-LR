from pathlib import Path
import xml.etree.ElementTree as ET
import re

color_re = re.compile(
    r"^(#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})|none|currentColor|url\(#[a-zA-Z0-9_\-]+\))$",
    re.IGNORECASE
)

svg_dir = Path("mined Svg")
files = list(svg_dir.glob("*.svg"))
bad = []
paint_count = 0

for p in files:
    root = ET.parse(p).getroot()
    for el in root.iter():
        for a in ("fill", "stroke", "stop-color"):
            if a in el.attrib:
                paint_count += 1
                val = el.attrib[a].strip()
                if not color_re.match(val):
                    bad.append(f"{p.name}: {a}='{val}'")

print(f"Total SVGs scanned: {len(files)}")
print(f"Total paint attributes checked: {paint_count}")
print(f"Invalid paint attributes found: {len(bad)}")
if bad:
    for b in bad[:20]:
        print("  -", b)
    sys.exit(1)
else:
    print("ALL PAINT ATTRIBUTES ARE STRICTLY VALID!")
