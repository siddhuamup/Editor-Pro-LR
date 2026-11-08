#!/usr/bin/env python3
"""
Lightroom APK Vector Icon Mining & SVG Conversion Pipeline.

Extracts all 1,267 authentic VectorDrawable assets from Lightroom.apk,
resolves obfuscated res/*.xml paths to canonical semantic names using resources.arsc,
resolves indirect resource references (@0x...) across all types (strings, dimensions, integers),
and converts them into standard W3C-compliant SVG files saved into 'mined Svg/'.
"""

import os
import sys
import struct
import zipfile
import re
from pathlib import Path
from collections import defaultdict, Counter
import xml.etree.ElementTree as ET

APK_PATH = r"C:\Users\Siddhu\Downloads\Lightroom.apk"
OUTPUT_DIR = Path(r"c:\Users\Siddhu\Desktop\apps testing\mined Svg")

def parse_string_pool(data: bytes, offset: int):
    """Parses Android Binary XML / ARSC StringPool chunk."""
    htype, hsize, csize, scount, stcount, flags, s_start, st_start = struct.unpack('<HHIIIIII', data[offset:offset+28])
    is_utf8 = bool(flags & (1 << 8))
    offsets = struct.unpack(f'<{scount}I', data[offset+28:offset+28+scount*4])
    pool_data = data[offset+s_start:offset+csize]
    strings = []
    for off in offsets:
        try:
            if is_utf8:
                p = off
                char_len = pool_data[p]; p += 1
                if char_len & 0x80:
                    char_len = ((char_len & 0x7f) << 8) | pool_data[p]; p += 1
                byte_len = pool_data[p]; p += 1
                if byte_len & 0x80:
                    byte_len = ((byte_len & 0x7f) << 8) | pool_data[p]; p += 1
                s = pool_data[p:p+byte_len].decode('utf-8', errors='replace')
            else:
                p = off
                char_len = struct.unpack('<H', pool_data[p:p+2])[0]; p += 2
                if char_len & 0x8000:
                    char_len = ((char_len & 0x7fff) << 16) | struct.unpack('<H', pool_data[p:p+2])[0]; p += 2
                s = pool_data[p:p+char_len*2].decode('utf-16le', errors='replace')
            strings.append(s)
        except Exception:
            strings.append('')
    return strings, offset + csize

def parse_arsc(arsc_data: bytes):
    """
    Parses resources.arsc to extract:
    1. file_to_keys: mapping from res/*.xml paths to canonical resource names (e.g. svg_camera)
    2. res_map: mapping from @0x... resource IDs to concrete values (strings, dimensions, integers)
    """
    global_strings, off = parse_string_pool(arsc_data, 12)
    p_type, p_hsize, p_csize, p_id = struct.unpack('<HHII', arsc_data[off:off+12])
    type_strings_off, last_public_type, key_strings_off, last_public_key = struct.unpack('<IIII', arsc_data[off+268:off+284])
    type_strings, _ = parse_string_pool(arsc_data, off + type_strings_off)
    key_strings, _ = parse_string_pool(arsc_data, off + key_strings_off)
    
    drawable_type_id = type_strings.index('drawable') + 1 if 'drawable' in type_strings else -1
    
    file_to_keys = defaultdict(list)
    res_map = {}
    
    pos = off + 288
    while pos < off + p_csize:
        c_type, c_hsize, c_size = struct.unpack('<HHI', arsc_data[pos:pos+8])
        if c_type == 0x0201: # RES_TABLE_TYPE_TYPE
            t_id, res0, res1, entry_count, entries_start = struct.unpack('<BBHII', arsc_data[pos+8:pos+20])
            entry_offsets = struct.unpack(f'<{entry_count}I', arsc_data[pos+c_hsize:pos+c_hsize+entry_count*4])
            
            for idx, e_off in enumerate(entry_offsets):
                if e_off != 0xFFFFFFFF:
                    epos = pos + entries_start + e_off
                    e_size, e_flags, e_key = struct.unpack('<HHI', arsc_data[epos:epos+8])
                    key_name = key_strings[e_key] if e_key < len(key_strings) else f'key_{e_key}'
                    
                    if (e_flags & 1) == 0:
                        v_size, v_res0, v_type = struct.unpack('<HBB', arsc_data[epos+8:epos+12])
                        v_data = struct.unpack('<I', arsc_data[epos+12:epos+16])[0]
                        
                        res_id = (p_id << 24) | (t_id << 16) | idx
                        res_key = f"@0x{res_id:08x}"
                        
                        if v_type == 3: # string
                            val_str = global_strings[v_data] if v_data < len(global_strings) else ''
                            if t_id == drawable_type_id:
                                file_to_keys[val_str].append(key_name)
                        elif v_type == 16: # int dec
                            val_str = str(v_data)
                        elif v_type == 17: # int hex
                            val_str = f'0x{v_data:x}'
                        elif v_type == 18: # boolean
                            val_str = 'true' if v_data != 0 else 'false'
                        elif v_type == 4: # float
                            val_str = str(struct.unpack('<f', struct.pack('<I', v_data))[0])
                        elif v_type == 5: # dimension
                            unit_types = ['px', 'dp', 'sp', 'pt', 'in', 'mm']
                            radix_mult = [1.0, 1.0/128, 1.0/32768, 1.0/8388608]
                            unit = unit_types[v_data & 0xF] if (v_data & 0xF) < len(unit_types) else ''
                            val = (v_data >> 8) * radix_mult[(v_data >> 4) & 0x3]
                            val_str = f'{val:g}{unit}'
                        elif 28 <= v_type <= 31: # color
                            val_str = f'#{v_data:08X}'
                        else:
                            val_str = str(v_data)
                            
                        if res_key not in res_map or not res_map[res_key]:
                            res_map[res_key] = val_str
                            
        pos += c_size
        
    return file_to_keys, res_map

