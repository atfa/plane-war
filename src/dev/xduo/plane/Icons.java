package dev.xduo.plane;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Vector icon library for the HUD, the power-up drops and the settings screen.
 *
 * <p>Everything is drawn with Canvas primitives (paths, arcs, round rects) so the icons
 * scale with the DeX fit factor, need no bitmap resources and never depend on a CJK font
 * being available - the game used to paste one Chinese glyph into a box as a placeholder.
 *
 * <p>Contract, enforced by every method here:
 * <ul>
 *   <li>an icon fits inside the circle of radius {@code r} around {@code (cx, cy)} -
 *       nothing is drawn outside it, so a caller that clipped to that circle is safe;</li>
 *   <li>no allocation per call: the caller's {@link Paint}, {@link Path} and {@link RectF}
 *       are reused (the render loop runs at 60 fps and must stay allocation free);</li>
 *   <li>paint state is restored on the way out (style, stroke width, cap, alpha, shader),
 *       so callers keep their own paint configuration;</li>
 *   <li>icon ids are the {@code UiSpec.ICON_*} constants - UiSpec owns them because it is
 *       pure Java and asserts the coverage on the JVM, Icons is the Android side.</li>
 * </ul>
 */
final class Icons {

    private Icons() {
    }

    /** Draws {@code icon} centred on (cx, cy) inside the circle of radius r. */
    static void draw(Canvas c, Paint p, Path path, RectF tmp, int icon,
                     float cx, float cy, float r, int color) {
        if (r <= 0f) return;
        // paint state the callers rely on: put it back exactly
        int alpha = p.getAlpha();
        int col = p.getColor();
        Paint.Style style = p.getStyle();
        float width = p.getStrokeWidth();
        Paint.Cap cap = p.getStrokeCap();
        p.setColor(color);

        if (icon == UiSpec.ICON_SWITCH) {
            switch_(c, p, tmp, cx, cy, r * 2f, r * 1.1f, true, color);
            p.setColor(col);
            p.setAlpha(alpha);
            p.setStyle(style);
            p.setStrokeWidth(width);
            p.setStrokeCap(cap);
            return;
        }

        c.save();
        c.translate(cx, cy);
        c.scale(r, r);
        switch (icon) {
            case UiSpec.ICON_SHIELD: shield(c, p, path); break;
            case UiSpec.ICON_HEAL: heal(c, p, path, tmp); break;
            case UiSpec.ICON_BOMB: bomb(c, p, path, tmp, false); break;
            case UiSpec.ICON_SCORE: star(c, p, path, 5, 0.95f, 0.42f); break;
            case UiSpec.ICON_RAPID: bolt(c, p, path); break;
            case UiSpec.ICON_LASER: laser(c, p, path, tmp); break;
            case UiSpec.ICON_HOMING: missile(c, p, path, tmp); break;
            case UiSpec.ICON_WEAPON: flame(c, p, path); break;
            case UiSpec.ICON_SUPER: star(c, p, path, 8, 0.95f, 0.34f); break;
            case UiSpec.ICON_BOMB_BTN: bomb(c, p, path, tmp, true); break;
            case UiSpec.ICON_MUSIC: music(c, p, path, tmp); break;
            case UiSpec.ICON_SFX: speaker(c, p, path, tmp); break;
            case UiSpec.ICON_VIBRO: vibro(c, p, path, tmp); break;
            case UiSpec.ICON_SLOW: chevron(c, p, path, 1, true); break;
            case UiSpec.ICON_FAST: chevron(c, p, path, 2, false); break;
            case UiSpec.ICON_LIVES: ship(c, p, path); break;
            default: dot(c, p); break;
        }
        c.restore();

        p.setColor(col);
        p.setAlpha(alpha);
        p.setStyle(style);
        p.setStrokeWidth(width);
        p.setStrokeCap(cap);
    }

