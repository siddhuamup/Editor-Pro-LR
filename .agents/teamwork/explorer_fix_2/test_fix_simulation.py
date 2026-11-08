import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
import re
import xml.etree.ElementTree as ET
from mine_apk_svgs import APK_PATH, parse_arsc, decode_axml_to_dict_tree, convert_vector_node_to_svg, safe_float
import zipfile

def proposed_parse_color(c_str: str, alpha_mult=1.0):
    """
    Proposed parse_color() implementation.
    """
    if not c_str:
        return None, None
    c_str = str(c_str).strip()
    
    # 1. Transparent cutouts / zero alpha
    # 17170445 is 0x0106000d (@android:color/transparent)
    if c_str in ('0x0', '#00000000', '17170445', '@0x0106000d', 'transparent') or alpha_mult == 0:
        return 'none', 0.0

    # 2. Hex color strings
    if c_str.startswith('#'):
        h = c_str[1:]
        if len(h) == 8: # #AARRGGBB
            try:
                alpha = (int(h[0:2], 16) / 255.0) * alpha_mult
                if alpha <= 0.001:
                    return 'none', 0.0
                return f'#{h[2:8].lower()}', round(alpha, 3)
            except ValueError:
                return '#ffffff', round(1.0 * alpha_mult, 3)
        elif len(h) == 6: # #RRGGBB
            if all(ch in '0123456789abcdefABCDEF' for ch in h):
                return f'#{h.lower()}', round(1.0 * alpha_mult, 3)
            return '#ffffff', round(1.0 * alpha_mult, 3)
        elif len(h) == 4: # #ARGB
            try:
                alpha = (int(h[0]*2, 16) / 255.0) * alpha_mult
                if alpha <= 0.001:
                    return 'none', 0.0
                return f'#{h[1]*2}{h[2]*2}{h[3]*2}'.lower(), round(alpha, 3)
            except ValueError:
                return '#ffffff', round(1.0 * alpha_mult, 3)
        elif len(h) == 3: # #RGB
            if all(ch in '0123456789abcdefABCDEF' for ch in h):
                return f'#{h[0]*2}{h[1]*2}{h[2]*2}'.lower(), round(1.0 * alpha_mult, 3)
            return '#ffffff', round(1.0 * alpha_mult, 3)

    # 3. Valid W3C paint keywords or url references
    if c_str in ('none', 'currentColor'):
        return c_str, round(1.0 * alpha_mult, 3)
    if c_str.startswith('url('):
        return c_str, round(1.0 * alpha_mult, 3)

    # 4. Unresolved resource IDs (decimal digits e.g. 2130970235), theme refs (?attr, @0x, ?0x),
    #    or ColorStateList/Gradient XML file paths (res/color/..., res/bDm.xml)
    # Default to Lightroom standard icon color: opaque white (#ffffff)
    return '#ffffff', round(1.0 * alpha_mult, 3)

# Test proposed_parse_color on all known defect values
test_cases = [
    ('17170445', 1.0, ('none', 0.0)),
    ('@0x0106000d', 1.0, ('none', 0.0)),
    ('#00000000', 1.0, ('none', 0.0)),
    ('0x0', 1.0, ('none', 0.0)),
    ('#ffffff', 0.0, ('none', 0.0)),
    ('2130970235', 1.0, ('#ffffff', 1.0)),
    ('2130970194', 1.0, ('#ffffff', 1.0)),
    ('res/color/adobe_spectrum_dropdown_error_icon.xml', 1.0, ('#ffffff', 1.0)),
    ('res/bDm.xml', 1.0, ('#ffffff', 1.0)),
    ('@0x7f060b76', 1.0, ('#ffffff', 1.0)),
    ('?0x7f04027b', 1.0, ('#ffffff', 1.0)),
    ('?attr/colorControlNormal', 1.0, ('#ffffff', 1.0)),
    ('#FFFFFF', 1.0, ('#ffffff', 1.0)),
    ('#FF123456', 1.0, ('#123456', 1.0)),
    ('#80123456', 1.0, ('#123456', 0.502)),
    ('none', 1.0, ('none', 1.0)),
    ('currentColor', 1.0, ('currentColor', 1.0)),
    ('url(#gradient)', 1.0, ('url(#gradient)', 1.0)),
]