def decode_axml_to_dict_tree(data: bytes, res_map: dict):
    """
    Decodes compiled Android Binary XML (AXML) directly into a structured node hierarchy.
    Resolves indirect @0x... resource references in attributes via res_map.
    """
    pos = 0
    htype, hsize, csize = struct.unpack('<HHI', data[pos:pos+8])
    pos += hsize
    
    strings, pos = parse_string_pool(data, pos)
    
    if pos < len(data):
        rm_type, rm_hsize, rm_csize = struct.unpack('<HHI', data[pos:pos+8])
        if rm_type == 0x0180:
            pos += rm_csize
            
    stack = []
    root_node = None
    
    while pos < len(data):
        n_type, n_hsize, n_size = struct.unpack('<HHI', data[pos:pos+8])
        if n_type == 0x0102: # START_ELEMENT
            line_no, comment_idx, ns_idx, name_idx = struct.unpack('<IIII', data[pos+8:pos+24])
            attr_start, attr_size, attr_count, id_idx, class_idx, style_idx = struct.unpack('<HHHHHH', data[pos+24:pos+36])
            elem_name = strings[name_idx] if name_idx < len(strings) else f'elem_{name_idx}'
            
            attribs = {}
            for i in range(attr_count):
                a_pos = pos + 36 + i * 20
                a_ns, a_name, a_raw_val, a_v_size, a_v_res0, a_v_type, a_v_data = struct.unpack('<IIIHBBI', data[a_pos:a_pos+20])
                attr_name = strings[a_name] if a_name < len(strings) else f'attr_{a_name}'
                
                # Format attribute value
                if a_raw_val != 0xFFFFFFFF and a_raw_val < len(strings) and strings[a_raw_val]:
                    val_str = strings[a_raw_val]
                elif a_v_type == 3: # string
                    val_str = strings[a_v_data] if a_v_data < len(strings) else ''
                elif a_v_type == 16: # int dec
                    val_str = str(a_v_data)
                elif a_v_type == 17: # int hex
                    val_str = f'0x{a_v_data:x}'
                elif a_v_type == 18: # boolean
                    val_str = 'true' if a_v_data != 0 else 'false'
                elif a_v_type == 4: # float
                    val_str = str(struct.unpack('<f', struct.pack('<I', a_v_data))[0])
                elif a_v_type == 5: # dimension
                    unit_types = ['px', 'dp', 'sp', 'pt', 'in', 'mm']
                    radix_mult = [1.0, 1.0/128, 1.0/32768, 1.0/8388608]
                    unit = unit_types[a_v_data & 0xF] if (a_v_data & 0xF) < len(unit_types) else ''
                    val = (a_v_data >> 8) * radix_mult[(a_v_data >> 4) & 0x3]
                    val_str = f'{val:g}{unit}'
                elif 28 <= a_v_type <= 31: # colors
                    val_str = f'#{a_v_data:08X}'
                elif a_v_type == 1: # reference
                    val_str = f'@0x{a_v_data:08x}'
                elif a_v_type == 2: # attr reference
                    val_str = f'?0x{a_v_data:08x}'
                else:
                    val_str = str(a_v_data)
                    
                # Resolve indirect resource reference
                if val_str.startswith('@0x'):
                    val_str = res_map.get(val_str.lower(), val_str)
                    
                attribs[attr_name] = val_str
                
            node = {'tag': elem_name, 'attrib': attribs, 'children': []}
            if not stack:
                root_node = node
            else:
                stack[-1]['children'].append(node)
            stack.append(node)
            
        elif n_type == 0x0103: # END_ELEMENT
            if stack:
                stack.pop()
                
        pos += n_size
        
    return root_node

