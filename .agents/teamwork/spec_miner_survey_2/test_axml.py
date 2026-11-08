import zipfile
import struct
import sys

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

def decode_axml(data):
    pos = 0
    htype, hsize, csize = struct.unpack('<HHI', data[pos:pos+8])
    pos += hsize
    
    strings, pos = parse_string_pool(data, pos)
    
    # Resource map (optional)
    if pos < len(data):
        rm_type, rm_hsize, rm_csize = struct.unpack('<HHI', data[pos:pos+8])
        if rm_type == 0x0180:
            pos += rm_csize
            
    xml_lines = []
    indent = 0
    ns_map = {}
    root_element = True
    
    while pos < len(data):
        n_type, n_hsize, n_size = struct.unpack('<HHI', data[pos:pos+8])
        if n_type == 0x0100: # START_NAMESPACE
            prefix_idx, uri_idx = struct.unpack('<II', data[pos+16:pos+24])
            prefix = strings[prefix_idx] if prefix_idx < len(strings) else ''
            uri = strings[uri_idx] if uri_idx < len(strings) else ''
            ns_map[uri] = prefix
        elif n_type == 0x0101: # END_NAMESPACE
            pass
        elif n_type == 0x0102: # START_ELEMENT
            line_no, comment_idx, ns_idx, name_idx = struct.unpack('<IIII', data[pos+8:pos+24])
            attr_start, attr_size, attr_count, id_idx, class_idx, style_idx = struct.unpack('<HHHHHH', data[pos+24:pos+36])
            elem_name = strings[name_idx]
            
            attrs = []
            for i in range(attr_count):
                a_pos = pos + 36 + i * 20
                a_ns, a_name, a_raw_val, a_v_size, a_v_res0, a_v_type, a_v_data = struct.unpack('<IIIHBBI', data[a_pos:a_pos+20])
                ns_uri = strings[a_ns] if a_ns != 0xFFFFFFFF and a_ns < len(strings) else ''
                ns_prefix = (ns_map.get(ns_uri, 'android') + ':') if ns_uri else ''
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
                elif a_v_type >= 28 and a_v_type <= 31: # colors
                    val_str = f'#{a_v_data:08X}'
                elif a_v_type == 1: # reference
                    val_str = f'@0x{a_v_data:08x}'
                elif a_v_type == 2: # attribute reference
                    val_str = f'?0x{a_v_data:08x}'
                else:
                    val_str = str(a_v_data)
                attrs.append(f'{ns_prefix}{attr_name}="{val_str}"')
                
            if root_element:
                attrs.append('xmlns:android="http://schemas.android.com/apk/res/android"')
                attrs.append('xmlns:aapt="http://schemas.android.com/aapt"')
                root_element = False
            attr_str = (' ' + ' '.join(attrs)) if attrs else ''
            xml_lines.append('  ' * indent + f'<{elem_name}{attr_str}>')
            indent += 1
        elif n_type == 0x0103: # END_ELEMENT
            indent -= 1
            line_no, comment_idx, ns_idx, name_idx = struct.unpack('<IIII', data[pos+8:pos+24])
            elem_name = strings[name_idx]
            xml_lines.append('  ' * indent + f'</{elem_name}>')
        pos += n_size
    return '\n'.join(xml_lines)

if __name__ == '__main__':
    with zipfile.ZipFile(r'C:\Users\Siddhu\Downloads\Lightroom.apk', 'r') as z:
        for sample in ['res/RUT.xml', 'res/quM.xml', 'res/c5Q.xml']:
            raw = z.read(sample)
            print(f'=== {sample} ===')
            print(decode_axml(raw))
