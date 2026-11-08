import zipfile
import struct
from collections import Counter, defaultdict

def parse_string_pool(data, offset):
    header_type, header_size, chunk_size, string_count, style_count, flags, strings_start, styles_start = struct.unpack('<HHIIIIII', data[offset:offset+28])
    is_utf8 = bool(flags & (1 << 8))
    offsets = struct.unpack(f'<{string_count}I', data[offset+28:offset+28+string_count*4])
    pool_data = data[offset+strings_start:offset+chunk_size]
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
    return strings, offset + chunk_size

def get_arsc_mapping(arsc_data):
    global_strings, off = parse_string_pool(arsc_data, 12)
    p_type, p_hsize, p_csize, p_id = struct.unpack('<HHII', arsc_data[off:off+12])
    type_strings_off, last_public_type, key_strings_off, last_public_key = struct.unpack('<IIII', arsc_data[off+268:off+284])
    type_strings, _ = parse_string_pool(arsc_data, off + type_strings_off)
    key_strings, _ = parse_string_pool(arsc_data, off + key_strings_off)
    drawable_type_id = type_strings.index('drawable') + 1
    
    pos = off + 288
    file_to_keys = defaultdict(list)
    key_to_files = defaultdict(list)
    
    while pos < off + p_csize:
        c_type, c_hsize, c_size = struct.unpack('<HHI', arsc_data[pos:pos+8])
        if c_type == 0x0201:
            t_id, res0, res1, entry_count, entries_start = struct.unpack('<BBHII', arsc_data[pos+8:pos+20])
            if t_id == drawable_type_id:
                entry_offsets = struct.unpack(f'<{entry_count}I', arsc_data[pos+c_hsize:pos+c_hsize+entry_count*4])
                for idx, e_off in enumerate(entry_offsets):
                    if e_off != 0xFFFFFFFF:
                        epos = pos + entries_start + e_off
                        e_size, e_flags, e_key = struct.unpack('<HHI', arsc_data[epos:epos+8])
                        key_name = key_strings[e_key] if e_key < len(key_strings) else f'key_{e_key}'
                        if (e_flags & 1) == 0:
                            v_size, v_res0, v_type = struct.unpack('<HBB', arsc_data[epos+8:epos+12])
                            v_data = struct.unpack('<I', arsc_data[epos+12:epos+16])[0]
                            if v_type == 3:
                                val_str = global_strings[v_data] if v_data < len(global_strings) else f'str_{v_data}'
                                file_to_keys[val_str].append(key_name)
                                key_to_files[key_name].append(val_str)
        pos += c_size
    return file_to_keys, key_to_files

def main():
    apk_path = r'C:\Users\Siddhu\Downloads\Lightroom.apk'
    with zipfile.ZipFile(apk_path, 'r') as z:
        arsc_data = z.read('resources.arsc')
        file_to_keys, key_to_files = get_arsc_mapping(arsc_data)
        
        # Check all vectors
        vector_files = []
        for name in z.namelist():
            if name.startswith('res/') and name.endswith('.xml'):
                data = z.read(name)
                if len(data) >= 8:
                    htype = struct.unpack('<H', data[:2])[0]
                    if htype == 0x0003:
                        # Check root tag
                        strings, _ = parse_string_pool(data, 8)
                        if 'vector' in strings:
                            # Verify if root element is vector
                            # By checking if vector is among strings and first START_ELEMENT
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
                                        vector_files.append(name)
                                    break
                                pos += n_size

        print(f"Verified <vector> drawables count: {len(vector_files)}")
        mapped_vectors = [f for f in vector_files if f in file_to_keys]
        unmapped_vectors = [f for f in vector_files if f not in file_to_keys]
        print(f"Vectors with ARSC key names: {len(mapped_vectors)}")
        print(f"Vectors without direct ARSC key names: {len(unmapped_vectors)}")
        
        # Breakdown of mapped key name prefixes
        all_vector_keys = []
        for f in mapped_vectors:
            all_vector_keys.extend(file_to_keys[f])
            
        print(f"Total vector key names: {len(all_vector_keys)}")
        prefix_counter = Counter(k.split('_')[0] if '_' in k else 'NO_PREFIX' for k in all_vector_keys)
        print("\nVector Drawable key prefix breakdown:")
        for p, c in prefix_counter.most_common(20):
            print(f"  {p}_: {c}")
            
        # Category breakdown:
        svg_keys = [k for k in all_vector_keys if k.startswith('svg_')]
        ic_keys = [k for k in all_vector_keys if k.startswith('ic_')]
        spectrum_keys = [k for k in all_vector_keys if 'spectrum' in k]
        adobe_keys = [k for k in all_vector_keys if k.startswith('adobe_')]
        mtrl_keys = [k for k in all_vector_keys if 'mtrl' in k or 'material' in k]
        aspect_keys = [k for k in all_vector_keys if 'aspect' in k]
        
        print(f"\nCategorized counts:")
        print(f"  Lightroom UI Icons (svg_*): {len(svg_keys)}")
        print(f"  Standard Icons (ic_*): {len(ic_keys)}")
        print(f"  Spectrum Design Icons (*spectrum*): {len(spectrum_keys)}")
        print(f"  Adobe Brand & System (adobe_*): {len(adobe_keys)}")
        print(f"  Material Design components: {len(mtrl_keys)}")
        print(f"  Aspect ratio icons: {len(aspect_keys)}")

if __name__ == '__main__':
    main()
