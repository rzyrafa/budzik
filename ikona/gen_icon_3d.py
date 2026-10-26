"""Builds the 3D launcher icon PNGs from budzik-ikona-3d-zrodlo.jpg (bells inside a dark circle)."""
import os
import sys

from PIL import Image, ImageDraw

root = sys.argv[1]
res = os.path.join(root, 'app', 'src', 'main', 'res')
out_dir = os.path.join(root, 'ikona')

# Dark circle in the source image: centre and radius in px (a bit inside the real edge, so no white fringe)
CX, CY, R = 1000, 1003, 985
SS = 4  # supersampling for an anti-aliased circular mask

# Adaptive icon: 108dp canvas over a full-bleed background of the circle's edge colour. The circle (80dp) is a bit
# bigger than the visible mask (~72dp), so its edge blends into the background and the bells stay in the 66dp safe zone.
# A transparent margin would make Pixel Launcher add its own coloured ring around the icon.
DENSITIES = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
CANVAS_DP, CIRCLE_DP = 108, 80

src = Image.open(os.path.join(out_dir, 'budzik-ikona-3d-zrodlo.jpg')).convert('RGB')
disc = src.crop((CX - R, CY - R, CX + R, CY + R))


def round_icon(size):
    """The circle scaled to size x size, transparent outside."""
    img = disc.resize((size, size), Image.LANCZOS).convert('RGBA')
    mask = Image.new('L', (size * SS, size * SS), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size * SS - 1, size * SS - 1), fill=255)
    img.putalpha(mask.resize((size, size), Image.LANCZOS))
    return img


for name, scale in DENSITIES.items():
    canvas = round(CANVAS_DP * scale)
    circle = round(CIRCLE_DP * scale)
    fg = Image.new('RGBA', (canvas, canvas), (0, 0, 0, 0))
    off = (canvas - circle) // 2
    fg.alpha_composite(round_icon(circle), (off, off))
    folder = os.path.join(res, f'mipmap-{name}')
    os.makedirs(folder, exist_ok=True)
    fg.save(os.path.join(folder, 'ic_launcher_foreground.png'), optimize=True)

for size in (192, 512):
    round_icon(size).save(os.path.join(out_dir, f'budzik-ikona-{size}.png'), optimize=True)
print('ok')
