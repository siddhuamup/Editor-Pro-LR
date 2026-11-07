import os
from PIL import Image

def analyze_image(path):
    if not os.path.exists(path):
        print(f"[MISSING] {path}")
        return
    img = Image.open(path)
    print(f"\n--- Analysis for: {os.path.basename(path)} ---")
    print(f"Path: {path}")
    print(f"Dimensions: {img.width}x{img.height} (Aspect ratio: {img.width/img.height:.4f})")
    print(f"Format: {img.format}, Mode: {img.mode}")
    
    # Sample key regions:
    # Top bar
    top_bar = img.getpixel((img.width // 2, 50))
    print(f"Top bar color at ({img.width // 2}, 50): {top_bar}")
    
    # Center canvas
    center = img.getpixel((img.width // 2, img.height // 2))
    print(f"Center canvas color at ({img.width // 2}, {img.height // 2}): {center}")
    
    # Bottom / right dock
    if img.width < img.height: # Portrait
        dock = img.getpixel((img.width // 2, img.height - 40))
        print(f"Bottom dock color at ({img.width // 2}, {img.height - 40}): {dock}")
    else: # Landscape
        dock = img.getpixel((img.width - 40, img.height // 2))
        print(f"Right dock color at ({img.width - 40}, {img.height // 2}): {dock}")

images = [
    r"c:\Users\Siddhu\Desktop\apps testing\emulator_portrait.png",
    r"c:\Users\Siddhu\Desktop\apps testing\emulator_landscape.png",
    r"c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\worker_m5_verification\emulator_portrait_masking.png",
    r"c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\worker_m5_verification\emulator_landscape_masking.png",
]

for p in images:
    analyze_image(p)