def clean_dim(val: str, fallback="24") -> str:
    if not val:
        return fallback
    clean = re.sub(r'[^\d.]', '', str(val))
    return clean if clean else fallback

def safe_float(val, fallback=0.0) -> float:
    try:
        if val is None:
            return fallback
        clean = re.sub(r'[^\d.-]', '', str(val))
        return float(clean) if clean else fallback
    except (ValueError, TypeError):
        return fallback

def parse_color(c_str: str, alpha_mult=1.0):
    """
    Parses Android color representation into (hex_color, opacity).
    Handles #AARRGGBB, #RRGGBB, theme refs (?attr/..., @color/...), and transparent cutouts.
    """
    if not c_str:
        return None, None
    c_str = str(c_str).strip()
    
    # Theme references or unresolved refs default to clean opaque white (Lightroom icon color)
    if c_str.startswith('?') or c_str.startswith('@'):
        return '#ffffff', round(1.0 * alpha_mult, 3)
        
    if c_str.startswith('#'):
        h = c_str[1:]
        if len(h) == 8: # #AARRGGBB
            alpha = (int(h[0:2], 16) / 255.0) * alpha_mult
            if alpha == 0:
                return 'none', 0.0
            return f'#{h[2:8].lower()}', round(alpha, 3)
        elif len(h) == 6: # #RRGGBB
            return f'#{h.lower()}', round(1.0 * alpha_mult, 3)
        elif len(h) == 4: # #ARGB
            alpha = (int(h[0]*2, 16) / 255.0) * alpha_mult
            if alpha == 0:
                return 'none', 0.0
            return f'#{h[1]*2}{h[2]*2}{h[3]*2}'.lower(), round(alpha, 3)
        elif len(h) == 3: # #RGB
            return f'#{h[0]*2}{h[1]*2}{h[2]*2}'.lower(), round(1.0 * alpha_mult, 3)
            
    if c_str in ('0x0', '#00000000'):
        return 'none', 0.0
        
    return c_str, round(1.0 * alpha_mult, 3)

