import os
import re

svg_base = r'c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference\Svg Icons\01_Lightroom_Mobile_SVG_Pack_24px'
res_drawable = r'c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\app\src\main\res\drawable'

samples = [
    ('01_Edit_Panels/lr-mobile-auto.svg', 'ic_lr_auto.xml'),
    ('01_Edit_Panels/lr-mobile-light.svg', 'ic_lr_light.xml'),
    ('01_Edit_Panels/lr-mobile-color.svg', 'ic_lr_color.xml'),
    ('01_Edit_Panels/lr-mobile-curve.svg', 'ic_lr_curve.xml'),
    ('02_Masking_and_AI/lr-mobile-masking.svg', 'ic_lr_masking.xml'),
    ('02_Masking_and_AI/lr-mobile-mask-brush.svg', 'ic_lr_mask_brush.xml'),
    ('04_Crop_and_Geometry/lr-mobile-crop.svg', 'ic_lr_crop.xml'),
    ('06_Header_and_Actions/lr-mobile-back-chevron.svg', 'ic_lr_back_chevron.xml'),
    ('11_Slider_and_Adjustment_Icons/lr-mobile-slider-exposure.svg', 'ic_lr_slider_exposure.xml')
]

print("Comparing SVG vector paths with Android XML VectorDrawable paths:")
for svg_rel, xml_name in samples:
    svg_p = os.path.join(svg_base, svg_rel.replace('/', os.sep))
    xml_p = os.path.join(res_drawable, xml_name)
    if not os.path.exists(svg_p) or not os.path.exists(xml_p):
        print(f"File not found: {svg_p} or {xml_p}")
        continue
        
    with open(svg_p, 'r', encoding='utf-8', errors='ignore') as f:
        svg_txt = f.read()
    with open(xml_p, 'r', encoding='utf-8', errors='ignore') as f:
        xml_txt = f.read()
        
    svg_paths = re.findall(r'\bd=["\']([^"\']+)["\']', svg_txt)
    xml_paths = re.findall(r'android:pathData=["\']([^"\']+)["\']', xml_txt)
    
    print(f"\n{svg_rel} <-> {xml_name}:")
    print(f"  SVG path count: {len(svg_paths)}, XML path count: {len(xml_paths)}")
    for i, (sp, xp) in enumerate(zip(svg_paths, xml_paths)):
        sp_clean = sp.strip()
        xp_clean = xp.strip()
        match = "EXACT MATCH" if sp_clean == xp_clean else "DIFFERENT"
        print(f"    Path #{i}: {match}")
        if match != "EXACT MATCH":
            print(f"      SVG: {sp_clean[:60]}")
            print(f"      XML: {xp_clean[:60]}")
