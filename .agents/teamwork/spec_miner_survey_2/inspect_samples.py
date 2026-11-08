import zipfile
import struct
from test_axml import decode_axml, parse_string_pool

def find_specific_features():
    apk_path = r'C:\Users\Siddhu\Downloads\Lightroom.apk'
    with zipfile.ZipFile(apk_path, 'r') as z:
        group_samples = []
        clippath_samples = []
        gradient_samples = []
        filltype_samples = []
        tint_samples = []
        trim_samples = []
        
        xml_files = [n for n in z.namelist() if n.endswith('.xml') and n.startswith('res/')]
        
        for name in xml_files:
            data = z.read(name)
            # Fast string search in pool
            try:
                pos = 0
                htype, hsize, csize = struct.unpack('<HHI', data[pos:pos+8])
                if htype != 0x0003: continue
                strings, _ = parse_string_pool(data, hsize)
                
                if 'vector' in strings:
                    if 'group' in strings and len(group_samples) < 3:
                        group_samples.append((name, decode_axml(data)))
                    if 'clip-path' in strings and len(clippath_samples) < 3:
                        clippath_samples.append((name, decode_axml(data)))
                    if 'gradient' in strings and len(gradient_samples) < 3:
                        gradient_samples.append((name, decode_axml(data)))
                    if 'fillType' in strings and len(filltype_samples) < 3:
                        filltype_samples.append((name, decode_axml(data)))
                    if 'tint' in strings and len(tint_samples) < 3:
                        tint_samples.append((name, decode_axml(data)))
                    if ('trimPathEnd' in strings or 'trimPathStart' in strings) and len(trim_samples) < 3:
                        trim_samples.append((name, decode_axml(data)))
            except Exception:
                continue

        print("=== GROUP SAMPLES ===")
        for name, xml in group_samples:
            print(f"--- {name} ---")
            print(xml[:800])

        print("\n=== CLIP-PATH SAMPLES ===")
        for name, xml in clippath_samples:
            print(f"--- {name} ---")
            print(xml[:800])

        print("\n=== GRADIENT SAMPLES ===")
        for name, xml in gradient_samples:
            print(f"--- {name} ---")
            print(xml[:800])

        print("\n=== FILLTYPE SAMPLES ===")
        for name, xml in filltype_samples:
            print(f"--- {name} ---")
            print(xml[:800])

        print("\n=== TINT SAMPLES ===")
        for name, xml in tint_samples:
            print(f"--- {name} ---")
            print(xml[:800])

        print("\n=== TRIM PATH SAMPLES ===")
        for name, xml in trim_samples:
            print(f"--- {name} ---")
            print(xml[:800])

if __name__ == '__main__':
    find_specific_features()
