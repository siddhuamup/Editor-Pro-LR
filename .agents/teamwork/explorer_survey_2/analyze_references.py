import os
import numpy as np
from PIL import Image

v_dir = r'c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference'
files = sorted([f for f in os.listdir(v_dir) if f.endswith('.png')])

print(f"{'Idx':<3} {'Filename':<40} {'Dock Active (cx, cy, L, R)':<30} {'Sub Active (cx, cy, L, R)':<30}")

for idx, f in enumerate(files):
    im = Image.open(os.path.join(v_dir, f)).convert('RGB')
    arr = np.array(im)
    h, w, _ = arr.shape
    
    # Dock: y from h-260 to h-40
    dock_arr = arr[h-260:h-40, :, :]
    # Blue mask: b > 160 and b > r + 60 and g > 80
    dock_mask = (dock_arr[:, :, 2].astype(int) > 160) & \
                (dock_arr[:, :, 2].astype(int) - dock_arr[:, :, 0].astype(int) > 60) & \
                (dock_arr[:, :, 1].astype(int) > 80)
    
    y_idxs, x_idxs = np.where(dock_mask)
    dock_info = "None"
    if len(x_idxs) > 10:
        min_x, max_x = int(x_idxs.min()), int(x_idxs.max())
        min_y, max_y = int(y_idxs.min() + h - 260), int(y_idxs.max() + h - 260)
        cx = (min_x + max_x) // 2
        cy = (min_y + max_y) // 2
        dock_info = f"cx={cx} cy={cy} [{min_x}..{max_x}]"
        
    # Substrip: y from h-460 to h-260
    sub_arr = arr[h-460:h-260, :, :]
    sub_mask = (sub_arr[:, :, 2].astype(int) > 160) & \
               (sub_arr[:, :, 2].astype(int) - sub_arr[:, :, 0].astype(int) > 60) & \
               (sub_arr[:, :, 1].astype(int) > 80)
    sy_idxs, sx_idxs = np.where(sub_mask)
    sub_info = "None"
    if len(sx_idxs) > 10:
        min_x, max_x = int(sx_idxs.min()), int(sx_idxs.max())
        min_y, max_y = int(sy_idxs.min() + h - 460), int(sy_idxs.max() + h - 460)
        cx = (min_x + max_x) // 2
        cy = (min_y + max_y) // 2
        sub_info = f"cx={cx} cy={cy} [{min_x}..{max_x}]"

    print(f"{idx:<3} {f:<40} {dock_info:<30} {sub_info:<30}")
