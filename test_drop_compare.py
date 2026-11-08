from PIL import Image, ImageDraw
import math

# We want to sample points along the bezier curve and compare with blur_icon_single.png
def cubic_bezier(p0, p1, p2, p3, t):
    x = (1-t)**3 * p0[0] + 3*(1-t)**2 * t * p1[0] + 3*(1-t) * t**2 * p2[0] + t**3 * p3[0]
    y = (1-t)**3 * p0[1] + 3*(1-t)**2 * t * p1[1] + 3*(1-t) * t**2 * p2[1] + t**3 * p3[1]
    return (x, y)

# Path 1: standard Lightroom tint drop
# M12 3.5s6.5 6.8 6.5 11.2a6.5 6.5 0 1 1-13 0C5.5 10.3 12 3.5 12 3.5z

# Path 2: Sleeker drop matching aspect ratio 0.66
# M12,3 C12,3 17.5,9.5 17.5,14.5 A5.5,5.5 0 1,1 6.5,14.5 C6.5,9.5 12,3 12,3 Z

svg2 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="240" height="240">
  <rect width="24" height="24" fill="#1e1e1e"/>
  <path d="M12,3.2 C12,3.2 17.5,9.5 17.5,14.5 A5.5,5.5 0 1,1 6.5,14.5 C6.5,9.5 12,3.2 12,3.2 Z" fill="#ffffff"/>
</svg>"""

with open("drop_sleek.svg", "w") as f:
    f.write(svg2)

print("Saved drop_sleek.svg")
