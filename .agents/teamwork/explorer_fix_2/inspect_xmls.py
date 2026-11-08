import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
import zipfile
from mine_apk_svgs import APK_PATH, parse_arsc, decode_axml_to_dict_tree


xml_paths = [
    'res/color/adobe_spectrum_dropdown_error_icon.xml',
    'res/color/adobe_spectrum_dropdown_icon_color.xml',
    'res/color/m3_chip_text_color.xml',
    'res/color/adobe_spectrum_bottomsheet_chevron_tint.xml',
    'res/bDm.xml',
    'res/U5B.xml',
    'res/color/adobe_spectrum_clearbutton_icon_color.xml',
    'res/Fdo.xml',
    'res/color/m3_chip_background_color.xml',
    'res/z1w.xml',
    'res/Lm1.xml',
    'res/zwd.xml',
    'res/VdN.xml',
    'res/i4u.xml',
    'res/Vln.xml',
    'res/LlZ.xml',
    'res/AaJ.xml',
    'res/VIJ.xml',
    'res/WRk.xml'
]

with zipfile.ZipFile(APK_PATH, 'r') as z:
    file_to_keys, res_map = parse_arsc(z.read('resources.arsc'))
    for xp in xml_paths:
        try:
            ast = decode_axml_to_dict_tree(z.read(xp), res_map)
            tag = ast['tag']
            ch_count = len(ast['children'])
            print(f"{xp} -> <{tag}> ({ch_count} children)")
            for c in ast['children'][:2]:
                print(f"   <{c['tag']}>: {c['attrib']}")
        except Exception as e:
            print(f"{xp} -> Error: {e}")
