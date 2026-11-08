#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

SVG_DIR = Path("mined Svg")
svg_files = sorted(list(SVG_DIR.glob("*.svg")))

report = []
for p in svg_files:
    root = ET.parse(p).getroot()
    file_issues = []
    for elem in root.iter():
        for attr in ['fill', 'stroke']:
            if attr in elem.attrib:
                val = elem.attrib[attr].strip()
                if val == 'none' or val.startswith('url(') or (val.startswith('#') and len(val) in (4,5,7,9)):
                    continue
                if val in ('currentColor', 'white', 'black', 'transparent', 'red', 'green', 'blue'):
                    continue
                file_issues.append((elem.tag.split('}')[-1], attr, val))
    if file_issues:
        report.append((p.name, file_issues))

print(f"Total affected files: {len(report)}")
for fname, issues in report:
    print(f"{fname}:")
    for tag, attr, val in issues:
        print(f"  <{tag}> {attr}=\"{val}\"")
