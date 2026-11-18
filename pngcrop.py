#!/usr/bin/env python3
"""Crop a rectangle out of a screenshot and write it as a PNG (optionally upscaled), so the
result can be looked at as an image instead of ASCII.

usage: pngcrop.py x0 y0 x1 y1 out.png [scale] [png]
"""
import sys, os, zlib, struct

sys.path.insert(0, os.path.expanduser("~/fix/.agents/skills/android-on-device-apk/scripts"))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "..", ".agents", "skills", "android-on-device-apk", "scripts"))
import contextlib, io
_argv = sys.argv[:]
_png = os.environ["TMPDIR"] + "/screen.png"
for a in sys.argv[1:]:
    if a.endswith(".png") and not a.endswith("crop.png"):
        _png = a
sys.argv = ["ascii", _png]
with contextlib.redirect_stdout(io.StringIO()):
    import ascii as A
sys.argv = _argv


def write_png(path, w, h, rgb):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw += rgb[y * w * 3:(y + 1) * w * 3]
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    out = b"\x89PNG\r\n\x1a\n"
    out += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
    out += chunk(b"IDAT", zlib.compress(bytes(raw), 6))
    out += chunk(b"IEND", b"")
    open(path, "wb").write(out)


def main():
    x0, y0, x1, y1 = (int(sys.argv[i]) for i in range(1, 5))
    out = sys.argv[5]
    scale = int(sys.argv[6]) if len(sys.argv) > 6 else 1
    path = sys.argv[7] if len(sys.argv) > 7 else _png
    h, pal, trns, raw = A.read_png(path)
    px, ch = A.unfilter(h, h['w'], raw)
    rgb = A.to_rgb(h, pal, trns, px, ch)
    w, ht = h['w'], h['h']
    x0, y0 = max(0, x0), max(0, y0)
    x1, y1 = min(w, x1), min(ht, y1)
    cw, chh = x1 - x0, y1 - y0
    if scale <= 1:
        buf = bytearray(cw * chh * 3)
        for y in range(chh):
            src = ((y0 + y) * w + x0) * 3
            buf[(y * cw) * 3:(y * cw + cw) * 3] = rgb[src:src + cw * 3]
        write_png(out, cw, chh, buf)
    else:
        buf = bytearray(cw * scale * chh * scale * 3)
        for y in range(chh * scale):
            sy = y0 + y // scale
            row = bytearray(cw * scale * 3)
            for x in range(cw):
                o = (sy * w + x0 + x) * 3
                r, g, b = rgb[o], rgb[o + 1], rgb[o + 2]
                for k in range(scale):
                    p = (x * scale + k) * 3
                    row[p], row[p + 1], row[p + 2] = r, g, b
            buf[y * cw * scale * 3:(y + 1) * cw * scale * 3] = row
        write_png(out, cw * scale, chh * scale, buf)
    print("wrote %s (%dx%d from %s)" % (out, cw * scale, chh * scale, path))


main()
