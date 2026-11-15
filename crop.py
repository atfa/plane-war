#!/usr/bin/env python3
"""Zoom-render a pixel rectangle of a screenshot as half-block ASCII.
usage: crop.py x0 y0 x1 y1 [cols] [png]"""
import sys, os

sys.path.insert(0, os.path.expanduser(
    "~/fix/.agents/skills/android-on-device-apk/scripts"))
import contextlib, io  # noqa: E402
# ascii.py has no __main__ guard: it renders on import, so feed it our png
# and swallow that output.
_png = next((a for a in sys.argv[1:] if a.endswith(".png")),
            os.environ["TMPDIR"] + "/screen.png")
_argv = sys.argv[:]
sys.argv = ["ascii", _png]
with contextlib.redirect_stdout(io.StringIO()):
    import ascii as A  # noqa: E402
sys.argv = _argv

RAMP = " .:-=+*#%@"


def main():
    x0, y0, x1, y1 = (int(sys.argv[i]) for i in range(1, 5))
    cols = int(sys.argv[5]) if len(sys.argv) > 5 else 60
    path = sys.argv[6] if len(sys.argv) > 6 else os.environ["TMPDIR"] + "/screen.png"
    h, pal, trns, raw = A.read_png(path)
    px, ch = A.unfilter(h, h['w'], raw)
    rgb = A.to_rgb(h, pal, trns, px, ch)
    w = h['w']
    ht = y1 - y0
    rows = max(1, int(cols * ht / (x1 - x0) / 2))
    sy, sx = ht / rows, (x1 - x0) / cols

    def lum(x, y):
        o = (y * w + x) * 3
        return (rgb[o] * 299 + rgb[o + 1] * 587 + rgb[o + 2] * 114) // 1000

    for r in range(rows):
        line = []
        for cc in range(cols):
            top = lum(min(x0 + int((cc + .5) * sx), w - 1), y0 + int((r * 2 + .5) * sy))
            bot = lum(min(x0 + int((cc + .5) * sx), w - 1), y0 + int((r * 2 + 1.5) * sy))
            line.append(RAMP[min(9, top * 10 // 256)] + RAMP[min(9, bot * 10 // 256)].upper())
        print(''.join(line))


main()
