#!/usr/bin/env python3
"""Verify a screenshot by colour: counts + bounding boxes of the game's palette.
usage: check.py [png]"""
import sys, os

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "tools"))
import skillpath; skillpath.add()  # noqa: E402
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

TARGETS = {
    "ship/bullets(cyan)": (120, 224, 255),
    "grunt(red)": (255, 108, 108),
    "darter(orange)": (255, 172, 64),
    "gunship(purple)": (188, 124, 255),
    "elite(green)": (96, 236, 176),
    "heavy(pink)": (255, 88, 190),
    "gold(text)": (255, 206, 96),
    "white": (255, 255, 255),
    "powerbox(amber)": (255, 190, 80),
}


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else os.environ["TMPDIR"] + "/screen.png"
    h, pal, trns, raw = A.read_png(path)
    px, ch = A.unfilter(h, h['w'], raw)
    rgb = A.to_rgb(h, pal, trns, px, ch)
    w, ht = h['w'], h['h']
    print("image %dx%d" % (w, ht))
    for name, (tr, tg, tb) in TARGETS.items():
        n = 0
        x0 = y0 = 10 ** 9
        x1 = y1 = -1
        for y in range(0, ht, 3):
            row = y * w
            for x in range(0, w, 3):
                o = (row + x) * 3
                if (abs(rgb[o] - tr) < 34 and abs(rgb[o + 1] - tg) < 34
                        and abs(rgb[o + 2] - tb) < 34):
                    n += 1
                    if x < x0: x0 = x
                    if x > x1: x1 = x
                    if y < y0: y0 = y
                    if y > y1: y1 = y
        if n:
            print("  %-20s px=%-6d box=(%d,%d)..(%d,%d)" % (name, n * 9, x0, y0, x1, y1))
        else:
            print("  %-20s -" % name)


main()
