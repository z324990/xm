#!/usr/bin/env python3
"""Generate app icons for AI Chat Platform desktop app."""

import struct
import zlib
import os
import sys

ASSETS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'assets')

def create_png(width, height, draw_func):
    def make_chunk(chunk_type, data):
        chunk = chunk_type + data
        crc = struct.pack('>I', zlib.crc32(chunk) & 0xffffffff)
        return struct.pack('>I', len(data)) + chunk + crc

    # IHDR: color type 6 = RGBA
    ihdr_data = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    ihdr = make_chunk(b'IHDR', ihdr_data)

    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0)  # filter byte
        for x in range(width):
            r, g, b, a = draw_func(x, y, width, height)
            raw_data.extend([r, g, b, a])

    compressed = zlib.compress(bytes(raw_data))
    idat = make_chunk(b'IDAT', compressed)
    iend = make_chunk(b'IEND', b'')

    return b'\x89PNG\r\n\x1a\n' + ihdr + idat + iend


def draw_icon(x, y, w, h):
    cx, cy = w // 2, h // 2
    radius = min(w, h) // 2 - 10
    dx, dy = x - cx, y - cy
    dist = (dx * dx + dy * dy) ** 0.5

    if dist > radius:
        return (0, 0, 0, 0)

    # Dark background circle
    inner_r = radius * 0.92
    if dist < inner_r:
        t = dist / inner_r
        rv = int(79 + (129 - 79) * t)
        gv = int(70 + (140 - 70) * t)
        bv = int(229 + (248 - 229) * t)
        return (rv, gv, bv, 255)

    edge = 1.0 - (dist - inner_r) / (radius - inner_r)
    fade = int(255 * edge * 0.8)
    return (79, 70, 229, fade)


def main():
    os.makedirs(ASSETS_DIR, exist_ok=True)

    sizes = [(1024, 'icon-1024.png'), (256, 'icon.png'),
             (128, '128x128.png'), (64, '64x64.png')]

    for size, name in sizes:
        path = os.path.join(ASSETS_DIR, name)
        data = create_png(size, size, draw_icon)
        with open(path, 'wb') as f:
            f.write(data)
        print(f'  Created {name} ({size}x{size})')

    # Generate .ico
    ico_path = os.path.join(ASSETS_DIR, 'icon.ico')
    ico_sizes = [256, 48, 32, 16]
    ico_data = bytearray()
    ico_data.extend(struct.pack('<HHH', 0, 1, len(ico_sizes)))

    offset = 6 + len(ico_sizes) * 16
    png_files = []

    for size in ico_sizes:
        png = create_png(size, size, draw_icon)
        png_files.append(png)
        w = size if size < 256 else 0
        h = size if size < 256 else 0
        ico_data.extend(struct.pack('<BBBBHHII', w, h, 0, 0, 1, 32, len(png), offset))
        offset += len(png)

    for png in png_files:
        ico_data.extend(png)

    with open(ico_path, 'wb') as f:
        f.write(ico_data)
    print(f'  Created icon.ico')
    print(f'\nAll icons generated in: {ASSETS_DIR}')


if __name__ == '__main__':
    main()
