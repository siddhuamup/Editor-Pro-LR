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

def analyze_axml_tags_and_attrs(data):
    try:
        pos = 0
        htype, hsize, csize = struct.unpack('<HHI', data[pos:pos+8])
        if htype != 0x0003:
            return None
        pos += hsize
        strings, pos = parse_string_pool(data, pos)
        
        # Resource map (optional)
        if pos < len(data):
            rm_type, rm_hsize, rm_csize = struct.unpack('<HHI', data[pos:pos+8])
            if rm_type == 0x0180:
                pos += rm_csize
                
        tags = Counter()
        tag_attrs = defaultdict(Counter)
        root_tag = None
        
        while pos < len(data):
            n_type, n_hsize, n_size = struct.unpack('<HHI', data[pos:pos+8])
            if n_type == 0x0102: # START_ELEMENT
                line_no, comment_idx, ns_idx, name_idx = struct.unpack('<IIII', data[pos+8:pos+24])
                attr_start, attr_size, attr_count, id_idx, class_idx, style_idx = struct.unpack('<HHHHHH', data[pos+24:pos+36])
                elem_name = strings[name_idx]
                if root_tag is None:
                    root_tag = elem_name
                tags[elem_name] += 1
                for i in range(attr_count):
                    a_pos = pos + 36 + i * 20
                    a_ns, a_name, a_raw_val, a_v_size, a_v_res0, a_v_type, a_v_data = struct.unpack('<IIIHBBI', data[a_pos:a_pos+20])
                    attr_name = strings[a_name] if a_name < len(strings) else f'attr_{a_name}'
                    tag_attrs[elem_name][attr_name] += 1
            pos += n_size
        return root_tag, tags, tag_attrs
    except Exception as e:
        return None

def main():
    apk_path = r'C:\Users\Siddhu\Downloads\Lightroom.apk'
    with zipfile.ZipFile(apk_path, 'r') as z:
        xml_files = [n for n in z.namelist() if n.endswith('.xml')]
        print(f'Total XML files in APK: {len(xml_files)}')
        
        root_tags = Counter()
        vector_subtags = Counter()
        vector_attrs = defaultdict(Counter)
        vector_count = 0
        vector_files = []
        
        for name in xml_files:
            data = z.read(name)
            res = analyze_axml_tags_and_attrs(data)
            if res is None:
                continue
            root, tags, tag_attrs = res
            root_tags[root] += 1
            if root == 'vector':
                vector_count += 1
                vector_files.append(name)
                for t, c in tags.items():
                    vector_subtags[t] += c
                for t, attrs in tag_attrs.items():
                    for a, c in attrs.items():
                        vector_attrs[t][a] += c
                        
        print(f'\nRoot tags breakdown across ALL XMLs:')
        for r, c in root_tags.most_common(20):
            print(f'  <{r}>: {c}')
            
        print(f'\nTotal <vector> drawables: {vector_count}')
        print(f'\nTags found inside <vector> drawables:')
        for t, c in vector_subtags.most_common():
            print(f'  <{t}>: {c}')
            
        print(f'\nAttributes per tag inside <vector> drawables:')
        for t in sorted(vector_attrs.keys()):
            print(f'  Tag <{t}>:')
            for a, c in vector_attrs[t].most_common():
                print(f'    {a}: {c}')

if __name__ == '__main__':
    main()
