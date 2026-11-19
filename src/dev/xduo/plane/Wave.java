package dev.xduo.plane;

/**
 * Formation waves: pattern geometry as pure Java (no Android imports), so the JVM suite
 * (plane/test/TestLogic.java, run by plane/test_jvm.sh) can pin that every member lands on screen,
 * that the difficulty ramp caps the size, and that the gap in a wall is a real gap.
 *
 * <p>The view asks for a pattern, gets back lanes (x fractions of the play width) and delays, and
 * spawns the members over time. A wave that spawns 8 members at once reads as a wall of bullets,
 * not a formation, so the delays are part of the design, not an implementation detail.
 */
public final class Wave {

    private Wave() {}

    public static final int V = 0, LINE = 1, PINCER = 2, SINE = 3, WALL = 4;
    public static final int COUNT = 5;
    public static final String[] NAME = {"V字", "一字", "双侧钳形", "正弦列车", "缺口墙"};

    /** new enemy types: splitter, miner, sniper, mothership (5..8, on top of the original 0..4) */
    public static final int SPLITTER = 5, MINER = 6, SNIPER = 7, MOTHER = 8;
    public static final int TYPES = 9;
    public static final String[] TYPE_NAME = {
            "杂兵", "俯冲机", "炮艇", "精英", "重型机", "分裂体", "布雷艇", "狙击手", "母舰"};

    /** members per formation: grows with the difficulty and with the ramp, and is capped */
    public static int size(int kind, int diff, float rampSec) {
        int d = diff < 0 ? 0 : (diff > 2 ? 2 : diff);
        int base;
        switch (kind) {
            case LINE: base = 4; break;
            case WALL: base = 5; break;
            case PINCER: base = 4; break;
            case SINE: base = 5; break;
            default: base = 5; break;      // V
        }
        int grow = (int) (rampSec / 60f);
        int n = base + d + (grow > 3 ? 3 : grow);
        int cap = kind == WALL ? 9 : 8;
        return n > cap ? cap : n;
    }

    /** the gap lane in a wall: always strictly inside the row, so the player can always thread it */
    public static int gapIndex(int n) {
        if (n <= 2) return 0;
        return 1 + (n - 3) / 2;
    }

    /**
     * Lane x fractions in [0,1] for a formation of {@code n} members. Every value is inside the
     * interval, so after the view maps it to pixels with a margin no member spawns off screen.
     */
    public static float[] lanes(int kind, int n) {
        float[] x = new float[n];
        if (n <= 0) return x;
        if (n == 1) { x[0] = 0.5f; return x; }
        switch (kind) {
            case PINCER: {
                // two columns closing in from both edges
                for (int i = 0; i < n; i++) {
                    int k = i / 2;
                    float f = (n <= 2) ? 0f : (float) k / ((n + 1) / 2 - 1);
                    x[i] = clamp01((i % 2 == 0) ? 0.08f + f * 0.34f : 0.92f - f * 0.34f);
                }
                break;
            }
            case V: {
                // apex in the middle, arms swept back
                int mid = n / 2;
                for (int i = 0; i < n; i++) {
                    int d = Math.abs(i - mid);
                    x[i] = clamp01(0.5f + (i - mid) * 0.16f);
                    if (x[i] > 0.96f) x[i] = 0.96f;
                    if (x[i] < 0.04f) x[i] = 0.04f;
                    d = d;                       // the apex leads, the arms trail (see delays)
                }
                break;
            }
            case WALL: {
                int gap = gapIndex(n);
                int j = 0;
                for (int i = 0; i < n; i++) {
                    if (i == gap) continue;
                    x[i] = clamp01(0.06f + (n <= 1 ? 0f : (float) j / (n - 1)) * 0.88f);
                    j++;
                }
                x[gap] = -1f;                    // -1 marks the gap: no enemy there
                break;
            }
            case SINE: {
                for (int i = 0; i < n; i++) x[i] = 0.5f;
                break;
            }
            default: {                           // LINE
                for (int i = 0; i < n; i++)
                    x[i] = clamp01(0.08f + (n <= 1 ? 0f : (float) i / (n - 1)) * 0.84f);
                break;
            }
        }
        return x;
    }

    /** seconds between members; the apex of a V leads, the arms trail */
    public static float[] delays(int kind, int n) {
        float[] d = new float[n];
        int mid = n / 2;
        for (int i = 0; i < n; i++) {
            switch (kind) {
                case V: d[i] = Math.abs(i - mid) * 0.16f; break;
                case SINE: d[i] = i * 0.22f; break;
                case PINCER: d[i] = (i / 2) * 0.20f; break;
                case WALL: d[i] = 0f; break;
                default: d[i] = i * 0.10f; break;
            }
        }
        return d;
    }

    /** which enemy type fills a formation, by ramp time; the new types join after 40s */
    public static int typeFor(int kind, float rampSec) {
        if (rampSec < 40f) return kind == WALL ? 0 : (kind == SINE ? 0 : 0);
        if (rampSec < 70f) return kind == WALL ? 0 : 1;
        if (kind == WALL) return 0;
        if (kind == SINE) return 1;
        return 2;
    }

    /** the new types appear in formations of their own, gated by the ramp */
    public static int newTypeFor(int index, float rampSec) {
        if (rampSec < 20f) return 0;
        int[] pool = {SPLITTER, MINER, SNIPER, MOTHER};
        int k = index % pool.length;
        if (pool[k] == MOTHER && rampSec < 60f) return SPLITTER;
        return pool[k];
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
}
