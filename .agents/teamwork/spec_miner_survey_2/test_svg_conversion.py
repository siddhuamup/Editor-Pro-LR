import sys
sys.path.append(r'.agents\teamwork\spec_miner_survey_2')
import zipfile
import re
import xml.etree.ElementTree as ET
from test_axml import decode_axml
from map_vectors import get_arsc_mapping

def format_color(hex_str):
    """Converts Android #AARRGGBB or #RRGGBB to (svg_color, opacity)"""
    if not hex_str or not hex_str.startswith('#'):
        return hex_str, 1.0
    val = hex_str[1:]
    if len(val) == 8:
        a = int(val[0:2], 16) / 255.0
        r = int(val[2:4], 16)
        g = int(val[4:6], 16)
        b = int(val[6:8], 16)
        return f"#{r:02x}{g:02x}{b:02x}", a
    elif len(val) == 6:
        return f"#{val.lower()}", 1.0
    return hex_str, 1.0

def convert_vector_to_svg(vector_xml_str):
    """Converts decoded VectorDrawable XML to standard SVG XML"""
    root = ET.fromstring(vector_xml_str)
    
    width_str = root.attrib.get('{http://schemas.android.com/apk/res/android}width', root.attrib.get('android:width', '24dp'))
    height_str = root.attrib.get('{http://schemas.android.com/apk/res/android}height', root.attrib.get('android:height', '24dp'))
    vw_str = root.attrib.get('{http://schemas.android.com/apk/res/android}viewportWidth', root.attrib.get('android:viewportWidth', '24'))
    vh_str = root.attrib.get('{http://schemas.android.com/apk/res/android}viewportHeight', root.attrib.get('android:viewportHeight', '24'))
    
    # Strip 'dp' or 'px'
    width = re.sub(r'[^\d.]', '', width_str)
    height = re.sub(r'[^\d.]', '', height_str)
    
    svg = ET.Element('svg', {
        'xmlns': 'http://www.w3.org/2000/svg',
        'viewBox': f'0 0 {vw_str} {vh_str}',
        'width': width,
        'height': height
    })
    
    clip_counter = 0
    defs = None
    
    def process_element(elem, parent_svg):
        nonlocal clip_counter, defs
        tag = elem.tag.split('}')[-1]
        
        if tag == 'group':
            g = ET.SubElement(parent_svg, 'g')
            # Check transforms
            tx = float(elem.attrib.get('{http://schemas.android.com/apk/res/android}translateX', elem.attrib.get('android:translateX', '0')))
            ty = float(elem.attrib.get('{http://schemas.android.com/apk/res/android}translateY', elem.attrib.get('android:translateY', '0')))
            px = float(elem.attrib.get('{http://schemas.android.com/apk/res/android}pivotX', elem.attrib.get('android:pivotX', '0')))
            py = float(elem.attrib.get('{http://schemas.android.com/apk/res/android}pivotY', elem.attrib.get('android:pivotY', '0')))
            sx = float(elem.attrib.get('{http://schemas.android.com/apk/res/android}scaleX', elem.attrib.get('android:scaleX', '1')))
            sy = float(elem.attrib.get('{http://schemas.android.com/apk/res/android}scaleY', elem.attrib.get('android:scaleY', '1')))
            rot = float(elem.attrib.get('{http://schemas.android.com/apk/res/android}rotation', elem.attrib.get('android:rotation', '0')))
            
            transforms = []
            if tx != 0 or ty != 0 or px != 0 or py != 0:
                if px != 0 or py != 0:
                    transforms.append(f'translate({tx + px} {ty + py})')
                else:
                    transforms.append(f'translate({tx} {ty})')
            if rot != 0:
                transforms.append(f'rotate({rot})')
            if sx != 1 or sy != 1:
                transforms.append(f'scale({sx} {sy})')
            if px != 0 or py != 0:
                transforms.append(f'translate({-px} {-py})')
                
            if transforms:
                g.attrib['transform'] = ' '.join(transforms)
                
            for child in elem:
                process_element(child, g)
                
        elif tag == 'clip-path':
            clip_counter += 1
            clip_id = f'clip_{clip_counter}'
            if defs is None:
                defs = ET.Element('defs')
                svg.insert(0, defs)
            clip_elem = ET.SubElement(defs, 'clipPath', {'id': clip_id})
            path_data = elem.attrib.get('{http://schemas.android.com/apk/res/android}pathData', elem.attrib.get('android:pathData', ''))
            ET.SubElement(clip_elem, 'path', {'d': path_data})
            parent_svg.attrib['clip-path'] = f'url(#{clip_id})'
            
        elif tag == 'path':
            p_data = elem.attrib.get('{http://schemas.android.com/apk/res/android}pathData', elem.attrib.get('android:pathData', ''))
            fc = elem.attrib.get('{http://schemas.android.com/apk/res/android}fillColor', elem.attrib.get('android:fillColor', ''))
            fa = float(elem.attrib.get('{http://schemas.android.com/apk/res/android}fillAlpha', elem.attrib.get('android:fillAlpha', '1.0')))
            sc = elem.attrib.get('{http://schemas.android.com/apk/res/android}strokeColor', elem.attrib.get('android:strokeColor', ''))
            sa = float(elem.attrib.get('{http://schemas.android.com/apk/res/android}strokeAlpha', elem.attrib.get('android:strokeAlpha', '1.0')))
            sw = elem.attrib.get('{http://schemas.android.com/apk/res/android}strokeWidth', elem.attrib.get('android:strokeWidth', ''))
            ft = elem.attrib.get('{http://schemas.android.com/apk/res/android}fillType', elem.attrib.get('android:fillType', ''))
            
            p_attrib = {'d': p_data}
            
            # Fill
            if fc and fc not in ('#00000000', '0x0'):
                color, alpha = format_color(fc)
                p_attrib['fill'] = color
                total_fa = alpha * fa
                if total_fa < 0.999:
                    p_attrib['fill-opacity'] = f'{total_fa:.3f}'
            else:
                p_attrib['fill'] = 'none'
                
            # Stroke
            if sc and sc not in ('#00000000', '0x0'):
                color, alpha = format_color(sc)
                p_attrib['stroke'] = color
                total_sa = alpha * sa
                if total_sa < 0.999:
                    p_attrib['stroke-opacity'] = f'{total_sa:.3f}'
                if sw:
                    p_attrib['stroke-width'] = sw
            
            # FillType
            if ft in ('1', 'evenOdd'):
                p_attrib['fill-rule'] = 'evenodd'
                
            # Stroke Caps/Joins
            cap = elem.attrib.get('{http://schemas.android.com/apk/res/android}strokeLineCap', elem.attrib.get('android:strokeLineCap', ''))
            cap_map = {'0': 'butt', '1': 'round', '2': 'square'}
            if cap in cap_map:
                p_attrib['stroke-linecap'] = cap_map[cap]
            elif cap in ('butt', 'round', 'square'):
                p_attrib['stroke-linecap'] = cap
                
            join = elem.attrib.get('{http://schemas.android.com/apk/res/android}strokeLineJoin', elem.attrib.get('android:strokeLineJoin', ''))
            join_map = {'0': 'miter', '1': 'round', '2': 'bevel'}
            if join in join_map:
                p_attrib['stroke-linejoin'] = join_map[join]
            elif join in ('miter', 'round', 'bevel'):
                p_attrib['stroke-linejoin'] = join
                
            ET.SubElement(parent_svg, 'path', p_attrib)

    for child in root:
        process_element(child, svg)
        
    return ET.tostring(svg, encoding='utf-8').decode('utf-8')

def main():
    apk_path = r'C:\Users\Siddhu\Downloads\Lightroom.apk'
    with zipfile.ZipFile(apk_path, 'r') as z:
        for sample in ['res/RUT.xml', 'res/quM.xml', 'res/c5Q.xml', 'res/2wa.xml', 'res/akH.xml']:
            raw = z.read(sample)
            dec_xml = decode_axml(raw)
            svg_out = convert_vector_to_svg(dec_xml)
            print(f"=== SVG for {sample} ===")
            print(svg_out[:600])
            # Validate SVG
            parsed = ET.fromstring(svg_out)
            print(f"Validation: SUCCESS (tag={parsed.tag}, children={len(list(parsed))})")

if __name__ == '__main__':
    main()
