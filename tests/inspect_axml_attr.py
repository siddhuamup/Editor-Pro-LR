#!/usr/bin/env python3
"""Inspect why raw integers/paths ended up in attributes."""
import zipfile
import struct
from pathlib import Path

APK_PATH = r"C:\Users\Siddhu\Downloads\Lightroom.apk"

def inspect_file():
    with zipfile.ZipFile(APK_PATH, 'r') as z:
        arsc_raw = z.read('resources.arsc')
        import sys
        sys.path.append('.')
        from mine_apk_svgs import parse_arsc, parse_string_pool
        file_to_keys, res_map = parse_arsc(arsc_raw)
        
        # Find res path for spectrum_switch_default_thumb_selected_disabled
        target_res = None
        for rpath, keys in file_to_keys.items():
            if 'spectrum_switch_default_thumb_selected_disabled' in keys:
                target_res = rpath
                break
                
        print(f"Target res: {target_res}")
        if target_res:
            data = z.read(target_res)
            strings, pos = parse_string_pool(data, 8)
            print(f"Strings in target: {strings[:20]}")
            # Scan elements
            if pos < len(data):
                rm_type, _, rm_csize = struct.unpack('<HHI', data[pos:pos+8])
                if rm_type == 0x0180: pos += rm_csize
            while pos < len(data):
                n_type, _, n_size = struct.unpack('<HHI', data[pos:pos+8])
                if n_type == 0x0102: # START_ELEMENT
                    attr_count = struct.unpack('<H', data[pos+28:pos+30])[0]
                    name_idx = struct.unpack('<I', data[pos+20:pos+24])[0]
                    tag = strings[name_idx]
                    print(f"Element: <{tag}> with {attr_count} attrs")
                    for i in range(attr_count):
                        a_pos = pos + 36 + i * 20
                        a_ns, a_name, a_raw_val, a_v_size, a_v_res0, a_v_type, a_v_data = struct.unpack('<IIIHBBI', data[a_pos:a_pos+20])
                        attr_name = strings[a_name]
                        print(f"  attr {attr_name}: raw_val={a_raw_val}, type=0x{a_v_type:02x} ({a_v_type}), data=0x{a_v_data:08x} ({a_v_data})")
                pos += n_size

if __name__ == '__main__':
    inspect_file()
