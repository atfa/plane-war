package dev.termux.planewar;

import java.util.Random;

/**
 * The five super blast themes. This class only describes what a theme does (damage, shape,
 * extra effects); GameView.java executes it on the real board.
 *
 * Pure Java - no Android imports - so plane/test_jvm.sh can assert it on the JVM.
 */
public final class Super {

    private Super() {}

    public static final int METEOR = 0, LIGHTNING = 1, HOLY = 2, QUAKE = 3, FREEZE = 4;
    public static final int COUNT = 5;

    public static final String[] NAME = {
            "烈焰流星", "万雷齐鸣", "圣光普照", "大地裂变", "冰封万里"
    };
    public static final String[] SUB = {
            "流星三连爆", "六道雷柱贯屏", "全场净化 +1 命", "地裂波自下而上", "全场冰封 4 秒"
    };
    /** rgb, one per theme (used for the banner, the glow and the particles) */
    public static final int[] COLOR = {
            0xFFFF6A2A, 0xFF9AD8FF, 0xFFFFF3C0, 0xFFC79A5A, 0xFF8FE8FF
    };

    /** base damage per theme (scaled by Tuning.superDamage(diff)) */
    static final float[] DMG = {46f, 58f, 34f, 44f, 22f};
    /** how many impact points the theme creates (meteors / lightning columns); 0 = screen wide */
    static final int[] HITS = {3, 6, 0, 0, 0};
    /** radius of one impact point, in dp (0 = whole screen) */
    static final float[] RADIUS_DP = {70f, 42f, 0f, 0f, 0f};

    /**
     * The largest impact count of any theme. Views size their effect arrays with this, never
     * with COUNT - the two are different numbers and mixing them up is an
     * ArrayIndexOutOfBoundsException on the theme that has the most impacts (万雷齐鸣: 6).
     */
    public static final int MAX_IMPACTS = maxHits();

    private static int maxHits() {
        int m = 0;
        for (int h : HITS) if (h > m) m = h;
        return m;
    }

    public static String name(int theme) { return NAME[clamp(theme)]; }
    public static String sub(int theme) { return SUB[clamp(theme)]; }
    public static int color(int theme) { return COLOR[clamp(theme)]; }

    /** damage of one impact point (or of the whole-screen themes) */
    public static float damage(int theme, int diff) {
        return DMG[clamp(theme)] * Tuning.superDamage(diff);
    }

    /** number of impact points the theme creates */
    public static int impacts(int theme) {
        return HITS[clamp(theme)];
    }

    /** radius of one impact point in dp; 0 = the theme hits the whole screen */
    public static float radiusDp(int theme) {
        return RADIUS_DP[clamp(theme)];
    }

    /** every theme sweeps the screen clean of enemy bullets */
    public static boolean clearsBullets(int theme) { return true; }

    /** 圣光普照 restores one life */
    public static boolean heals(int theme) { return clamp(theme) == HOLY; }

    /** 冰封万里 freezes every enemy in place */
    public static boolean freezes(int theme) { return clamp(theme) == FREEZE; }

    public static float freezeSeconds() { return 4f; }

    /** 大地裂变 is a wave that travels up the screen instead of an instant hit */
    public static boolean sweeps(int theme) { return clamp(theme) == QUAKE; }

    public static int pick(Random r) {
        return r == null ? 0 : Math.abs(r.nextInt()) % COUNT;
    }

    static int clamp(int t) { return t < 0 ? 0 : (t >= COUNT ? COUNT - 1 : t); }
}
