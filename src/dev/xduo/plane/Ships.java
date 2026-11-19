package dev.xduo.plane;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * The aircraft art layer: one routine per silhouette, one row of truth per aircraft in ShipSpec.
 *
 * <p>Every part is authored once, in class initialisers, in <b>unit space</b>: the nose sits at
 * y = -1, the tail at y = +1, the wing tips at +/- wingSpan, so the whole ship fits in a circle of
 * radius 1. Drawing is one translate + one scale to the ship's pixel radius, which means the same
 * geometry serves a 13 dp darter and a 60 dp boss, and every ship is symmetric by construction.
 * Gradients are authored in unit space too - a shader's coordinates ride the canvas transform -
 * so one static final LinearGradient covers every size and nothing is allocated per frame.
 *
 * <p>Enemies fly nose-<i>down</i>: the caller passes unit space with the nose at -1 and this class
 * rotates the enemy silhouettes by 180 degrees, so their engines and exhaust point up-screen,
 * which is where they came from.
 *
 * <p>The paint is a borrowed one (GameView uses a single Paint for the whole frame), so its style,
 * stroke width, cap, alpha, colour and shader are captured on entry and put back on exit.
 *
 * <p>Each ship is a stack of layers drawn in a fixed order - flame, wings, hull, sub-parts, seams,
 * rim light, cockpit glass, identity lamps - and the stack is truncated at ShipSpec.layers(id), so
 * the 34 enemies a max-difficulty wave can put on screen cost what ShipSpec.frameOps budgets, no
 * more. The last layer of every ship is the identity core lamp: a fully opaque disc of
 * ShipSpec.identity(id), which is what check.py samples and what makes a ship readable at 40 px.
 */
final class Ships {

    private Ships() {
    }

    /** The 13 silhouettes, indexed by ShipSpec.silhouette(id); used by the test guard. */
    static final int[] ALL_SILHOUETTES = {
            ShipSpec.SIL_INTERCEPTOR, ShipSpec.SIL_DIAMOND, ShipSpec.SIL_ARROW,
            ShipSpec.SIL_HEXPOD, ShipSpec.SIL_PENTAGON, ShipSpec.SIL_BROADSIDE,
            ShipSpec.SIL_LOBES, ShipSpec.SIL_BARGE, ShipSpec.SIL_NEEDLE,
            ShipSpec.SIL_SAUCER, ShipSpec.SIL_FORTRESS, ShipSpec.SIL_STORM,
            ShipSpec.SIL_WRAITH,
    };

    // ---------------------------------------------------------------- palettes

    /** Hull body gradient, light at the nose, dark at the tail, one per aircraft. */
    private static final Shader[] HULL = new Shader[ShipSpec.COUNT];
    /** Cockpit glass: a bright rim of tinted identity colour fading into ShipSpec.cockpit. */
    private static final Shader[] GLASS = new Shader[ShipSpec.COUNT];
    /** Engine exhaust: white-hot at the nozzle, the ship's own colour, fading out at the tail. */
    private static final Shader[] FLAME = new Shader[ShipSpec.COUNT];
    /** Metal on the secondary parts (nacelles, pods, turrets): darker, cooler than the hull. */
    private static final int[] METAL = new int[ShipSpec.COUNT];
    private static final int[] METAL_HI = new int[ShipSpec.COUNT];
    private static final int[] DARK = new int[ShipSpec.COUNT];
    private static final int[] RIM = new int[ShipSpec.COUNT];
    private static final int[] HOT = new int[ShipSpec.COUNT];

    /**
     * How much white goes on the lit nose. Pale identities (splitter's mint, miner's gold) are
     * already near white, so a fixed 0.38 washed them out and they read as "the pale ship" - the
     * lighter the ship's own colour, the less white it gets.
     */
    private static float brightEnd(int id) {
        int c = ShipSpec.identity(id);
        int luma = (ShipSpec.red(c) * 77 + ShipSpec.green(c) * 150 + ShipSpec.blue(c) * 29) >> 8;
        float k = 1f - Math.max(0f, (luma - 170f) / 85f);
        return Math.max(0.12f, 0.38f * k);
    }

