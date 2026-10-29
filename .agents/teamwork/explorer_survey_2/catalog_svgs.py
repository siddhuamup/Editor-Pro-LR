import os
import re
import xml.etree.ElementTree as ET

svg_base = r'c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference\Svg Icons\01_Lightroom_Mobile_SVG_Pack_24px'
res_drawable = r'c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\app\src\main\res\drawable'

existing_drawables = set(os.listdir(res_drawable)) if os.path.exists(res_drawable) else set()

svg_catalog = []

for root, dirs, files in os.walk(svg_base):
    rel_folder = os.path.relpath(root, svg_base)
    if rel_folder == '.':
        continue
    for f in sorted(files):
        if not f.endswith('.svg'):
            continue
        p = os.path.join(root, f)
        try:
            with open(p, 'r', encoding='utf-8', errors='ignore') as fp:
                raw_xml = fp.read()
            # replace naked & with &amp; if not already &amp;
            fixed_xml = re.sub(r'&(?!(amp|lt|gt|quot|apos);)', '&amp;', raw_xml)
            
            tree = ET.fromstring(fixed_xml)
            
            viewBox = tree.attrib.get('viewBox', '0 0 24 24')
            width = tree.attrib.get('width', '24')
            height = tree.attrib.get('height', '24')
            id_attr = tree.attrib.get('id', '')
            data_name = tree.attrib.get('data-name', '')
            
            # Title & Desc
            title_el = tree.find('.//{*}title')
            desc_el = tree.find('.//{*}desc')
            title_text = title_el.text if title_el is not None and title_el.text else data_name
            desc_text = desc_el.text if desc_el is not None and desc_el.text else ""
            
            paths = tree.findall('.//{*}path')
            circles = tree.findall('.//{*}circle')
            rects = tree.findall('.//{*}rect')
            polys = tree.findall('.//{*}polygon')
            lines = tree.findall('.//{*}line')
            
            elem_summary = []
            if paths: elem_summary.append(f"{len(paths)} path")
            if circles: elem_summary.append(f"{len(circles)} circle")
            if rects: elem_summary.append(f"{len(rects)} rect")
            if polys: elem_summary.append(f"{len(polys)} poly")
            if lines: elem_summary.append(f"{len(lines)} line")
            
            # Extract all path 'd' attributes
            path_ds = [p.attrib.get('d', '') for p in paths if 'd' in p.attrib]
            
            clean_name = f.replace('.svg', '').lower().replace('-', '_').replace(' ', '_')
            candidates = [
                f"{clean_name}.xml",
                f"ic_lr_{clean_name}.xml",
                f"ic_{clean_name}.xml"
            ]
            matched_drawable = None
            for c in candidates:
                if c in existing_drawables:
                    matched_drawable = c
                    break
            if not matched_drawable:
                # substring match
                for d in existing_drawables:
                    d_base = d.replace('.xml', '').replace('ic_lr_', '').replace('ic_', '')
                    if d_base == clean_name or clean_name in d_base or d_base in clean_name:
                        matched_drawable = d
                        break

            svg_catalog.append({
                'category': rel_folder,
                'filename': f,
                'id_attr': id_attr,
                'data_name': data_name,
                'title': title_text,
                'desc': desc_text,
                'viewBox': viewBox,
                'dimensions': f"{width}x{height}",
                'elements': ", ".join(elem_summary),
                'clean_name': clean_name,
                'path_count': len(paths),
                'matched_drawable': matched_drawable,
                'path_sample': path_ds[0][:40] + "..." if path_ds else ""
            })
        except Exception as e:
            print(f"Error parsing {f}: {e}")

print(f"Total SVGs successfully cataloged: {len(svg_catalog)}")

# Check matched vs missing
matched = [s for s in svg_catalog if s['matched_drawable']]
missing = [s for s in svg_catalog if not s['matched_drawable']]
print(f"Matched in res/drawable: {len(matched)}")
print(f"Missing from res/drawable: {len(missing)}")

if missing:
    print("\nMissing SVGs:")
    for m in missing:
        print(f"  [{m['category']}] {m['filename']} (Title: {m['title']})")
