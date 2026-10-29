import os
import re
import xml.etree.ElementTree as ET

svg_dir = r'c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference\Svg Icons'
sprite_path = os.path.join(svg_dir, 'lightroom-all-sprite.svg')

if os.path.exists(sprite_path):
    with open(sprite_path, 'r', encoding='utf-8', errors='ignore') as f:
        text = f.read()
    symbols = re.findall(r'<symbol\s+id=[\'"]([^\'"]+)[\'"]', text)
    print(f"lightroom-all-sprite.svg contains {len(symbols)} symbols:")
    for s in symbols[:20]:
        print(f"  - {s}")
    if len(symbols) > 20:
        print(f"  ... and {len(symbols) - 20} more symbols")
