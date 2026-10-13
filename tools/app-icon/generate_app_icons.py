#!/usr/bin/env python3
"""Generate the window icon set (PNG sizes + multi-resolution ICO) from the approved source image.

Cut away the white margin of the generated source, make everything outside the rounded tile transparent and
write the sizes next to the source. Run from the repository root:

    python3 tools/app-icon/generate_app_icons.py

Requires Pillow. The results are checked in, so the Gradle build does not need Python.
"""
import os
from collections import deque

from PIL import Image

ICON_DIR = "proasteion/exedra/src/main/resources/com/aresstack/enterpriseai/proasteion/exedra/ui/icon"
SOURCE = os.path.join(ICON_DIR, "app-icon-source.png")
SIZES = (16, 24, 32, 48, 64, 128, 256)
ICO_SIZES = (16, 24, 32, 48, 64, 128, 256)
BACKGROUND_THRESHOLD = 200  # min(r, g, b) above this counts as white margin
EDGE_BAND = 3               # anti-aliased pixels between margin and tile


def exterior_mask(image):
    """Flood-fill the white margin from the four corners."""
    width, height = image.size
    pixels = image.load()
    outside = bytearray(width * height)
    queue = deque([(0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1)])
    while queue:
        x, y = queue.popleft()
        index = y * width + x
        if outside[index] or min(pixels[x, y]) <= BACKGROUND_THRESHOLD:
            continue
        outside[index] = 1
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < width and 0 <= ny < height and not outside[ny * width + nx]:
                queue.append((nx, ny))
    return outside


def near(outside, width, height, x, y, radius):
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            nx, ny = x + dx, y + dy
            if 0 <= nx < width and 0 <= ny < height and outside[ny * width + nx]:
                return True
    return False


def cut_tile(image):
    """Return the tile as RGBA with a transparent, anti-aliased surrounding."""
    width, height = image.size
    outside = exterior_mask(image)
    pixels = image.load()
    result = Image.new("RGBA", image.size)
    out = result.load()
    edge_colour = pixels[width // 2, height // 2]
    for y in range(height):
        for x in range(width):
            r, g, b = pixels[x, y]
            if outside[y * width + x]:
                out[x, y] = (0, 0, 0, 0)
            elif near(outside, width, height, x, y, EDGE_BAND):
                # Blend factor against the white margin: white -> 0, tile colour -> 1.
                darkness = (255 - min(r, g, b)) / float(255 - min(edge_colour))
                alpha = max(0.0, min(1.0, darkness))
                if alpha <= 0.0:
                    out[x, y] = (0, 0, 0, 0)
                    continue
                # Un-blend the white margin so the edge keeps the tile colour.
                colour = tuple(int(max(0, min(255, round((c - 255 * (1 - alpha)) / alpha)))) for c in (r, g, b))
                out[x, y] = colour + (int(round(alpha * 255)),)
            else:
                out[x, y] = (r, g, b, 255)
    box = result.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
    tile = result.crop(box)
    side = max(tile.size)
    square = Image.new("RGBA", (side, side))
    square.paste(tile, ((side - tile.size[0]) // 2, (side - tile.size[1]) // 2))
    return square


def main():
    tile = cut_tile(Image.open(SOURCE).convert("RGB"))
    for size in SIZES:
        tile.resize((size, size), Image.LANCZOS).save(os.path.join(ICON_DIR, "app-icon-%d.png" % size), optimize=True)
    tile.resize((256, 256), Image.LANCZOS).save(os.path.join(ICON_DIR, "app-icon.ico"),
                                               sizes=[(s, s) for s in ICO_SIZES])


if __name__ == "__main__":
    main()
