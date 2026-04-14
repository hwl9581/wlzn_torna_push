"""Extract text contours and generate SVG paths from pluginIcon.png."""
from PIL import Image
import base64

img = Image.open(r'D:\Develop\Project\syncApi\src\main\resources\META-INF\pluginIcon.png').convert('RGBA')
w, h = img.size
pixels = img.load()

# Determine background color and shape
# Background is light (~250), text is dark (<80), orange dots
# Find the bounding circle/rect of non-transparent pixels
non_transparent = []
for y in range(h):
    for x in range(w):
        if pixels[x, y][3] > 128:
            non_transparent.append((x, y))

bg_x = [p[0] for p in non_transparent]
bg_y = [p[1] for p in non_transparent]
print(f'Non-transparent bounds: x=[{min(bg_x)},{max(bg_x)}] y=[{min(bg_y)},{max(bg_y)}]')

# Estimate corner radius by checking where the first opaque pixel is in each row
print('\nCorner radius estimation:')
for y in range(0, 20):
    for x in range(w):
        if pixels[x, y][3] > 128:
            print(f'  Row {y}: first opaque at x={x}')
            break

# Detect background color (sample center of light area)
bg_samples = []
for y in [30, 35, 40]:
    for x in [60, 65, 70]:
        r, g, b, a = pixels[x, y]
        bg_samples.append((r, g, b))
avg_bg = tuple(sum(c) // len(bg_samples) for c in zip(*bg_samples))
print(f'\nBackground color: rgb{avg_bg} = #{avg_bg[0]:02x}{avg_bg[1]:02x}{avg_bg[2]:02x}')

# Detect orange color
orange_samples = []
for y in range(h):
    for x in range(w):
        r, g, b, a = pixels[x, y]
        if r > 240 and g > 50 and g < 120 and b < 30 and a > 200:
            orange_samples.append((r, g, b))
if orange_samples:
    avg_or = tuple(sum(c) // len(orange_samples) for c in zip(*orange_samples))
    print(f'Orange color: rgb{avg_or} = #{avg_or[0]:02x}{avg_or[1]:02x}{avg_or[2]:02x}')

# Since hand-tracing paths is impractical, embed the PNG as base64
# but use xlink:href for maximum compatibility
with open(r'D:\Develop\Project\syncApi\src\main\resources\META-INF\pluginIcon.png', 'rb') as f:
    b64 = base64.b64encode(f.read()).decode()

# Create SVG with embedded image
svg = f'''<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="40" height="40" viewBox="0 0 {w} {h}">
  <image width="{w}" height="{h}" xlink:href="data:image/png;base64,{b64}"/>
</svg>
'''

svg_path = r'D:\Develop\Project\syncApi\src\main\resources\META-INF\pluginIcon.svg'
with open(svg_path, 'w') as f:
    f.write(svg)
print(f'\nSVG written ({len(svg)} bytes)')

# Also generate a pure-path version as backup
# Extract dark pixel regions as simple rect-based paths
dark_rects = []
for y in range(h):
    x = 0
    while x < w:
        r, g, b, a = pixels[x, y]
        if a > 200 and r < 100 and g < 100 and b < 100:
            x_start = x
            while x < w:
                r2, g2, b2, a2 = pixels[x, y]
                if not (a2 > 200 and r2 < 100):
                    break
                x += 1
            dark_rects.append((x_start, y, x - x_start, 1))
        else:
            x += 1

# Merge adjacent rects vertically (same x_start and width)
# Build path data from dark_rects
path_data = ' '.join(f'M{r[0]},{r[1]}h{r[2]}v1h-{r[2]}z' for r in dark_rects)

# Find orange pixel rects
orange_rects = []
for y in range(h):
    x = 0
    while x < w:
        r, g, b, a = pixels[x, y]
        if a > 200 and r > 200 and g > 50 and g < 180 and b < 100:
            x_start = x
            while x < w:
                r2, g2, b2, a2 = pixels[x, y]
                if not (a2 > 200 and r2 > 200 and g2 > 50 and g2 < 180 and b2 < 100):
                    break
                x += 1
            orange_rects.append((x_start, y, x - x_start, 1))
        else:
            x += 1

orange_path = ' '.join(f'M{r[0]},{r[1]}h{r[2]}v1h-{r[2]}z' for r in orange_rects)

# Determine background rounded rect path
# Find the outline by checking first/last opaque pixel per row
bg_path_parts = []
for y in range(h):
    first_x = None
    last_x = None
    for x in range(w):
        if pixels[x, y][3] > 128:
            if first_x is None:
                first_x = x
            last_x = x
    if first_x is not None:
        bg_path_parts.append((first_x, last_x, y))

# Build background as a filled shape
bg_outline = f'M{bg_path_parts[0][0]},{bg_path_parts[0][2]}'
for first_x, last_x, y in bg_path_parts:
    bg_outline += f'L{last_x + 1},{y}'
for first_x, last_x, y in reversed(bg_path_parts):
    bg_outline += f'L{first_x},{y}'
bg_outline += 'Z'

svg_path_version = f'''<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 {w} {h}">
  <path d="{bg_outline}" fill="#{avg_bg[0]:02x}{avg_bg[1]:02x}{avg_bg[2]:02x}"/>
  <path d="{path_data}" fill="#1a1a1a"/>
  <path d="{orange_path}" fill="#ff4d00"/>
</svg>
'''

path_svg_file = r'D:\Develop\Project\syncApi\src\main\resources\META-INF\pluginIcon_paths.svg'
with open(path_svg_file, 'w') as f:
    f.write(svg_path_version)
print(f'Path-based SVG written ({len(svg_path_version)} bytes)')
print(f'Dark rects: {len(dark_rects)}, Orange rects: {len(orange_rects)}')
