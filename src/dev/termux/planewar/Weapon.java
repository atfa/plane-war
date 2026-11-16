package dev.termux.planewar;

/**
 * Player weapon table + target selection, as pure Java (no Android imports) so the JVM suite
 * (plane/test/TestLogic.java, run by plane/test_jvm.sh) can pin the shot patterns, the missile
 * counts, the beam cadence and the lock-on choice.
 *
 * <p>Two rules the view relies on and the tests assert:
 * <ul>
 *   <li>a volley of the beam settles on any one target <em>once</em> per {@link #BEAM_TICK}, never
 *       once per frame - 60 hits/s from one beam would delete a 80 hp boss in under a second;</li>
 *   <li>{@link #pickTarget} never returns "no target" while a candidate is on screen, and the boss
 *       (which does not live in the enemy list) is always a legal candidate.</li>
 * </ul>
 */
public final class Weapon {

    private Weapon() {}

    public static final int MAX_LEVEL = 5;

    /** power-up durations (s) */
    public static final float LASER_SEC = 8f;
    public static final float HOMING_SEC = 12f;

    /** the beam damages at most once per BEAM_TICK per volley, i.e. ~6.7 settlements/s */
    public static final float BEAM_TICK = 0.15f;
    /** half width of the beam column, in dp */
    public static final float BEAM_HALF_DP = 26f;
    /** beam damage per settlement */
    public static final float BEAM_DMG = 3f;

    /** hard cap on player bullets, so LV5 + missiles + laser cannot stack into a frame-time spike */
    public static final int MAX_PLAYER_BULLETS = 140;

    /** homing missiles per volley, by weapon level (0 = none) */
    private static final int[] MISSILES = {0, 0, 0, 2, 2, 3};

    /** sideways spread multiplier: LV5 opens the barrage up */
    public static float spreadScale(int level) { return level >= MAX_LEVEL ? 1.25f : 1f; }

    public static int missiles(int level) {
        int l = level < 1 ? 1 : (level > MAX_LEVEL ? MAX_LEVEL : level);
        return MISSILES[l];
    }

    /**
     * Straight shot pattern for a level, as flat triples of {offsetDp, angleRad, dmgScale}.
     * LV1 single, LV2 twin, LV3 triple slant, LV4 quad, LV5 six-way wide.
     */
    public static float[] pattern(int level) {
        switch (level) {
            case 1: return new float[]{0f, 0f, 1f};
            case 2: return new float[]{-7f, 0f, 1f, 7f, 0f, 1f};
            case 3: return new float[]{0f, 0f, 1f, -8f, 0.10f, 1f, 8f, -0.10f, 1f};
            case 4: return new float[]{-6f, 0f, 1f, 6f, 0f, 1f, -11f, 0.20f, 1f, 11f, -0.20f, 1f};
            default: return new float[]{-6f, 0f, 1f, 6f, 0f, 1f, -14f, 0f, 0.8f, 14f, 0f, 0.8f,
                    -16f, 0.28f, 0.9f, 16f, -0.28f, 0.9f};
        }
    }

    /** number of shots in a pattern (triples) */
    public static int shots(int level) { return pattern(level).length / 3; }

    /**
     * Nearest legal target. {@code xs}/{@code ys} are the enemies (n of them); the boss is a
     * separate legal candidate appended as index {@code n}. Returns 0..n-1 for an enemy,
     * {@code n} for the boss, -1 only when nothing is on screen.
     *
     * <p>Off-screen candidates (y above the top edge, i.e. not yet in play) never win, so a missile
     * does not lock a spawn that is still off the top of the screen.
     */
    public static int pickTarget(float[] xs, float[] ys, int n, boolean bossAlive,
                                 float bx, float by, float px, float py, float topY) {
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float x = xs[i], y = ys[i];
            if (Float.isNaN(x) || Float.isNaN(y)) continue;
            if (y < topY) continue;
            float d = (x - px) * (x - px) + (y - py) * (y - py);
            if (d < bestD) { bestD = d; best = i; }
        }
        if (bossAlive && by >= topY) {
            float d = (bx - px) * (bx - px) + (by - py) * (by - py);
            if (d < bestD) return n;
        }
        return best;
    }

    /** true when the beam should settle damage this frame (once per BEAM_TICK, not per frame) */
    public static boolean beamDue(float cooldown, float dt) {
        if (cooldown > 0f) return false;
        return true;
    }

    /** next cooldown after a settlement: always BEAM_TICK, so the cadence is fixed */
    public static float beamCooldown() { return BEAM_TICK; }
}