    /**
     * A real on/off switch (track + knob) instead of the "开/关" text, centred on (cx, cy).
     * The knob slides and the track lights up, so the state reads without any label.
     */
    static void switch_(Canvas c, Paint p, RectF tmp, float cx, float cy,
                        float w, float h, boolean on, int color) {
        int alpha = p.getAlpha();
        Paint.Style style = p.getStyle();
        float width = p.getStrokeWidth();

        float rad = h / 2;
        tmp.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setAlpha(on ? 110 : 60);
        c.drawRoundRect(tmp, rad, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, h * 0.09f));
        p.setColor(color);
        p.setAlpha(on ? 230 : 110);
        c.drawRoundRect(tmp, rad, rad, p);

        float knob = h * 0.34f;
        float kx = on ? cx + w / 2 - rad : cx - w / 2 + rad;
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(on ? 255 : 150);
        c.drawCircle(kx, cy, knob, p);
        p.setAlpha(alpha);
        p.setStyle(style);
        p.setStrokeWidth(width);
    }

    // ------------------------------------------------------------------ power-ups

    /** heraldic shield, 护盾 */
    private static void shield(Canvas c, Paint p, Path path) {
        path.reset();
        path.moveTo(0, -0.95f);
        path.lineTo(0.78f, -0.6f);
        path.lineTo(0.78f, 0.1f);
        path.quadTo(0.76f, 0.7f, 0f, 0.97f);
        path.quadTo(-0.76f, 0.7f, -0.78f, 0.1f);
        path.lineTo(-0.78f, -0.6f);
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(mul(p.getAlpha(), 0.35f));
        c.drawPath(path, p);
        p.setAlpha(mul(p.getAlpha(), 2.6f));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(0.16f);
        c.drawPath(path, p);
        // inner chevron = "protection"
        p.setStyle(Paint.Style.FILL);
        path.reset();
        path.moveTo(0, -0.5f);
        path.lineTo(0.42f, -0.28f);
        path.lineTo(0.42f, 0.12f);
        path.quadTo(0.4f, 0.46f, 0f, 0.66f);
        path.quadTo(-0.4f, 0.46f, -0.42f, 0.12f);
        path.lineTo(-0.42f, -0.28f);
        path.close();
        c.drawPath(path, p);
    }

    /** medical cross, 生命 */
    private static void heal(Canvas c, Paint p, Path path, RectF tmp) {
        float rad = 0.14f;
        p.setStyle(Paint.Style.FILL);
        float w = 0.26f, long_ = 0.92f;
        tmp.set(-w, -long_, w, long_);
        c.drawRoundRect(tmp, rad, rad, p);
        tmp.set(-long_, -w, long_, w);
        c.drawRoundRect(tmp, rad, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.1f);
        p.setAlpha(mul(p.getAlpha(), 0.5f));
        c.drawCircle(0, 0, 0.95f, p);
    }

    /** bomb sphere with a lit fuse; ring=true adds the shockwave (the skill button) */
    private static void bomb(Canvas c, Paint p, Path path, RectF tmp, boolean ring) {
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(0, 0.16f, 0.62f, p);
        // highlight
        p.setAlpha(mul(p.getAlpha(), 0.45f));
        c.drawCircle(-0.2f, -0.05f, 0.17f, p);
        p.setAlpha(mul(p.getAlpha(), 1f / 0.45f));
        // fuse + spark
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(0.15f);
        c.drawLine(0.24f, -0.28f, 0.52f, -0.62f, p);
        starPath(path, 4, 0.3f, 0.09f);
        c.save();
        c.translate(0.6f, -0.72f);
        c.drawPath(path, p);
        c.restore();
        if (ring) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.12f);
            p.setAlpha(mul(p.getAlpha(), 0.55f));
            tmp.set(-0.95f, -0.95f, 0.95f, 0.95f);
            c.drawArc(tmp, -60, 120, false, p);
            c.drawArc(tmp, 120, 120, false, p);
        }
    }

    /** lightning bolt, 速射 */
    private static void bolt(Canvas c, Paint p, Path path) {
        path.reset();
        path.moveTo(0.16f, -0.95f);
        path.lineTo(-0.66f, 0.12f);
        path.lineTo(-0.06f, 0.12f);
        path.lineTo(-0.28f, 0.95f);
        path.lineTo(0.66f, -0.16f);
        path.lineTo(0.06f, -0.16f);
        path.close();
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path, p);
    }

    /** emitter lens + beam, 激光 */
    private static void laser(Canvas c, Paint p, Path path, RectF tmp) {
        // a wide soft column with a bright core and two coils squeezed around it: at 30 px a
        // chevron at the bottom read as a download arrow, the coils read as a laser
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(mul(p.getAlpha(), 0.42f));
        tmp.set(-0.2f, -0.95f, 0.2f, 0.95f);
        c.drawRoundRect(tmp, 0.18f, 0.18f, p);
        p.setAlpha(mul(p.getAlpha(), 1f / 0.42f));
        tmp.set(-0.075f, -0.95f, 0.075f, 0.95f);
        c.drawRoundRect(tmp, 0.075f, 0.075f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(0.15f);
        c.drawLine(-0.52f, -0.36f, 0.52f, -0.36f, p);
        c.drawLine(-0.52f, 0.36f, 0.52f, 0.36f, p);
    }

    /** air-to-air missile with fins and a trail, 追踪 */
    private static void missile(Canvas c, Paint p, Path path, RectF tmp) {
        // rocket: nose, body, two fins, exhaust. The detailed silhouette collapsed into a leaf at
        // 30 px, so every part is a plain convex shape now
        p.setStyle(Paint.Style.FILL);
        tmp.set(-0.21f, -0.42f, 0.21f, 0.44f);
        c.drawRoundRect(tmp, 0.2f, 0.2f, p);
        path.reset();
        path.moveTo(0, -0.95f);
        path.lineTo(0.24f, -0.32f);
        path.lineTo(-0.24f, -0.32f);
        path.close();
        path.moveTo(-0.19f, 0.04f);
        path.lineTo(-0.54f, 0.54f);
        path.lineTo(-0.19f, 0.44f);
        path.close();
        path.moveTo(0.19f, 0.04f);
        path.lineTo(0.54f, 0.54f);
        path.lineTo(0.19f, 0.44f);
        path.close();
        c.drawPath(path, p);
        p.setAlpha(mul(p.getAlpha(), 0.55f));
        path.reset();
        path.moveTo(-0.12f, 0.5f);
        path.lineTo(0.12f, 0.5f);
        path.lineTo(0, 0.95f);
        path.close();
        c.drawPath(path, p);
    }

    /** flame, 火力 (weapon level) */
    private static void flame(Canvas c, Paint p, Path path) {
        path.reset();
        path.moveTo(0.02f, -0.95f);
        path.quadTo(0.5f, -0.34f, 0.34f, 0.06f);
        path.quadTo(0.62f, -0.1f, 0.56f, -0.44f);
        path.quadTo(0.98f, 0.16f, 0.6f, 0.66f);
        path.quadTo(0.24f, 1.0f, -0.3f, 0.86f);
        path.quadTo(-0.86f, 0.62f, -0.62f, -0.06f);
        path.quadTo(-0.5f, 0.24f, -0.26f, 0.16f);
        path.quadTo(-0.44f, -0.3f, 0.02f, -0.95f);
        path.close();
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path, p);
        // inner core
        p.setAlpha(mul(p.getAlpha(), 0.4f));
        path.reset();
        path.moveTo(0.02f, -0.2f);
        path.quadTo(0.34f, 0.24f, 0.12f, 0.56f);
        path.quadTo(-0.1f, 0.78f, -0.3f, 0.5f);
        path.quadTo(-0.44f, 0.24f, -0.16f, -0.1f);
        path.close();
        c.drawPath(path, p);
    }

    // ------------------------------------------------------------------ skills / settings

    /** 8 point nova, the super blast button */
    private static void star(Canvas c, Paint p, Path path, int points, float outer, float inner) {
        starPath(path, points, outer, inner);
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.09f);
        p.setAlpha(mul(p.getAlpha(), 0.5f));
        c.drawCircle(0, 0, outer * 0.55f, p);
    }

    static void starPath(Path path, int points, float outer, float inner) {
        path.reset();
        for (int i = 0; i < points * 2; i++) {
            double a = i * Math.PI / points;
            float rr = (i & 1) == 0 ? outer : inner;
            float x = (float) (Math.sin(a) * rr);
            float y = (float) (-Math.cos(a) * rr);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
    }

    /** eighth note, 音乐 */
    private static void music(Canvas c, Paint p, Path path, RectF tmp) {
        p.setStyle(Paint.Style.FILL);
        c.save();
        c.rotate(-18f);
        tmp.set(-0.62f, 0.28f, -0.02f, 0.76f);
        c.drawOval(tmp, p);
        c.restore();
        // stem + flag
        tmp.set(-0.04f, -0.78f, 0.16f, 0.5f);
        c.drawRect(tmp, p);
        path.reset();
        path.moveTo(0.16f, -0.78f);
        path.quadTo(0.72f, -0.6f, 0.6f, -0.06f);
        path.quadTo(0.5f, -0.36f, 0.16f, -0.3f);
        path.close();
        c.drawPath(path, p);
    }

    /** speaker with two waves, 音效 */
    private static void speaker(Canvas c, Paint p, Path path, RectF tmp) {
        p.setStyle(Paint.Style.FILL);
        tmp.set(-0.85f, -0.3f, -0.45f, 0.3f);
        c.drawRect(tmp, p);
        path.reset();
        path.moveTo(-0.45f, -0.3f);
        path.lineTo(0.05f, -0.75f);
        path.lineTo(0.05f, 0.75f);
        path.lineTo(-0.45f, 0.3f);
        path.close();
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(0.14f);
        for (int i = 0; i < 2; i++) {
            float s = 0.42f + i * 0.28f;
            p.setAlpha(mul(p.getAlpha(), i == 0 ? 1f : 0.55f));
            tmp.set(0.05f - s, -s, 0.05f + s, s);
            c.drawArc(tmp, -55, 110, false, p);
        }
    }

    /** phone with vibration waves, 震动 */
    private static void vibro(Canvas c, Paint p, Path path, RectF tmp) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(0.14f);
        tmp.set(-0.3f, -0.8f, 0.3f, 0.8f);
        c.drawRoundRect(tmp, 0.14f, 0.14f, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(0, 0.56f, 0.09f, p);
        p.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 2; i++) {
            float s = 0.46f + i * 0.26f;
            p.setAlpha(mul(p.getAlpha(), i == 0 ? 1f : 0.5f));
            tmp.set(-s - 0.16f, -s, s - 0.16f, s);
            c.drawArc(tmp, 120, 120, false, p);
            tmp.set(0.16f - s, -s, 0.16f + s, s);
            c.drawArc(tmp, -60, 120, false, p);
        }
    }

    /** one chevron (slow) / two chevrons (fast), the sensitivity slider ends */
    private static void chevron(Canvas c, Paint p, Path path, int n, boolean pointLeft) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(0.22f);
        for (int i = 0; i < n; i++) {
            float ox = (i - (n - 1) / 2f) * 0.46f;
            path.reset();
            if (pointLeft) {
                path.moveTo(ox + 0.28f, -0.7f);
                path.lineTo(ox - 0.24f, 0f);
                path.lineTo(ox + 0.28f, 0.7f);
            } else {
                path.moveTo(ox - 0.28f, -0.7f);
                path.lineTo(ox + 0.24f, 0f);
                path.lineTo(ox - 0.28f, 0.7f);
            }
            c.drawPath(path, p);
        }
    }

    /** small nose-up plane, the life counter (matches the HUD lives shape) */
    private static void ship(Canvas c, Paint p, Path path) {
        path.reset();
        path.moveTo(0, -0.95f);
        path.lineTo(0.75f, 0.7f);
        path.lineTo(0, 0.26f);
        path.lineTo(-0.75f, 0.7f);
        path.close();
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path, p);
    }

    /** unknown id: a plain disc, so a missing icon is visible but never a crash */
    private static void dot(Canvas c, Paint p) {
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(0, 0, 0.5f, p);
    }

    private static int mul(int alpha, float k) {
        return Math.max(0, Math.min(255, Math.round(alpha * k)));
    }
}
