import zipfile
import struct
from collections import Counter

def check_gradients_and_complex():
    apk_path = r'C:\Users\Siddhu\Downloads\Lightroom.apk'
    with zipfile.ZipFile(apk_path, 'r') as z:
        gradient_files = []
        aapt_attr_files = []
        for name in z.namelist():
            if name.endswith('.xml'):
                data = z.read(name)
                if b'gradient' in data.lower():
                    gradient_files.append(name)
                if b'aapt' in data.lower():
                    aapt_attr_files.append(name)
                    
        print(f'XML files containing "gradient": {len(gradient_files)}')
        print(f'Sample gradient files: {gradient_files[:10]}')
        print(f'XML files containing "aapt": {len(aapt_attr_files)}')
        print(f'Sample aapt files: {aapt_attr_files[:10]}')

if __name__ == '__main__':
    check_gradients_and_complex()
