#!/usr/bin/env python
"""Generate ic_launcher.png for every density - pure stdlib PNG writer (no PIL).
A cyan fighter jet over a night-sky gradient, drawn on an 11x11 grid."""
import os, struct, zlib

SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
N = 11

JET = [
    (5, 1),
    (5, 2), (4, 2), (6, 2),
    (5, 3), (3, 3), (7, 3),
    (5, 4), (2, 4), (8, 4),
    (5, 5), (1, 5), (9, 5),
    (5, 6), (2, 6), (8, 6),
    (5, 7), (4, 7), (6, 7),
    (5, 8),
]
COCKPIT = (5, 3)
FLAME = [(5, 9)]


def px(x, y, n):
    # rounded square background with a vertical sky gradient
    r = n * 0.20
    cx = min(max(x, r), n - r)
    cy = min(max(y, r), n - r)
    if (x - cx) ** 2 + (y - cy) ** 2 > r * r:
        return (0, 0, 0, 0)
    t = y / float(n)
    bg = (int(10 + 18 * t), int(16 + 26 * t), int(40 + 46 * t))
    i = x * N // n
    j = y * N // n
    if (i, j) in FLAME:
        return (255, 170, 70, 255)
    if (i, j) == COCKPIT:
        return (20, 44, 78, 255)
    if (i, j) in JET:
        f = j / float(N)
        return (int(150 - 60 * f), int(240 - 90 * f), 255, 255)
    return bg + (255,)


def png(path, n):
    rows = []
    for y in range(n):
        row = bytearray(b"\x00")
        for x in range(n):
            row += bytes(px(x, y, n))
        rows.append(bytes(row))
    raw = b"".join(rows)

    def chunk(t, d):
        c = t + d
        return struct.pack(">I", len(d)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    data = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", n, n, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))
    open(path, "wb").write(data)
    print("wrote %s (%dx%d, %d bytes)" % (path, n, n, len(data)))


for d, n in SIZES.items():
    out = "res/mipmap-%s" % d
    os.makedirs(out, exist_ok=True)
    png(os.path.join(out, "ic_launcher.png"), n)
