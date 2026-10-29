import os
import numpy as np
from PIL import Image
from collections import Counter

v_dir = r'c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference'
files = sorted([f for f in os.listdir(v_dir) if f.endswith('.png')])

def get_most_common_color(arr, y1, y2, x1, x2):
    crop = arr[y1:y2, x1:x2, :3]
    # flatten to list of (r, g, b)
    pixels = [tuple(p) for p in crop.reshape(-1, 3)]
    # ignore completely transparent or purely dynamic image pixels if needed
    counter = Counter(pixels)
    return counter.most_common(5)

print("Sampling dominant UI colors across 10 key vertical reference screenshots:")

sample_indices = [6, 7, 8, 12, 13, 19, 26, 27, 33, 34]
for idx in sample_indices:
    f = files[idx]
    im = Image.open(os.path.join(v_dir, f)).convert('RGB')
    arr = np.array(im)
    h, w, _ = arr.shape
    
    # Top bar: y=60..150, x=100..w-100
    top_c = get_most_common_color(arr, 60, 150, 100, w-100)[0][0]
    # Bottom dock: y=h-240..h-50, x=50..w-50
    dock_c = get_most_common_color(arr, h-240, h-50, 50, w-50)[0][0]
    # Substrip: y=h-440..h-260, x=50..w-50
    sub_c = get_most_common_color(arr, h-440, h-260, 50, w-50)[0][0]
    # Panel: y=h-850..h-500, x=100..w-100
    panel_c = get_most_common_color(arr, h-850, h-500, 100, w-100)[0][0]
    # Divider between dock and substrip: line at y=h-255
    div_c = get_most_common_color(arr, h-258, h-252, 50, w-50)[0][0]
    
    print(f"\n[{idx:02d}] {f}:")
    print(f"  Topbar:   RGB{top_c} -> #{top_c[0]:02X}{top_c[1]:02X}{top_c[2]:02X}")
    print(f"  Panel:    RGB{panel_c} -> #{panel_c[0]:02X}{panel_c[1]:02X}{panel_c[2]:02X}")
    print(f"  Substrip: RGB{sub_c} -> #{sub_c[0]:02X}{sub_c[1]:02X}{sub_c[2]:02X}")
    print(f"  Dock:     RGB{dock_c} -> #{dock_c[0]:02X}{dock_c[1]:02X}{dock_c[2]:02X}")
    print(f"  Divider:  RGB{div_c} -> #{div_c[0]:02X}{div_c[1]:02X}{div_c[2]:02X}")

# Sample blue accent in screenshots that have blue active highlights
print("\nSampling active blue accent color:")
for idx in [6, 7, 8, 12, 13, 33]:
    f = files[idx]
    im = Image.open(os.path.join(v_dir, f)).convert('RGB')
    arr = np.array(im)
    mask = (arr[:, :, 2].astype(int) > 180) & \
           (arr[:, :, 2].astype(int) - arr[:, :, 0].astype(int) > 80) & \
           (arr[:, :, 1].astype(int) > 100)
    ys, xs = np.where(mask)
    if len(xs) > 0:
        blue_pixels = [tuple(arr[y, x]) for y, x in zip(ys, xs)]
        common_blue = Counter(blue_pixels).most_common(3)
        print(f"[{idx:02d}] {f}:")
        for b_col, count in common_blue:
            print(f"  RGB{b_col} -> #{b_col[0]:02X}{b_col[1]:02X}{b_col[2]:02X} (count={count})")
