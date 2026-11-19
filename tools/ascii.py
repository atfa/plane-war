#!/usr/bin/env python3
# Vendored copy of ascii.py from the dev-apk-on-android agent skill
# (https://github.com/atfa/dev-apk-on-android, MIT). Kept in-tree so the screenshot
# checks in this repo run on a fresh clone with no extra PYTHONPATH.
"""PNG -> ASCII art, pure stdlib (no PIL).  usage: ascii.py img.png [width] [step]

Saturated colours become letters (G red-dominant..., useful for spotting UI elements):
  R red-ish, G green-ish, B blue-ish, Y yellow-ish; everything else a luminance ramp.
"""
import sys, zlib, struct

def read_png(path):
    d = open(path, 'rb').read()
    assert d[:8] == b'\x89PNG\r\n\x1a\n', 'not a png'
    pos, idat, pal, trns = 8, b'', None, None
    hdr = {}
    while pos < len(d):
        ln = struct.unpack('>I', d[pos:pos+4])[0]; typ = d[pos+4:pos+8]
        data = d[pos+8:pos+8+ln]
        if typ == b'IHDR':
            hdr = dict(zip(('w','h','depth','color','comp','filt','interlace'),
                           struct.unpack('>IIBBBBB', data)))
        elif typ == b'PLTE': pal = data
        elif typ == b'tRNS': trns = data
        elif typ == b'IDAT': idat += data
        pos += 12 + ln
    return hdr, pal, trns, zlib.decompress(idat)

def unfilter(h, w, raw):
    ch = {0:1, 2:3, 3:1, 4:2, 6:4}[h['color']]
    bpp = max(1, h['depth'] // 8) * ch
    stride = (w * ch * h['depth'] + 7) // 8
    out, prev = bytearray(w * h['h']), bytearray(stride)
    i = 0
    for y in range(h['h']):
        f = raw[i]; i += 1
        line = bytearray(raw[i:i+stride]); i += stride
        for x in range(stride):
            a = line[x-bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x-bpp] if x >= bpp else 0
            if f == 1: line[x] = (line[x] + a) & 255
            elif f == 2: line[x] = (line[x] + b) & 255
            elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p-a), abs(p-b), abs(p-c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x] + pr) & 255
        out[y*stride:(y+1)*stride] = line
        prev = line
    return out, ch

def to_rgb(h, pal, trns, px, ch):
    w, dep = h['w'], h['depth']
    assert dep == 8, 'only 8-bit PNG supported (screencap output is)'
    out = bytearray(w * h['h'] * 3)
    for i in range(w * h['h']):
        o = i * ch
        if h['color'] == 2: r, g, b = px[o], px[o+1], px[o+2]
        elif h['color'] == 6: r, g, b = px[o], px[o+1], px[o+2]
        elif h['color'] == 0: r = g = b = px[o]
        elif h['color'] == 4: r = g = b = px[o]
        else:
            p = px[o] * 3
            r, g, b = pal[p], pal[p+1], pal[p+2]
            if trns and p // 3 < len(trns) and trns[p // 3] == 0: r = g = b = 255
        out[i*3:i*3+3] = bytes((r, g, b))
    return out

RAMP = ' .:-=+*#%@'
def mark(r, g, b):
    mx, mn = max(r, g, b), min(r, g, b)
    if mx - mn < 60: return None
    if g > r and g > b: return 'G'
    if r > g and r > b: return 'R'
    if b > r and b > g: return 'B'
    if r > 200 and g > 200: return 'Y'
    return None

def main():
    path = sys.argv[1]
    W = int(sys.argv[2]) if len(sys.argv) > 2 else 70
    step = int(sys.argv[3]) if len(sys.argv) > 3 else 1
    h, pal, trns, raw = read_png(path)
    px, ch = unfilter(h, h['w'], raw)
    rgb = to_rgb(h, pal, trns, px, ch)
    w, ht = h['w'], h['h']
    sx, sy = w / W, ht / max(1, int(W * ht / w / 2))
    H = int(W * ht / w / 2)
    for j in range(H):
        line = []
        for i in range(0, W, step):
            x = min(w-1, int((i + step // 2) * sx))
            y = min(ht-1, int((j + 0.5) * sy))
            o = (y * w + x) * 3
            r, g, b = rgb[o], rgb[o+1], rgb[o+2]
            m = mark(r, g, b)
            line.append(m if m else RAMP[min(9, int((0.299*r + 0.587*g + 0.114*b) / 25.6))])
        print(''.join(line))

main()