def convert_vector_node_to_svg(root_node: dict) -> ET.Element:
    """Converts a parsed VectorDrawable AST into a standard W3C SVG ElementTree."""
    if root_node['tag'] != 'vector':
        raise ValueError(f"Expected root tag <vector>, found <{root_node['tag']}>")
        
    rattrs = root_node['attrib']
    vw = clean_dim(rattrs.get('viewportWidth'), '24')
    vh = clean_dim(rattrs.get('viewportHeight'), '24')
    w = clean_dim(rattrs.get('width'), vw)
    h = clean_dim(rattrs.get('height'), vh)
    
    svg = ET.Element('svg', {
        'xmlns': 'http://www.w3.org/2000/svg',
        'viewBox': f'0 0 {vw} {vh}',
        'width': w,
        'height': h
    })
    
    # Global vector opacity
    if 'alpha' in rattrs:
        val_alpha = safe_float(rattrs['alpha'], 1.0)
        if val_alpha < 0.999:
            svg.attrib['opacity'] = f'{val_alpha:.3f}'
            
    clip_counter = 0
    defs = None
    
    def process_node(node, parent_elem):
        nonlocal clip_counter, defs
        tag = node['tag']
        catts = node['attrib']
        
        if tag == 'group':
            g = ET.SubElement(parent_elem, 'g')
            
            px = safe_float(catts.get('pivotX'), 0.0)
            py = safe_float(catts.get('pivotY'), 0.0)
            tx = safe_float(catts.get('translateX'), 0.0)
            ty = safe_float(catts.get('translateY'), 0.0)
            rot = safe_float(catts.get('rotation'), 0.0)
            sx = safe_float(catts.get('scaleX'), 1.0)
            sy = safe_float(catts.get('scaleY'), 1.0)
            
            transforms = []
            if px != 0 or py != 0 or tx != 0 or ty != 0 or rot != 0 or sx != 1 or sy != 1:
                if px != 0 or py != 0:
                    transforms.append(f'translate({px + tx:g} {py + ty:g})')
                    if rot != 0:
                        transforms.append(f'rotate({rot:g})')
                    if sx != 1 or sy != 1:
                        transforms.append(f'scale({sx:g} {sy:g})')
                    transforms.append(f'translate({-px:g} {-py:g})')
                else:
                    if tx != 0 or ty != 0:
                        transforms.append(f'translate({tx:g} {ty:g})')
                    if rot != 0:
                        transforms.append(f'rotate({rot:g})')
                    if sx != 1 or sy != 1:
                        transforms.append(f'scale({sx:g} {sy:g})')
                        
            if transforms:
                g.attrib['transform'] = ' '.join(transforms)
                
            for child in node['children']:
                process_node(child, g)
                
        elif tag == 'clip-path':
            clip_counter += 1
            clip_id = f'clip_{clip_counter}'
            if defs is None:
                defs = ET.Element('defs')
                svg.insert(0, defs)
            clip_elem = ET.SubElement(defs, 'clipPath', {'id': clip_id})
            path_data = catts.get('pathData', '')
            ET.SubElement(clip_elem, 'path', {'d': path_data})
            parent_elem.attrib['clip-path'] = f'url(#{clip_id})'
            
        elif tag == 'path':
            pattrs = {}
            if 'pathData' in catts:
                pattrs['d'] = catts['pathData']
                
            # Fill color handling
            fill_color = catts.get('fillColor')
            fill_alpha = safe_float(catts.get('fillAlpha'), 1.0)
            if fill_color:
                fc, fa = parse_color(fill_color, fill_alpha)
                pattrs['fill'] = fc
                if fa is not None and fa < 0.999 and fc != 'none':
                    pattrs['fill-opacity'] = f'{fa:.3f}'
            else:
                pattrs['fill'] = 'none' # Critical: prevent default black SVG fill
                
            # Stroke color handling
            stroke_color = catts.get('strokeColor')
            stroke_alpha = safe_float(catts.get('strokeAlpha'), 1.0)
            if stroke_color:
                sc, sa = parse_color(stroke_color, stroke_alpha)
                pattrs['stroke'] = sc
                if sa is not None and sa < 0.999 and sc != 'none':
                    pattrs['stroke-opacity'] = f'{sa:.3f}'
                if 'strokeWidth' in catts:
                    pattrs['stroke-width'] = clean_dim(catts['strokeWidth'], '1')
                    
            # Stroke caps and joins
            cap = catts.get('strokeLineCap', '')
            cap_map = {'0': 'butt', '1': 'round', '2': 'square'}
            if cap in cap_map:
                pattrs['stroke-linecap'] = cap_map[cap]
            elif cap in ('butt', 'round', 'square'):
                pattrs['stroke-linecap'] = cap
                
            join = catts.get('strokeLineJoin', '')
            join_map = {'0': 'miter', '1': 'round', '2': 'bevel'}
            if join in join_map:
                pattrs['stroke-linejoin'] = join_map[join]
            elif join in ('miter', 'round', 'bevel'):
                pattrs['stroke-linejoin'] = join
                
            if 'strokeMiterLimit' in catts:
                pattrs['stroke-miterlimit'] = catts['strokeMiterLimit']
                
            # Fill rule
            fill_type = catts.get('fillType', '')
            if fill_type in ('1', 'evenOdd'):
                pattrs['fill-rule'] = 'evenodd'
                
            ET.SubElement(parent_elem, 'path', pattrs)
            
    for child in root_node['children']:
        process_node(child, svg)
        
    return svg

