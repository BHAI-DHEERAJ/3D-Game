#!/usr/bin/env python3
"""Generate all app icons (PWA + Android) with no third-party libraries.

    python3 tools/make_icons.py

Writes:
  app/icons/icon-192.png, icon-512.png, maskable-512.png
  android/app/src/main/res/mipmap-*/ic_launcher.png
  android/app/src/main/res/mipmap-*/ic_launcher_foreground.png
"""
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SS = 3  # supersampling factor

TOP = (255, 122, 61)     # #FF7A3D
BOTTOM = (226, 62, 62)   # #E23E3E


def lerp(a, b, t):
    return a + (b - a) * t


def mix(c1, c2, t):
    return tuple(int(round(lerp(c1[i], c2[i], t))) for i in range(3))


def rounded_alpha(x, y, size, radius):
    """1 inside a rounded square that fills the canvas, 0 outside (0..1 ss units)."""
    r = radius
    cx = min(max(x, r), size - r)
    cy = min(max(y, r), size - r)
    dx = x - cx
    dy = y - cy
    if dx * dx + dy * dy <= r * r:
        return 1.0
    return 0.0


def point_in_triangle(px, py, a, b, c):
    def sign(p1, p2, p3):
        return (p1[0] - p3[0]) * (p2[1] - p3[1]) - (p2[0] - p3[0]) * (p1[1] - p3[1])
    d1 = sign((px, py), a, b)
    d2 = sign((px, py), b, c)
    d3 = sign((px, py), c, a)
    has_neg = d1 < 0 or d2 < 0 or d3 < 0
    has_pos = d1 > 0 or d2 > 0 or d3 > 0
    return not (has_neg and has_pos)


def render(size, rounded=True, maskable_pad=0.0, transparent_bg=False):
    """Return list of rows of RGBA tuples."""
    n = size * SS
    radius = (0.22 * n) if rounded else 0
    # play triangle geometry, centred, scaled by maskable_pad
    scale = 1.0 - maskable_pad
    tw = n * 0.42 * scale
    th = n * 0.50 * scale
    cx, cy = n / 2.0, n / 2.0
    shift = tw * 0.06  # optical centring of a triangle
    a = (cx - tw / 2 + shift, cy - th / 2)
    b = (cx - tw / 2 + shift, cy + th / 2)
    c = (cx + tw / 2 + shift, cy)
    # rounded triangle look: treat via inner core + soft edge
    rows = []
    for py in range(size):
        row = []
        for px in range(size):
            acc_r = acc_g = acc_b = acc_a = 0.0
            for sy in range(SS):
                for sx in range(SS):
                    x = px * SS + sx + 0.5
                    y = py * SS + sy + 0.5
                    alpha = 1.0 if not rounded else rounded_alpha(x, y, n, radius)
                    if alpha <= 0:
                        continue
                    # background gradient
                    t = y / n
                    bg = mix(TOP, BOTTOM, t)
                    r, g, b_ = bg
                    if transparent_bg:
                        r = g = b_ = 0
                        alpha = 0.0
                        if point_in_triangle(x, y, a, b, c):
                            r = g = b_ = 255
                            alpha = 1.0
                    elif point_in_triangle(x, y, a, b, c):
                        r = g = b_ = 255
                    acc_r += r
                    acc_g += g
                    acc_b += b_
                    acc_a += alpha
            total = SS * SS
            if acc_a <= 0:
                row.append((0, 0, 0, 0))
            else:
                row.append((int(acc_r / total), int(acc_g / total),
                            int(acc_b / total), int(255 * acc_a / total)))
        rows.append(row)
    return rows


def write_png(path, rows):
    size = len(rows)
    raw = bytearray()
    for row in rows:
        raw.append(0)  # filter type 0
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data +
                struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) +
           chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("  wrote %-72s %5dx%-5d %6.1f KB" % (os.path.relpath(path, ROOT), size, size, len(png) / 1024))


def main():
    print("PWA / web icons")
    write_png(os.path.join(ROOT, "app/icons/icon-192.png"), render(192))
    write_png(os.path.join(ROOT, "app/icons/icon-512.png"), render(512))
    write_png(os.path.join(ROOT, "app/icons/maskable-512.png"), render(512, maskable_pad=0.22))

    print("Android launcher icons")
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for name, size in densities.items():
        base = os.path.join(ROOT, "android/app/src/main/res/mipmap-" + name)
        write_png(os.path.join(base, "ic_launcher.png"), render(size))
        write_png(os.path.join(base, "ic_launcher_round.png"), render(size))
        # adaptive foreground: 108dp canvas, art inside the middle ~66%
        write_png(os.path.join(base, "ic_launcher_foreground.png"),
                  render(int(round(size * 108 / 48)), rounded=False,
                         maskable_pad=0.36, transparent_bg=True))


if __name__ == "__main__":
    main()
