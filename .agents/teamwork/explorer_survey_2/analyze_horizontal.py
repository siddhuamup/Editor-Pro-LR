import os
import numpy as np
from PIL import Image

h_dir = r'c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference\horizontal screen UI(same as verticle)'
files = sorted([f for f in os.listdir(h_dir) if f.endswith('.png')])

print(f"{'Idx':<3} {'Filename':<42} {'Size':<12} {'Dock Active Tool Y (cy)':<25} {'Panel X-range':<25}")

for idx, f in enumerate(files):
    im = Image.open(os.path.join(h_dir, f)).convert('RGB')
    arr = np.array(im)
    h, w, _ = arr.shape
    if w < h:
        # vertical image in horizontal folder
        print(f"{idx:<3} {f:<42} {f'{w}x{h}':<12} {'[Vertical orientation]':<25}")
        continue
        
    # Right dock: X from w-160 to w
    dock_arr = arr[:, w-160:w, :]
    dock_mask = (dock_arr[:, :, 2].astype(int) > 160) & \
                (dock_arr[:, :, 2].astype(int) - dock_arr[:, :, 0].astype(int) > 60) & \
                (dock_arr[:, :, 1].astype(int) > 80)
    
    y_idxs, x_idxs = np.where(dock_mask)
    dock_info = "None"
    if len(y_idxs) > 10:
        min_y, max_y = int(y_idxs.min()), int(y_idxs.max())
        cy = (min_y + max_y) // 2
        # map cy to tool index: (cy - 154) / 151
        tool_idx = round((cy - 154) / 151)
        dock_info = f"cy={cy} (slot {tool_idx})"

    # Panel active elements (e.g. blue sliders / toggles in X from 1200 to w-160)
    panel_arr = arr[:, 1000:w-160, :]
    panel_mask = (panel_arr[:, :, 2].astype(int) > 160) & \
                 (panel_arr[:, :, 2].astype(int) - panel_arr[:, :, 0].astype(int) > 60) & \
                 (panel_arr[:, :, 1].astype(int) > 80)
    py_idxs, px_idxs = np.where(panel_mask)
    panel_info = "None"
    if len(px_idxs) > 10:
        min_px = int(px_idxs.min() + 1000)
        max_px = int(px_idxs.max() + 1000)
        panel_info = f"X=[{min_px}..{max_px}] ({len(px_idxs)} px)"

    print(f"{idx:<3} {f:<42} {f'{w}x{h}':<12} {dock_info:<25} {panel_info:<25}")