    static {
        for (int i = 0; i < ShipSpec.COUNT; i++) {
            int id = ShipSpec.identity(i);
            // three stops: a lit nose, the ship's own saturated colour across the middle,
            // and shadowed plating at the tail. Two stops (light -> dark) washed the hulls out.
            HULL[i] = new LinearGradient(0f, -1.15f, 0.35f, 1f,
                    new int[]{ShipSpec.tint(id, brightEnd(i)), id, ShipSpec.hullBottom(i)},
                    new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP);
            GLASS[i] = new LinearGradient(0f, -1f, 0f, 1f,
                    new int[]{ShipSpec.tint(id, 0.78f), ShipSpec.tint(id, 0.30f),
                            ShipSpec.cockpit(id)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            FLAME[i] = new LinearGradient(0f, 0f, 0f, 1f,
                    new int[]{ShipSpec.alpha(ShipSpec.tint(id, 1f), 235),
                            ShipSpec.alpha(id, 205),
                            ShipSpec.alpha(ShipSpec.shade(id, 0.55f), 0)},
                    new float[]{0f, 0.3f, 1f}, Shader.TileMode.CLAMP);
            METAL[i] = ShipSpec.mix(ShipSpec.accent(i), ShipSpec.hullBottom(i), 0.35f);
            METAL_HI[i] = ShipSpec.tint(ShipSpec.accent(i), 0.72f);
            DARK[i] = ShipSpec.mix(ShipSpec.hullBottom(i), 0xFF05070F, 0.45f);
            RIM[i] = ShipSpec.tint(ShipSpec.trim(i), 0.55f);
            HOT[i] = ShipSpec.tint(ShipSpec.trim(i), 0.25f);
        }
    }

    // ------------------------------------------------------------- shared parts

    /** One engine exhaust, drawn from (x,y) towards +y, tapering to a point. */
    private static final Path P_FLAME = new Path();
    /** A turret / pod / bay mouth: a rounded box, reused everywhere at three scales. */
    private static final Path P_POD = new Path();
    /** A lamp: unit circle, for engine rings, bay lights and rim lights. */
    private static final Path P_DISC = new Path();
    /** The right half of a swept delta wing, mirrored for the left. */
    private static final Path P_WING = new Path();
    /** The right half of the interceptor fuselage. */
    private static final Path P_FUSE = new Path();
    /** A vertical cannon barrel, drawn at the pod mouth. */
    private static final Path P_BARREL = new Path();

    static {
        P_FLAME.moveTo(-0.5f, 0f);
        P_FLAME.quadTo(-0.3f, 0.5f, 0f, 1f);
        P_FLAME.quadTo(0.3f, 0.5f, 0.5f, 0f);
        P_FLAME.close();

        P_POD.moveTo(-0.5f, -0.5f);
        P_POD.lineTo(0.5f, -0.5f);
        P_POD.lineTo(0.5f, 0.28f);
        P_POD.quadTo(0.5f, 0.5f, 0.28f, 0.5f);
        P_POD.lineTo(-0.28f, 0.5f);
        P_POD.quadTo(-0.5f, 0.5f, -0.5f, 0.28f);
        P_POD.close();

        P_DISC.addCircle(0f, 0f, 1f, Path.Direction.CW);

        P_WING.moveTo(0.16f, -0.42f);
        P_WING.cubicTo(0.52f, -0.34f, 0.82f, -0.02f, 0.95f, 0.34f);
        P_WING.lineTo(0.72f, 0.5f);
        P_WING.cubicTo(0.5f, 0.36f, 0.3f, 0.3f, 0.16f, 0.3f);
        P_WING.close();

        P_FUSE.moveTo(0f, -1.28f);
        P_FUSE.cubicTo(0.12f, -1.06f, 0.22f, -0.62f, 0.25f, -0.16f);
        P_FUSE.lineTo(0.25f, 0.5f);
        P_FUSE.lineTo(0.14f, 0.86f);
        P_FUSE.lineTo(0f, 0.86f);
        P_FUSE.close();

        P_BARREL.moveTo(-0.16f, 0f);
        P_BARREL.lineTo(0.16f, 0f);
        P_BARREL.lineTo(0.12f, 1f);
        P_BARREL.lineTo(-0.12f, 1f);
        P_BARREL.close();
    }

    // ---------------------------------------------------------------- drawing

    /**
     * Draw one aircraft centred at (cx,cy) with hit radius r.
     *
     * @param id    a ShipSpec id (an enemy type, ShipSpec.PLAYER, or ShipSpec.ofBoss(kind))
     * @param t     game time, for the engine flicker and the spinning parts
     * @param tint  Color.WHITE to flash the ship (hit), 0 for normal colours
     * @param flame engine flame length multiplier (fever / frenzy push it past 1)
     * @param rot   extra rotation in degrees, applied after the nose-down flip (darters bank)
     * @param aux   per-aircraft extra state: for a boss, its phase index (Boss.phaseFor)
     */
    static void draw(Canvas c, Paint p, Path path, RectF tmp, int id, float cx, float cy,
                     float r, float t, int tint, float flame, float rot, int aux) {
        if (r <= 0f) return;
        int baseA = p.getAlpha();
        Paint.Style baseStyle = p.getStyle();
        float baseStroke = p.getStrokeWidth();
        Paint.Cap baseCap = p.getStrokeCap();
        Shader baseShader = p.getShader();
        int baseColor = p.getColor();

        c.save();
        c.translate(cx, cy);
        if (id != ShipSpec.PLAYER) c.rotate(180f);   // enemies descend nose-first
        if (rot != 0f) c.rotate(rot);
        c.scale(r, r);
        int budget = ShipSpec.layers(id);
        float flicker = 0.86f + 0.14f * (float) Math.sin(t * 26f + cx * 0.13f);
        float fl = ShipSpec.flameLen(id) * flame * flicker;
        switch (ShipSpec.silhouette(id)) {
            case ShipSpec.SIL_INTERCEPTOR:
                interceptor(c, p, path, id, budget, t, fl, tint, baseA);
                break;
            case ShipSpec.SIL_DIAMOND:
                diamondShip(c, p, path, id, budget, fl, tint, baseA);
                break;
            case ShipSpec.SIL_ARROW:
                arrowShip(c, p, path, id, budget, fl, tint, baseA);
                break;
            case ShipSpec.SIL_HEXPOD:
                hexpod(c, p, path, id, budget, t, fl, tint, baseA);
                break;
            case ShipSpec.SIL_PENTAGON:
                pentagon(c, p, path, id, budget, t, fl, tint, baseA);
                break;
            case ShipSpec.SIL_BROADSIDE:
                broadside(c, p, path, id, budget, t, fl, tint, baseA);
                break;
            case ShipSpec.SIL_LOBES:
                lobes(c, p, path, id, budget, fl, tint, baseA);
                break;
            case ShipSpec.SIL_BARGE:
                barge(c, p, path, tmp, id, budget, fl, tint, baseA);
                break;
            case ShipSpec.SIL_NEEDLE:
                needle(c, p, path, id, budget, t, fl, tint, baseA);
                break;
            case ShipSpec.SIL_SAUCER:
                saucer(c, p, path, id, budget, t, fl, tint, baseA);
                break;
            case ShipSpec.SIL_FORTRESS:
                fortress(c, p, path, id, budget, t, fl, tint, aux, baseA);
                break;
            case ShipSpec.SIL_STORM:
                storm(c, p, path, id, budget, t, fl, tint, aux, baseA);
                break;
            case ShipSpec.SIL_WRAITH:
                wraith(c, p, path, id, budget, t, fl, tint, aux, baseA);
                break;
            default:
                diamondShip(c, p, path, id, budget, fl, tint, baseA);
                break;
        }
        c.restore();

        p.setStyle(baseStyle);
        p.setStrokeWidth(baseStroke);
        p.setStrokeCap(baseCap);
        p.setAlpha(baseA);
        p.setShader(baseShader);
        p.setColor(baseColor);
    }

    // ------------------------------------------------------------------ layers

    /** Fill a unit-space path with a unit-space shader. One budgeted layer. */
    private static void fill(Canvas c, Paint p, Path path, Shader sh, int tint, int alpha) {
        p.setStyle(Paint.Style.FILL);
        if (tint == 0) {
            p.setShader(sh);
            p.setColor(0xFFFFFFFF);
        } else {
            p.setShader(null);          // a flashed ship goes solid, shader and all
            p.setColor(tint);
        }
        p.setAlpha(alpha);
        c.drawPath(path, p);
        p.setShader(null);
    }

    /** Fill a unit-space path with a flat colour. One budgeted layer. */
    private static void fillCol(Canvas c, Paint p, Path path, int col, int tint, int alpha) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(tint == 0 ? col : tint);
        p.setAlpha(alpha);
        c.drawPath(path, p);
    }

    /** Stroke a unit-space path; w is a fraction of the ship radius, not a dp. */
    private static void stroke(Canvas c, Paint p, Path path, int col, int tint, float w, int alpha) {
        p.setStyle(Paint.Style.STROKE);
        p.setShader(null);
        p.setStrokeWidth(w);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(tint == 0 ? col : tint);
        p.setAlpha(alpha);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** A flat disc at unit (x,y) with unit radius rr. */
    private static void disc(Canvas c, Paint p, float x, float y, float rr, int col, int tint,
                             int alpha) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(tint == 0 ? col : tint);
        p.setAlpha(alpha);
        c.save();
        c.translate(x, y);
        c.scale(rr, rr);
        c.drawPath(P_DISC, p);
        c.restore();
    }

    /** A rounded box centred at (x,y), w wide, h tall, corner rad - for pods, turrets, plates. */
    private static void box(Canvas c, Paint p, Path path, float x, float y, float w, float h,
                            float rad, int col, int tint, int alpha) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(tint == 0 ? col : tint);
        p.setAlpha(alpha);
        c.save();
        c.translate(x, y);
        c.scale(w, h);
        path.reset();
        path.addRoundRect(-0.5f, -0.5f, 0.5f, 0.5f, rad, rad, Path.Direction.CW);
        c.drawPath(path, p);
        c.restore();
    }

