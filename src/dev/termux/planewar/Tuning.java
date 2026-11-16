package dev.termux.planewar;

/**
 * Numbers for the new "more spectacular" gameplay: super blast, bomb stock, graze, fever,
 * bosses, new enemies. Everything that needs to differ per difficulty lives here, so the
 * balance can be read (and unit tested) in one place.
 *
 * Pure Java - no Android imports - so plane/test_jvm.sh can assert it on the JVM.
 */
public final class Tuning {

    private Tuning() {}

    // ---- energy / super blast ----
    public static final float ENERGY_MAX = 100f;
    /** passive charge, energy per second of flight (so a run always reaches one super) */
    public static final float[] ENERGY_PER_SEC = {1.6f, 1.4f, 1.2f};
    /** charge per enemy type: 0 grunt, 1 darter, 2 gunship, 3 elite, 4 heavy */
    public static final float[] KILL_ENERGY = {4f, 5f, 9f, 12f, 28f};
    /** super blast damage scale per difficulty */
    public static final float[] SUPER_DMG = {1.00f, 1.15f, 1.30f};
    /** flat score bonus of a super blast, per difficulty */
    public static final float[] SUPER_SCORE = {400f, 500f, 640f};

    // ---- bombs (player stock) ----
    public static final int BOMB_START = 1;
    public static final int BOMB_MAX = 3;
    /** damage of the player's bomb; the EMP event keeps its own stronger number */
    public static final float[] BOMB_DMG = {26f, 30f, 34f};
    /** score bonus for picking up a bomb when the stock is already full */
    public static final int BOMB_FULL_SCORE = 400;

    // ---- graze (fly close to enemy bullets, get charged) ----
    /** inner radius = the hit box, outer radius = the graze band, in dp */
    public static final float GRAZE_RADIUS_DP = 46f;
    public static final float GRAZE_ENERGY = 1.6f;
    /** seconds of grace before the same bullet can graze again */
    public static final float GRAZE_COOLDOWN = 0.25f;

    // ---- combo fever ----
    public static final int FEVER_COMBO = 30;
    public static final float FEVER_SCORE_MULT = 2f;
    public static final float FEVER_FIRE_RATE = 0.7f;   // fire gap multiplier

    // ---- autopilot (test hooks use the same code path as the player) ----
    public static final float AUTO_SKILL_GAP = 2.5f;    // s between autopilot skill uses
    public static final int AUTO_BOMB_ENEMIES = 12;     // bomb a dense screen

    public static float energyPerSec(int diff) {
        return ENERGY_PER_SEC[clampDiff(diff)];
    }

    public static float killEnergy(int type) {
        int t = type < 0 ? 0 : (type > 4 ? 4 : type);
        return KILL_ENERGY[t];
    }

    public static float superDamage(int diff) {
        return SUPER_DMG[clampDiff(diff)];
    }

    public static float superScore(int diff) {
        return SUPER_SCORE[clampDiff(diff)];
    }

    public static float bombDamage(int diff) {
        return BOMB_DMG[clampDiff(diff)];
    }

    static int clampDiff(int d) {
        return d < 0 ? 0 : (d > 2 ? 2 : d);
    }
}
