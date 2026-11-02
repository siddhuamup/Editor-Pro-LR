import os
import numpy as np
from PIL import Image

v_dir = r'c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference'

for name in ['Screenshot_20261019-083216_Lightroom.png', 'Screenshot_20261019-083218_Lightroom.png', 'Screenshot_20261019-083231_Lightroom.png']:
    p = os.path.join(v_dir, name)
    im = Image.open(p).convert('RGB')
    arr = np.array(im)
    h, w, _ = arr.shape
    # Bottom 300 pixels
    crop_bottom = arr[h-300:h, :, :]
    # Check bounding box of non-black/non-canvas pixels
    # Find rows with content
    row_means = crop_bottom.mean(axis=(1, 2))
    content_rows = np.where(row_means > 10)[0]
    if len(content_rows) > 0:
        y_start = content_rows.min() + h - 300
        y_end = content_rows.max() + h - 300
        print(f"{name}: Bottom content between Y=[{y_start}..{y_end}]")
        # Sample center color
        sample_c = arr[(y_start+y_end)//2, w//2]
        print(f"  Center color at ({w//2}, {(y_start+y_end)//2}): #{sample_c[0]:02X}{sample_c[1]:02X}{sample_c[2]:02X}")
    else:
        print(f"{name}: No bottom content (full black)")