    /**
     * Cockpit glass: an ellipse of GLASS[id] with a bright rim and a specular streak.
     * Two budgeted layers (glass, then the streak).
     */
    private static void glass(Canvas c, Paint p, Path path, int id, float x, float y,
                              float rx, float ry, int tint, int alpha) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(tint == 0 ? 0xFFFFFFFF : tint);
        p.setAlpha(alpha);
        c.save();
        c.translate(x, y);
        c.scale(rx, ry);
        if (tint == 0) p.setShader(GLASS[id]);
        c.drawPath(P_DISC, p);
        p.setShader(null);
        // rim light, so the canopy reads as a glass bubble and not a ball bearing
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.07f);
        p.setColor(ShipSpec.tint(id, 0.8f));
        p.setAlpha((int) (alpha * 0.9f));
        c.drawPath(P_DISC, p);
        p.setStyle(Paint.Style.FILL);
        // specular streak, upper-left of the canopy, so the glass reads as curved
        p.setColor(0xFFFFFFFF);
        p.setAlpha((int) (alpha * 0.55f));
        c.save();
        c.translate(-0.26f, -0.36f);
        c.scale(0.30f, 0.16f);
        c.drawPath(P_DISC, p);
        c.restore();
        c.restore();
    }

    /** Engine exhaust: a hot core inside a wider plume of the ship's own colour. */
    private static void flame(Canvas c, Paint p, int id, float x, float y, float len, float w,
                              int tint, int alpha) {
        if (len <= 0f) return;
        p.setColor(tint == 0 ? 0xFFFFFFFF : tint);
        p.setAlpha(alpha);
        c.save();
        c.translate(x, y);
        c.scale(w, len);
        p.setShader(FLAME[id]);
        c.drawPath(P_FLAME, p);
        p.setShader(null);
        // white-hot core, 40% of the plume
        p.setColor(0xFFFFFFFF);
        p.setAlpha((int) (alpha * 0.75f));
        c.scale(0.42f, 0.55f);
        c.drawPath(P_FLAME, p);
        c.restore();
        p.setAlpha(alpha);
    }

    /**
     * The identity core lamp: fully opaque, exactly ShipSpec.identity(id), no shader, no alpha.
     * This is the pixel check.py samples and the point the whole ship is read from.
     */
    private static void core(Canvas c, Paint p, int id, float x, float y, float rr, int tint,
                             int alpha) {
        disc(c, p, x, y, rr, ShipSpec.identity(id), tint, alpha);
    }

    /** A polygon with n sides, squashed vertically by sy, rotated by rot, into path. */
    private static void poly(Path path, int n, float sy, double rot) {
        path.reset();
        for (int k = 0; k < n; k++) {
            double a = k * 2 * Math.PI / n + rot;
            float px = (float) Math.cos(a);
            float py = (float) Math.sin(a) * sy;
            if (k == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        path.close();
    }

    /** A symmetric hull: nose at (0,noseY), shoulders at +/- shW, tail at +/- tailW. */
    private static void hull(Path path, float noseY, float shY, float shW, float tailY,
                             float tailW) {
        path.reset();
        path.moveTo(0f, noseY);
        path.cubicTo(shW * 0.55f, noseY + (shY - noseY) * 0.35f, shW * 0.92f, shY - 0.12f,
                shW, shY);
        path.lineTo(tailW * 1.06f, tailY * 0.55f);
        path.lineTo(tailW, tailY);
        path.lineTo(0f, tailY);
        path.close();
    }

    /** Draw the right half of path, then the same path mirrored - strict symmetry, half the geometry. */
    private static void mirrored(Canvas c, Paint p, Path path, float sx) {
        c.save();
        c.scale(sx, 1f);
        c.drawPath(path, p);
        c.scale(-1f, 1f);
        c.drawPath(path, p);
        c.restore();
    }

    // ================================================================ the aircraft

    /**
     * 玄铁 interceptor (the player). Delta wings under a slim fuselage, two nacelles with their own
     * exhaust, canopy glass over the pilot, wing-tip gun barrels, and the identity core glowing at
     * the centre of the airframe.
     */
    private static void interceptor(Canvas c, Paint p, Path path, int id, int b, float t,
                                    float fl, int tint, int a) {
        float span = ShipSpec.wingSpan(id);
        float cr = ShipSpec.cockpitR(id);
        float core = ShipSpec.coreR(id);

        if (b > 0) {   // 1 exhaust plumes, one per nacelle
            flame(c, p, id, -0.44f, 0.9f, fl, 0.3f, tint, a);
            flame(c, p, id, 0.44f, 0.9f, fl, 0.3f, tint, a);
            b--;
        }
        if (b > 0) {   // 2 wings
            fill(c, p, P_WING, HULL[id], tint, a);
            mirrored(c, p, P_WING, span);
            b--;
        }
        if (b > 0) {   // 3 wing leading-edge rim light
            stroke(c, p, P_WING, RIM[id], tint, 0.035f, 190);
            c.save();
            c.scale(span, 1f);
            c.drawPath(P_WING, p);
            c.scale(-1f, 1f);
            c.drawPath(P_WING, p);
            c.restore();
            b--;
        }
        if (b > 0) {   // 4 nacelles with gun barrels
            fillCol(c, p, P_POD, METAL[id], tint, a);
            for (int s = -1; s <= 1; s += 2) {
                c.save();
                c.translate(s * 0.44f, 0.42f);
                c.scale(0.3f, 1.02f);
                c.drawPath(P_POD, p);
                c.restore();
                fillCol(c, p, P_BARREL, DARK[id], tint, a);
                c.save();
                c.translate(s * 0.44f, -0.62f);
                c.scale(0.11f, 0.62f);
                c.drawPath(P_BARREL, p);
                c.restore();
            }
            b--;
        }
        if (b > 0) {   // 5 fuselage
            fill(c, p, P_FUSE, HULL[id], tint, a);
            mirrored(c, p, P_FUSE, 1f);
            b--;
        }
        if (b > 0) {   // 6 hull rim light + armour seams
            stroke(c, p, P_FUSE, RIM[id], tint, 0.03f, 200);
            mirrored(c, p, P_FUSE, 1f);
            stroke(c, p, P_FUSE, DARK[id], tint, 0.022f, 120);
            b--;
        }
        if (b > 0) {   // 7 canopy glass
            c.save();
            c.translate(0f, -0.44f);
            c.scale(cr * 0.62f, cr);
            p.setStyle(Paint.Style.FILL);
            p.setColor(tint == 0 ? 0xFFFFFFFF : tint);
            p.setAlpha(a);
            p.setShader(tint == 0 ? GLASS[id] : null);
            c.drawPath(P_DISC, p);
            p.setShader(null);
            c.restore();
            b--;
        }
        if (b > 0) {   // 8 identity core lamp + wing-tip navigation lamps
            core(c, p, id, 0f, 0.06f, core, tint, a);
            disc(c, p, -0.9f * span, 0.36f, 0.07f, HOT[id], tint, 235);
            disc(c, p, 0.9f * span, 0.36f, 0.07f, HOT[id], tint, 235);
            b--;
        }
    }

    /** grunt 0: a four-point shuriken with concave edges, the cheapest ship on screen. */
    private static void diamondShip(Canvas c, Paint p, Path path, int id, int b, float fl,
                                    int tint, int a) {
        if (b > 0) {
            flame(c, p, id, 0f, 0.92f, fl, 0.34f, tint, a);
            b--;
        }
        if (b > 0) {
            float w = ShipSpec.wingSpan(id);
            path.reset();
            path.moveTo(0f, -1f);
            path.quadTo(0.3f, -0.26f, w, 0f);
            path.quadTo(0.3f, 0.26f, 0f, 1f);
            path.quadTo(-0.3f, 0.26f, -w, 0f);
            path.quadTo(-0.3f, -0.26f, 0f, -1f);
            path.close();
            fill(c, p, path, HULL[id], tint, a);
            b--;
        }
        if (b > 0) {
            stroke(c, p, path, RIM[id], tint, 0.04f, 190);
            b--;
        }
        if (b > 0) {
            core(c, p, id, 0f, 0f, ShipSpec.coreR(id), tint, a);
            b--;
        }
    }

    /** darter 1: a needle-nosed dart with swept tips, built to read as speed. */
    private static void arrowShip(Canvas c, Paint p, Path path, int id, int b, float fl,
                                  int tint, int a) {
        float w = ShipSpec.wingSpan(id);
        if (b > 0) {
            flame(c, p, id, -0.2f, 0.86f, fl, 0.2f, tint, a);
            flame(c, p, id, 0.2f, 0.86f, fl, 0.2f, tint, a);
            b--;
        }
        if (b > 0) {
            path.reset();
            path.moveTo(0f, -1.18f);
            path.cubicTo(0.16f, -0.7f, 0.24f, -0.2f, w, 0.42f);
            path.lineTo(0.22f, 0.28f);
            path.lineTo(0.16f, 0.9f);
            path.lineTo(-0.16f, 0.9f);
            path.lineTo(-0.22f, 0.28f);
            path.lineTo(-w, 0.42f);
            path.cubicTo(-0.24f, -0.2f, -0.16f, -0.7f, 0f, -1.18f);
            path.close();
            fill(c, p, path, HULL[id], tint, a);
            b--;
        }
        if (b > 0) {
            stroke(c, p, path, RIM[id], tint, 0.035f, 200);
            b--;
        }
        if (b > 0) {
            core(c, p, id, 0f, -0.1f, ShipSpec.coreR(id), tint, a);
            b--;
        }
    }

    /** gunship 2: a hexagonal hull with two gun pods on stub wings. */
    private static void hexpod(Canvas c, Paint p, Path path, int id, int b, float t, float fl,
                               int tint, int a) {
        if (b > 0) {
            flame(c, p, id, -0.34f, 0.72f, fl, 0.26f, tint, a);
            flame(c, p, id, 0.34f, 0.72f, fl, 0.26f, tint, a);
            b--;
        }
        if (b > 0) {
            poly(path, 6, 0.86f, Math.PI / 6);
            fill(c, p, path, HULL[id], tint, a);
            b--;
        }
        if (b > 0) {
            for (int s = -1; s <= 1; s += 2) {
                box(c, p, path, s * 1.02f, 0.06f, 0.5f, 0.78f, 0.16f, METAL[id], tint, a);
                fillCol(c, p, P_BARREL, DARK[id], tint, a);
                c.save();
                c.translate(s * 1.02f, 0.4f);
                c.scale(0.14f, 0.5f);
                c.drawPath(P_BARREL, p);
                c.restore();
            }
            b--;
        }
        if (b > 0) {
            glass(c, p, path, id, 0f, -0.44f, ShipSpec.cockpitR(id) * 1.1f,
                    ShipSpec.cockpitR(id), tint, a);
            b--;
        }
        if (b > 0) {
            poly(path, 6, 0.86f, Math.PI / 6);
            stroke(c, p, path, RIM[id], tint, 0.03f, 190);
            b--;
        }
        if (b > 0) {
            core(c, p, id, 0f, 0.24f, ShipSpec.coreR(id), tint, a);
            b--;
        }
    }

    /** elite 3: a pentagon hull inside a ring that spins. */
    private static void pentagon(Canvas c, Paint p, Path path, int id, int b, float t, float fl,
                                 int tint, int a) {
        if (b > 0) {
            flame(c, p, id, -0.3f, 0.8f, fl, 0.24f, tint, a);
            flame(c, p, id, 0.3f, 0.8f, fl, 0.24f, tint, a);
            b--;
        }
        if (b > 0) {   // the spinning ring, the elite's signature
            c.save();
            c.rotate(t * 90f);
            poly(path, 3, 1f, 0f);
            stroke(c, p, path, HOT[id], tint, 0.05f, 150);
            c.restore();
            b--;
        }
        if (b > 0) {
            poly(path, 5, 1f, -Math.PI / 2);
            fill(c, p, path, HULL[id], tint, a);
            b--;
        }
        if (b > 0) {
            poly(path, 5, 1f, -Math.PI / 2);
            stroke(c, p, path, RIM[id], tint, 0.035f, 210);
            poly(path, 5, 0.62f, -Math.PI / 2);
            stroke(c, p, path, DARK[id], tint, 0.03f, 150);
            b--;
        }
        if (b > 0) {
            glass(c, p, path, id, 0f, -0.48f, ShipSpec.cockpitR(id), ShipSpec.cockpitR(id) * 1.15f,
                    tint, a);
            b--;
        }
        if (b > 0) {
            core(c, p, id, 0f, 0.22f, ShipSpec.coreR(id), tint, a);
            b--;
        }
    }

    /** heavy 4: a wide armoured barge with four gun pods, the mid-wave wall. */
    private static void broadside(Canvas c, Paint p, Path path, int id, int b, float t, float fl,
                                  int tint, int a) {
        float w = ShipSpec.wingSpan(id);
        if (b > 0) {
            for (int k = 0; k < 4; k++) {
                flame(c, p, id, -0.66f + k * 0.44f, 0.62f, fl, 0.2f, tint, a);
            }
            b--;
        }
        if (b > 0) {   // main hull
            path.reset();
            path.moveTo(-w, -0.34f);
            path.lineTo(-w * 0.62f, -0.66f);
            path.lineTo(w * 0.62f, -0.66f);
            path.lineTo(w, -0.34f);
            path.lineTo(w * 0.94f, 0.44f);
            path.lineTo(-w * 0.94f, 0.44f);
            path.close();
            fill(c, p, path, HULL[id], tint, a);
            b--;
        }
        if (b > 0) {   // armour strakes
            fillCol(c, p, path, METAL[id], tint, a);
            for (int s = -1; s <= 1; s += 2) {
                c.save();
                c.translate(s * w * 0.5f, -0.16f);
                c.scale(w * 0.42f, 0.9f);
                c.drawPath(P_POD, p);
                c.restore();
            }
            b--;
        }
        if (b > 0) {   // four gun pods
            for (int k = 0; k < 4; k++) {
                float x = -0.78f + k * 0.52f;
                box(c, p, path, x, 0.62f, 0.3f, 0.5f, 0.1f, METAL[id], tint, a);
            }
            b--;
        }
        if (b > 0) {
            glass(c, p, path, id, 0f, -0.55f, ShipSpec.cockpitR(id) * 1.5f,
                    ShipSpec.cockpitR(id) * 0.8f, tint, a);
            b--;
        }
        if (b > 0) {
            path.reset();
            path.moveTo(-w, -0.34f);
            path.lineTo(-w * 0.62f, -0.66f);
            path.lineTo(w * 0.62f, -0.66f);
            path.lineTo(w, -0.34f);
            stroke(c, p, path, RIM[id], tint, 0.025f, 210);
            b--;
        }
        if (b > 0) {
            core(c, p, id, 0f, 0.04f, ShipSpec.coreR(id), tint, a);
            disc(c, p, -w * 0.94f, 0.3f, 0.06f, HOT[id], tint, 230);
            disc(c, p, w * 0.94f, 0.3f, 0.06f, HOT[id], tint, 230);
            b--;
        }
    }

    /** splitter 5: two lobes held apart by a glowing seam - it tears in half when it dies. */
    private static void lobes(Canvas c, Paint p, Path path, int id, int b, float fl,
                              int tint, int a) {
        if (b > 0) {
            flame(c, p, id, -0.46f, 0.66f, fl, 0.24f, tint, a);
            flame(c, p, id, 0.46f, 0.66f, fl, 0.24f, tint, a);
            b--;
        }
        if (b > 0) {
            float w = ShipSpec.wingSpan(id);
            disc(c, p, -w * 0.46f, 0f, 0.6f, 0, tint, 0);
            p.setShader(HULL[id]);
            p.setColor(0xFFFFFFFF);
            c.save();
            c.translate(-w * 0.46f, 0f);
            c.scale(0.6f, 0.66f);
            c.drawPath(P_DISC, p);
            c.translate(2f, 0f);
            c.drawPath(P_DISC, p);
            c.restore();
            p.setShader(null);
            b--;
        }
        if (b > 0) {
            float w = ShipSpec.wingSpan(id);
            stroke(c, p, P_DISC, RIM[id], tint, 0.05f, 170);
            c.save();
            c.translate(-w * 0.46f, 0f);
            c.scale(0.6f, 0.66f);
            c.drawPath(P_DISC, p);
            c.translate(2f, 0f);
            c.drawPath(P_DISC, p);
            c.restore();
            b--;
        }
        if (b > 0) {   // the seam the ship splits along
            p.setStyle(Paint.Style.FILL);
            p.setColor(tint == 0 ? DARK[id] : tint);
            p.setAlpha(a);
            c.save();
            c.scale(0.1f, 0.66f);
            c.drawPath(P_POD, p);
            c.restore();
            b--;
        }
        if (b > 0) {
            glass(c, p, path, id, 0f, -0.52f, ShipSpec.cockpitR(id), ShipSpec.cockpitR(id), tint, a);
            b--;
        }
        if (b > 0) {
            core(c, p, id, 0f, 0.04f, ShipSpec.coreR(id), tint, a);
            b--;
        }
    }

    /** miner 6: a ribbed freighter hull with an open mine bay underneath. */
    private static void barge(Canvas c, Paint p, Path path, RectF tmp, int id, int b, float fl,
                              int tint, int a) {
        float w = ShipSpec.wingSpan(id);
        if (b > 0) {
            flame(c, p, id, -0.4f, 0.5f, fl, 0.24f, tint, a);
            flame(c, p, id, 0.4f, 0.5f, fl, 0.24f, tint, a);
            b--;
        }
        if (b > 0) {
            path.reset();
            path.moveTo(-w, -0.5f);
            path.lineTo(-w * 0.7f, -0.78f);
            path.lineTo(w * 0.7f, -0.78f);
            path.lineTo(w, -0.5f);
            path.lineTo(w * 0.86f, 0.5f);
            path.lineTo(-w * 0.86f, 0.5f);
            path.close();
            fill(c, p, path, HULL[id], tint, a);
            b--;
        }
        if (b > 0) {   // cargo ribs
            stroke(c, p, P_DISC, DARK[id], tint, 0.05f, 90);
            for (int k = -1; k <= 1; k++) {
                c.save();
                c.translate(k * 0.52f, -0.14f);
                c.scale(0.07f, 0.56f);
                c.drawPath(P_DISC, p);
                c.restore();
            }
            b--;
        }
        if (b > 0) {   // the mine bay, and the mine hanging out of it
            box(c, p, path, 0f, 0.44f, w * 1.3f, 0.34f, 0.14f, DARK[id], tint, a);
            disc(c, p, 0f, 0.86f, 0.26f, METAL[id], tint, a);
            b--;
        }
        if (b > 0) {
            glass(c, p, path, id, 0f, -0.58f, ShipSpec.cockpitR(id) * 0.9f,
                    ShipSpec.cockpitR(id) * 0.7f, tint, a);
            b--;
        }
        if (b > 0) {
            core(c, p, id, 0f, 0.06f, ShipSpec.coreR(id), tint, a);
            disc(c, p, -w * 0.9f, 0.1f, 0.07f, HOT[id], tint, 230);
            disc(c, p, w * 0.9f, 0.1f, 0.07f, HOT[id], tint, 230);
            b--;
        }
    }

    /** sniper 7: a long thin needle with a scope lens at the nose. */
    private static void needle(Canvas c, Paint p, Path path, int id, int b, float t, float fl,
                               int tint, int a) {
        float w = ShipSpec.wingSpan(id);
        if (b > 0) {
            flame(c, p, id, 0f, 1f, fl, 0.22f, tint, a);
            b--;
        }
        if (b > 0) {
            path.reset();
            path.moveTo(0f, -1.5f);
            path.lineTo(w * 0.5f, -0.2f);
            path.lineTo(w, 0.6f);
            path.lineTo(w * 0.34f, 0.44f);
            path.lineTo(w * 0.22f, 1f);
            path.lineTo(-w * 0.22f, 1f);
            path.lineTo(-w * 0.34f, 0.44f);
            path.lineTo(-w, 0.6f);
            path.lineTo(-w * 0.5f, -0.2f);
            path.close();
            fill(c, p, path, HULL[id], tint, a);
            b--;
        }
        if (b > 0) {
            stroke(c, p, path, RIM[id], tint, 0.03f, 200);
            b--;
        }
        if (b > 0) {
            core(c, p, id, 0f, -0.62f, ShipSpec.coreR(id), tint, a);
            disc(c, p, 0f, -0.62f, ShipSpec.coreR(id) * 1.9f, 0, tint, 0);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(tint == 0 ? HOT[id] : tint);
            p.setAlpha(a);
            p.setStrokeWidth(0.05f);
            c.save();
            c.translate(0f, -0.62f);
            c.scale(ShipSpec.coreR(id) * 1.9f, ShipSpec.coreR(id) * 1.9f);
            c.drawPath(P_DISC, p);
            c.restore();
            b--;
        }
    }

    /** mothership 8: a saucer with a dome, three drone bays and a ring of rim lights. */
    private static void saucer(Canvas c, Paint p, Path path, int id, int b, float t, float fl,
                               int tint, int a) {
        float w = ShipSpec.wingSpan(id);
        if (b > 0) {
            for (int k = -1; k <= 1; k++) flame(c, p, id, k * 0.42f * w, 0.42f, fl, 0.22f, tint, a);
            b--;
        }
        if (b > 0) {   // hull disc
            c.save();
            c.scale(w, 0.52f);
            fill(c, p, P_DISC, HULL[id], tint, a);
            c.restore();
            b--;
        }
        if (b > 0) {   // dome
            c.save();
            c.translate(0f, -0.16f);
            c.scale(0.42f * w, 0.46f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(tint == 0 ? 0xFFFFFFFF : tint);
            p.setAlpha(a);
            p.setShader(tint == 0 ? GLASS[id] : null);
            c.drawPath(P_DISC, p);
            p.setShader(null);
            c.restore();
            b--;
        }
        if (b > 0) {   // three drone bays on the underside
            for (int k = -1; k <= 1; k++) {
                disc(c, p, k * 0.52f * w, 0.16f, 0.16f, DARK[id], tint, a);
                disc(c, p, k * 0.52f * w, 0.16f, 0.08f, HOT[id], tint, 235);
            }
            b--;
        }
        if (b > 0) {   // rim light ring
            for (int k = 0; k < 7; k++) {
                float ang = (float) (k * Math.PI / 6) + t * 0.9f;
                disc(c, p, (float) Math.cos(ang) * w * 0.92f, (float) Math.sin(ang) * 0.44f,
                        0.06f, HOT[id], tint, 200);
            }
            b--;
        }
        if (b > 0) {
            c.save();
            c.scale(w, 0.52f);
            stroke(c, p, P_DISC, RIM[id], tint, 0.05f, 200);
            c.restore();
            b--;
        }
        if (b > 0) {
            core(c, p, id, 0f, 0f, ShipSpec.coreR(id), tint, a);
            b--;
        }
    }

    /** BOSS 玄铁堡垒: a hexagonal fortress - layered armour, four turrets, a core in the keep. */
    private static void fortress(Canvas c, Paint p, Path path, int id, int b, float t, float fl,
                                 int tint, int phase, int a) {
        float w = ShipSpec.wingSpan(id);
        if (b > 0) {
            for (int k = 0; k < 4; k++) {
                flame(c, p, id, -0.6f + k * 0.4f, 0.86f, fl, 0.22f, tint, a);
            }
            b--;
        }
        if (b > 0) {   // outer keep
            poly(path, 6, 1.05f, -Math.PI / 2);
            c.save();
            c.scale(w, 1f);
            fill(c, p, path, HULL[id], tint, a);
            c.restore();
            b--;
        }
        if (b > 0) {   // inner plate, rotated 30 degrees so the seams break up the silhouette
            poly(path, 6, 0.78f, 0f);
            c.save();
            c.scale(w * 0.86f, 0.9f);
            fillCol(c, p, path, METAL[id], tint, a);
            c.restore();
            b--;
        }
        if (b > 0) {   // four turrets
            for (int k = 0; k < 4; k++) {
                double ang = k * Math.PI / 2 + Math.PI / 4;
                float x = (float) Math.cos(ang) * w * 0.66f;
                float y = (float) Math.sin(ang) * 0.66f;
                disc(c, p, x, y, 0.17f, METAL[id], tint, a);
                fillCol(c, p, P_BARREL, DARK[id], tint, a);
                c.save();
                c.translate(x, y);
                c.rotate((float) Math.toDegrees(ang) - 90f);
                c.scale(0.11f, 0.42f);
                c.drawPath(P_BARREL, p);
                c.restore();
            }
            b--;
        }
        if (b > 0) {   // armour seams
            stroke(c, p, P_DISC, DARK[id], tint, 0.02f, 110);
            for (int k = 0; k < 6; k++) {
                double ang = k * Math.PI / 3;
                path.reset();
                path.moveTo((float) Math.cos(ang) * 0.3f * w, (float) Math.sin(ang) * 0.3f);
                path.lineTo((float) Math.cos(ang) * w * 0.95f, (float) Math.sin(ang) * 0.95f);
                c.drawPath(path, p);
            }
            b--;
        }
        if (b > 0) {
            glass(c, p, path, id, 0f, -0.30f, ShipSpec.cockpitR(id) * 1.5f,
                    ShipSpec.cockpitR(id), tint, a);
            b--;
        }
        if (b > 0) {
            poly(path, 6, 1.05f, -Math.PI / 2);
            c.save();
            c.scale(w, 1f);
            stroke(c, p, path, RIM[id], tint, 0.022f, 210);
            c.restore();
            b--;
        }
        if (b > 0) {   // the three phase lamps, lit one per phase the boss has reached
            for (int k = 0; k < 3; k++) {
                float x = (k - 1) * 0.34f * w;
                disc(c, p, x, -0.78f, 0.1f, k <= phase ? HOT[id] : DARK[id], tint, a);
            }
            b--;
        }
        if (b > 0) {
            core(c, p, id, 0f, 0.06f, ShipSpec.coreR(id), tint, a);
            disc(c, p, 0f, 0.06f, ShipSpec.coreR(id) * 1.7f, 0, tint, 0);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(tint == 0 ? HOT[id] : tint);
            p.setAlpha((int) (a * (0.55f + 0.45f * (float) Math.sin(t * 4f))));
            p.setStrokeWidth(0.03f);
            c.save();
            c.translate(0f, 0.06f);
            float rr = ShipSpec.coreR(id) * 1.7f;
            c.scale(rr, rr);
            c.drawPath(P_DISC, p);
            c.restore();
            b--;
        }
    }

    /** BOSS 雷穹母舰: a storm manta - wide swept wings, three pods, arcs jumping across them. */
    private static void storm(Canvas c, Paint p, Path path, int id, int b, float t, float fl,
                              int tint, int phase, int a) {
        float w = ShipSpec.wingSpan(id);
        if (b > 0) {
            for (int k = -1; k <= 1; k++) flame(c, p, id, k * 0.36f, 0.7f, fl, 0.26f, tint, a);
            b--;
        }
        if (b > 0) {   // swept manta wings
            path.reset();
            path.moveTo(0f, -0.5f);
            path.cubicTo(w * 0.5f, -0.42f, w * 0.9f, -0.1f, w, 0.5f);
            path.cubicTo(w * 0.6f, 0.36f, w * 0.3f, 0.44f, 0f, 0.72f);
            path.cubicTo(-w * 0.3f, 0.44f, -w * 0.9f, -0.1f, -w, 0.5f);
            path.cubicTo(-w * 0.5f, -0.42f, -w * 0.5f, -0.5f, 0f, -0.5f);
            path.close();
            fill(c, p, path, HULL[id], tint, a);
            b--;
        }
        if (b > 0) {   // central spine
            hull(path, -1.05f, -0.3f, 0.3f, 0.86f, 0.2f);
            fill(c, p, path, HULL[id], tint, a);
            mirrored(c, p, path, 1f);
            b--;
        }
        if (b > 0) {   // three storm pods
            for (int k = -1; k <= 1; k++) {
                box(c, p, path, k * w * 0.62f, 0.16f, 0.34f, 0.62f, 0.12f, METAL[id], tint, a);
                disc(c, p, k * w * 0.62f, 0.44f, 0.1f, HOT[id], tint, 235);
            }
            b--;
        }
        if (b > 0) {   // lightning arcs crawling along the wing edges
            stroke(c, p, P_DISC, HOT[id], tint, 0.02f, 170);
            for (int s = -1; s <= 1; s += 2) {
                path.reset();
                for (int k = 0; k < 5; k++) {
                    float f = k / 4f;
                    float x = s * (0.18f + f * w * 0.74f);
                    float y = -0.1f + f * 0.5f
                            + (k % 2 == 0 ? -0.12f : 0.12f) * (float) Math.sin(t * 9f + k);
                    if (k == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                c.drawPath(path, p);
            }
            b--;
        }
        if (b > 0) {
            glass(c, p, path, id, 0f, -0.62f, ShipSpec.cockpitR(id) * 1.2f,
                    ShipSpec.cockpitR(id) * 1.3f, tint, a);
            b--;
        }
        if (b > 0) {
            path.reset();
            path.moveTo(0f, -0.5f);
            path.cubicTo(w * 0.5f, -0.42f, w * 0.9f, -0.1f, w, 0.5f);
            path.cubicTo(-w * 0.9f, -0.1f, -w * 0.5f, -0.42f, 0f, -0.5f);
            stroke(c, p, path, RIM[id], tint, 0.018f, 200);
            b--;
        }
        if (b > 0) {
            for (int k = 0; k < 3; k++) {
                float x = (k - 1) * 0.36f * w;
                disc(c, p, x, -0.78f, 0.1f, k <= phase ? HOT[id] : DARK[id], tint, a);
            }
            b--;
        }
        if (b > 0) {
            core(c, p, id, 0f, 0.1f, ShipSpec.coreR(id), tint, a);
            disc(c, p, 0f, -0.06f, ShipSpec.coreR(id) * 0.6f, HOT[id], tint, 200);
            b--;
        }
    }

    /** BOSS 赤炎妖机: a wraith of scythe wings and four engines, with a single burning eye. */
    private static void wraith(Canvas c, Paint p, Path path, int id, int b, float t, float fl,
                               int tint, int phase, int a) {
        float w = ShipSpec.wingSpan(id);
        if (b > 0) {
            for (int k = 0; k < 4; k++) {
                flame(c, p, id, -0.5f + k * 0.33f, 0.8f, fl, 0.2f, tint, a);
            }
            b--;
        }
        if (b > 0) {   // scythe wings, the outer scythe first
            path.reset();
            path.moveTo(0.18f, -0.62f);
            path.cubicTo(w * 0.62f, -0.5f, w, 0.06f, w * 0.9f, 0.72f);
            path.cubicTo(w * 0.66f, 0.3f, w * 0.4f, 0.24f, 0.16f, 0.42f);
            path.close();
            fill(c, p, path, HULL[id], tint, a);
            mirrored(c, p, path, 1f);
            b--;
        }
        if (b > 0) {   // hull
            hull(path, -1.3f, -0.34f, 0.34f, 0.9f, 0.22f);
            fill(c, p, path, HULL[id], tint, a);
            mirrored(c, p, path, 1f);
            b--;
        }
        if (b > 0) {   // four engine cowlings
            for (int k = 0; k < 4; k++) {
                float x = -0.5f + k * 0.33f;
                box(c, p, path, x, 0.62f, 0.24f, 0.44f, 0.1f, METAL[id], tint, a);
            }
            b--;
        }
        if (b > 0) {   // wing membrane ribs
            stroke(c, p, path, DARK[id], tint, 0.018f, 130);
            for (int k = 1; k <= 3; k++) {
                path.reset();
                path.moveTo(0.2f * k, -0.4f + k * 0.06f);
                path.quadTo(w * 0.5f * k, 0.02f, w * 0.3f * k, 0.5f);
                c.drawPath(path, p);
                c.save();
                c.scale(-1f, 1f);
                c.drawPath(path, p);
                c.restore();
            }
            b--;
        }
        if (b > 0) {   // the eye
            c.save();
            c.translate(0f, -0.44f);
            c.scale(ShipSpec.cockpitR(id) * 1.5f, ShipSpec.cockpitR(id) * 0.8f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(tint == 0 ? 0xFFFFFFFF : tint);
            p.setAlpha(a);
            p.setShader(tint == 0 ? GLASS[id] : null);
            c.drawPath(P_DISC, p);
            p.setShader(null);
            c.restore();
            b--;
        }
        if (b > 0) {
            hull(path, -1.3f, -0.34f, 0.34f, 0.9f, 0.22f);
            stroke(c, p, path, RIM[id], tint, 0.02f, 210);
            mirrored(c, p, path, 1f);
            b--;
        }
        if (b > 0) {
            for (int k = 0; k < 3; k++) {
                float x = (k - 1) * 0.38f * w;
                disc(c, p, x, -0.92f, 0.1f, k <= phase ? HOT[id] : DARK[id], tint, a);
            }
            b--;
        }
        if (b > 0) {
            core(c, p, id, 0f, 0.12f, ShipSpec.coreR(id), tint, a);
            b--;
        }
    }
}