def mine_all_svgs():
    """Main extraction and mining orchestration."""
    print("=" * 75)
    print("LIGHTROOM APK SVG ICON MINING ENGINE")
    print(f"Target APK: {APK_PATH}")
    print(f"Destination Directory: {OUTPUT_DIR.resolve()}")
    print("=" * 75)
    
    if not os.path.exists(APK_PATH):
        print(f"FATAL: Source APK does not exist at {APK_PATH}")
        sys.exit(1)
        
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    
    print("\n[Phase 1] Reading APK and resources.arsc...")
    with zipfile.ZipFile(APK_PATH, 'r') as z:
        arsc_raw = z.read('resources.arsc')
        print(f"Loaded resources.arsc ({len(arsc_raw):,} bytes). Parsing symbol tables...")
        file_to_keys, res_map = parse_arsc(arsc_raw)
        print(f"Extracted {len(file_to_keys):,} drawable file mappings and {len(res_map):,} resource entries.")
        
        print("\n[Phase 2] Discovering VectorDrawable XML files...")
        vector_entries = []
        for name in z.namelist():
            if name.startswith('res/') and name.endswith('.xml'):
                data = z.read(name)
                if len(data) >= 8 and struct.unpack('<H', data[:2])[0] == 0x0003:
                    # Quick check for vector string in chunk
                    if b'vector' in data:
                        strings, _ = parse_string_pool(data, 8)
                        if 'vector' in strings:
                            # Verify root element is vector
                            pos = 8
                            _, sp_csize = struct.unpack('<HI', data[pos+2:pos+8])
                            pos += sp_csize
                            if pos < len(data):
                                rm_type, _, rm_csize = struct.unpack('<HHI', data[pos:pos+8])
                                if rm_type == 0x0180: pos += rm_csize
                            while pos < len(data):
                                n_type, _, n_size = struct.unpack('<HHI', data[pos:pos+8])
                                if n_type == 0x0102: # START_ELEMENT
                                    elem_name_idx = struct.unpack('<I', data[pos+20:pos+24])[0]
                                    if elem_name_idx < len(strings) and strings[elem_name_idx] == 'vector':
                                        vector_entries.append((name, data))
                                    break
                                pos += n_size
                                
        print(f"Identified exactly {len(vector_entries):,} <vector> drawables inside APK.")
        
        print("\n[Phase 3] Decoding AXML, translating to W3C SVG, and writing output files...")
        success_count = 0
        error_count = 0
        collision_count = 0
        used_filenames = set()
        category_counter = Counter()
        
        for res_path, raw_axml in vector_entries:
            try:
                # 1. Resolve canonical name
                keys = file_to_keys.get(res_path, [])
                canonical_name = keys[0] if keys else Path(res_path).stem
                
                # Categorize
                prefix = canonical_name.split('_')[0] if '_' in canonical_name else 'other'
                category_counter[prefix] += 1
                
                # Collision handling
                target_filename = f"{canonical_name}.svg"
                if target_filename.lower() in used_filenames:
                    collision_count += 1
                    dup_idx = 2
                    while f"{canonical_name}_{dup_idx}.svg".lower() in used_filenames:
                        dup_idx += 1
                    target_filename = f"{canonical_name}_{dup_idx}.svg"
                    
                used_filenames.add(target_filename.lower())
                
                # 2. Decode AXML & resolve indirect resources
                ast_root = decode_axml_to_dict_tree(raw_axml, res_map)
                
                # 3. Convert to W3C SVG
                svg_tree = convert_vector_node_to_svg(ast_root)
                
                # Format SVG string with proper XML header and indent
                ET.indent(svg_tree, space="  ", level=0)
                svg_content = '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(svg_tree, encoding="utf-8").decode("utf-8")
                
                # 4. Save to destination
                out_path = OUTPUT_DIR / target_filename
                with open(out_path, 'w', encoding='utf-8') as out_f:
                    out_f.write(svg_content)
                    
                success_count += 1
                
            except Exception as e:
                error_count += 1
                print(f"ERROR converting {res_path} ({canonical_name}): {e}")
                
        print("\n" + "=" * 75)
        print("MINING SUMMARY")
        print(f"  Total Vectors Discovered : {len(vector_entries)}")
        print(f"  Successfully Extracted   : {success_count}")
        print(f"  Errors Encountered       : {error_count}")
        print(f"  Name Collisions Resolved : {collision_count}")
        print(f"  Output Directory         : {OUTPUT_DIR.resolve()}")
        print("\nAsset Category Breakdown:")
        for cat, cnt in category_counter.most_common(15):
            print(f"  - {cat}_*: {cnt} icons")
        print("=" * 75)
        
        if error_count > 0:
            print(f"WARNING: {error_count} icons failed conversion.")
            sys.exit(1)
        else:
            print("Extraction finished successfully with 100% conversion rate.")

if __name__ == '__main__':
    mine_all_svgs()