print("Running test cases on proposed_parse_color:")
all_passed = True
for val, a_mult, expected in test_cases:
    res = proposed_parse_color(val, a_mult)
    ok = (res == expected)
    if not ok:
        print(f"  FAIL: input=({val!r}, {a_mult}) -> got {res}, expected {expected}")
        all_passed = False
    else:
        print(f"  PASS: input=({val!r}, {a_mult}) -> {res}")

if all_passed:
    print("\nAll unit test cases PASSED!")

def run_full_apk_simulation():
    print("\n" + "=" * 80)
    print("RUNNING FULL APK SIMULATION ACROSS ALL 1,267 VECTOR DRAWABLES")
    print("=" * 80)
    
    with zipfile.ZipFile(APK_PATH, 'r') as z:
        arsc_raw = z.read('resources.arsc')
        file_to_keys, res_map = parse_arsc(arsc_raw)
        
        # Monkey-patch parse_color during simulation
        import mine_apk_svgs
        orig_parse_color = mine_apk_svgs.parse_color
        mine_apk_svgs.parse_color = proposed_parse_color
        
        vector_entries = []
        for name in z.namelist():
            if name.startswith('res/') and name.endswith('.xml'):
                data = z.read(name)
                if len(data) >= 8 and struct.unpack('<H', data[:2])[0] == 0x0003 and b'vector' in data:
                    strings, _ = mine_apk_svgs.parse_string_pool(data, 8)
                    if 'vector' in strings:
                        pos = 8
                        _, sp_csize = struct.unpack('<HI', data[pos+2:pos+8])
                        pos += sp_csize
                        if pos < len(data):
                            rm_type, _, rm_csize = struct.unpack('<HHI', data[pos:pos+8])
                            if rm_type == 0x0180: pos += rm_csize
                        while pos < len(data):
                            n_type, _, n_size = struct.unpack('<HHI', data[pos:pos+8])
                            if n_type == 0x0102:
                                elem_name_idx = struct.unpack('<I', data[pos+20:pos+24])[0]
                                if elem_name_idx < len(strings) and strings[elem_name_idx] == 'vector':
                                    vector_entries.append((name, data))
                                break
                            pos += n_size
                            
        print(f"Total vector drawables to test: {len(vector_entries)}")
        
        color_re = re.compile(r'^(#[0-9a-fA-F]{3,8}|none|currentColor|url\(#[a-zA-Z0-9_\-]+\))$')
        invalid_attrs = []
        converted_count = 0
        fill_dist = Counter()
        stroke_dist = Counter()
        
        for name, data in vector_entries:
            try:
                ast_root = decode_axml_to_dict_tree(data, res_map)
                svg_tree = convert_vector_node_to_svg(ast_root)
                converted_count += 1
                
                for el in svg_tree.iter():
                    for attr in ('fill', 'stroke'):
                        val = el.attrib.get(attr)
                        if val is not None:
                            if attr == 'fill':
                                fill_dist[val] += 1
                            else:
                                stroke_dist[val] += 1
                            if not color_re.match(val):
                                invalid_attrs.append((name, attr, val))
            except Exception as e:
                print(f"Error converting {name}: {e}")
                
        # Restore original
        mine_apk_svgs.parse_color = orig_parse_color
        
        print(f"Successfully converted: {converted_count}/{len(vector_entries)}")
        print(f"Total invalid fill/stroke attributes: {len(invalid_attrs)}")
        if invalid_attrs:
            for inv in invalid_attrs[:10]:
                print(f"  [FAIL] {inv}")
        else:
            print("  >>> SUCCESS: 0 invalid paint attributes found! 100% compliant! <<<")
            
        print(f"Distinct fill colors: {len(fill_dist)}")
        print(f"Most common fill values: {fill_dist.most_common(5)}")
        print(f"Most common stroke values: {stroke_dist.most_common(5)}")

import struct
from collections import Counter
run_full_apk_simulation()
