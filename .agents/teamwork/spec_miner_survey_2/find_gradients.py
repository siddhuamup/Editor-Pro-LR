import sys
sys.path.append(r'.agents\teamwork\spec_miner_survey_2')
import zipfile
from test_axml import decode_axml, parse_string_pool
import struct

apk_path = r'C:\Users\Siddhu\Downloads\Lightroom.apk'
with zipfile.ZipFile(apk_path, 'r') as z:
    for name in z.namelist():
        if name.startswith('res/') and name.endswith('.xml'):
            data = z.read(name)
            try:
                pos = 0
                htype, hsize, csize = struct.unpack('<HHI', data[pos:pos+8])
                if htype != 0x0003: continue
                strings, _ = parse_string_pool(data, hsize)
                if 'gradient' in strings and any(x in strings for x in ['vector', 'shape', 'path']):
                    xml = decode_axml(data)
                    print(f"=== {name} ===")
                    print(xml[:500])
            except Exception:
                continue
