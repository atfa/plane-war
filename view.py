#!/usr/bin/env python3
"""Screenshot -> readable ASCII: block-averaged luminance with gamma, plus a colour
letter when a block is strongly tinted. usage: view.py [png] [cols] [y0% y1%]"""
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

RAMP = " .,:;+*?%S#"[::1]
RAMP = " .',:;+*?%S#@"


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "/tmp/screen.png"
    cols = int(sys.argv[2]) if len(sys.argv) > 2 else 96
    h, pal, trns, raw = A.read_png(path)
    px, ch = A.unfilter(h, h['w'], raw)
    rgb = A.to_rgb(h, pal, trns, px, ch)
    w, ht = h['w'], h['h']
    rows = int(cols * ht / w / 2.0)
    bw, bh = w / float(cols), ht / float(rows)
    for j in range(rows):
        out = []
        for i in range(cols):
            x0, x1 = int(i * bw), max(int((i + 1) * bw), int(i * bw) + 1)
            y0, y1 = int(j * bh), max(int((j + 1) * bh), int(j * bh) + 1)
            n = rs = gs = bs = 0
            for y in range(y0, y1, 2):
                for x in range(x0, x1, 2):
                    o = (y * w + x) * 3
                    rs += rgb[o]; gs += rgb[o + 1]; bs += rgb[o + 2]; n += 1
            if not n:
                out.append(' '); continue
            r, g, b = rs / n, gs / n, bs / n
            mx, mn = max(r, g, b), min(r, g, b)
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            lum = lum ** 0.55                      # gamma: lift the darks
            c = RAMP[min(len(RAMP) - 1, int(lum * len(RAMP)))]
            if mx - mn > 45:
                if r > g and r > b: c = 'R' if b < g else 'M'
                elif g > r and g > b: c = 'G'
                elif b > r and b > g: c = 'B'
            out.append(c)
        print(''.join(out))


main()
