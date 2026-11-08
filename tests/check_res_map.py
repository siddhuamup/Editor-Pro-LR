import zipfile, sys
sys.path.append('.')
from mine_apk_svgs import parse_arsc

with zipfile.ZipFile(r"C:\Users\Siddhu\Downloads\Lightroom.apk", 'r') as z:
    file_to_keys, res_map = parse_arsc(z.read('resources.arsc'))
    print("0x7f060bcf:", res_map.get('@0x7f060bcf'))
    print("0x7f060bd0:", res_map.get('@0x7f060bd0'))
