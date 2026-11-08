#!/usr/bin/env python3
"""Diagnose invalid color values in mined SVGs."""
from pathlib import Path
import xml.etree.ElementTree as ET
from collections import Counter

SVG_DIR = Path("mined Svg")
svg_files = sorted(list(SVG_DIR.glob("*.svg")))

invalid_fills = Counter()
invalid_strokes = Counter()
files_with_invalid_colors = []

for p in svg_files:
    root = ET.parse(p).getroot()
    has_issue = False
    for elem in root.iter():
        for attr in ['fill', 'stroke']:
            if attr in elem.attrib:
                val = elem.attrib[attr].strip()
                if val == 'none' or val.startswith('url(') or (val.startswith('#') and len(val) in (4,5,7,9)):
                    continue
                if val in ('currentColor', 'white', 'black', 'transparent', 'red', 'green', 'blue'):
                    continue
                if attr == 'fill':
                    invalid_fills[val] += 1
                else:
                    invalid_strokes[val] += 1
                has_issue = True
    if has_issue:
        files_with_invalid_colors.append(p.name)

print(f"Total files with invalid fill/stroke: {len(files_with_invalid_colors)} out of {len(svg_files)}")
print("\nTop invalid fill values:")
for v, c in invalid_fills.most_common(20):
    print(f"  {v}: {c}")

print("\nTop invalid stroke values:")
for v, c in invalid_strokes.most_common(20):
    print(f"  {v}: {c}")

print("\nSample affected files:")
for f in files_with_invalid_colors[:15]:
    print(f"  - {f}")
