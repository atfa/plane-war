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

    /**
     * Is a bullet inside the graze ring? The ring starts where the hit box ends (inclusive
     * inner edge, exclusive outer edge), so a bullet that actually hits the ship is never
     * also a graze. Distances are in px; the ring width in px (convert dp at the call site).
     */
    public static boolean grazes(float dx, float dy, float hitR, float bandPx) {
        float d2 = dx * dx + dy * dy;
        return d2 >= hitR * hitR && d2 < (hitR + bandPx) * (hitR + bandPx);
    }

    /** combo needed to enter fever; negative combos (never happens, but) never trigger */
    public static boolean fever(int combo) {
        return combo >= FEVER_COMBO;
    }

    // ------------------------------------------------------------ difficulty tables
    //
    // These used to live in GameView, where no JVM test could reach them: the on-screen enemy cap
    // is what the boss summons, the mothership and the splitter all fight over, so it is exactly
    // the kind of number that must be assertable off-device.

    /** enemy spawn interval multiplier (lower = faster) */
    public static final float[] SPAWN = {1.00f, 0.70f, 0.48f};
    /** formation size multiplier */
    public static final float[] NUM = {1.00f, 1.25f, 1.55f};
    /** enemy hp multiplier */
    public static final float[] HP = {1.00f, 1.35f, 1.80f};
    /** enemy movement and bullet speed multiplier */
    public static final float[] SPEED = {1.00f, 1.15f, 1.32f};
    /** enemy fire-rate multiplier */
    public static final float[] FIRE = {1.00f, 1.35f, 1.75f};
    /** event interval multiplier (lower = more often) */
    public static final float[] EVENT = {1.00f, 0.85f, 0.70f};
    /** power-up drop multiplier */
    public static final float[] DROP = {1.00f, 0.85f, 0.70f};
    /** score multiplier */
    public static final float[] SCORE = {1.00f, 1.25f, 1.60f};
    /** ramp speed multiplier */
    public static final float[] RAMP = {1.00f, 1.35f, 1.70f};
    /** on-screen enemy cap - the budget every summoner shares */
    public static final int[] ENEMY_CAP = {22, 28, 34};

    public static float spawnScale(int d) { return SPAWN[clampDiff(d)]; }
    public static float hpScale(int d) { return HP[clampDiff(d)]; }
    public static float speedScale(int d) { return SPEED[clampDiff(d)]; }
    public static float scoreScale(int d) { return SCORE[clampDiff(d)]; }
    public static int enemyCap(int d) { return ENEMY_CAP[clampDiff(d)]; }

    // ------------------------------------------------------------ polish (hit-stop, streaks)

    /** kill counts that earn a streak banner, ascending */
    public static final int[] STREAK_AT = {5, 10, 20};
    private static final String[] STREAK_LABEL = {"五连杀", "十连杀", "二十连杀"};

    /**
     * The streak banner for a combo, or null when this combo is not a milestone.
     * Only exact hits trigger, so a 12-combo pays the 10 banner once and never repeats it.
     */
    public static String streakBanner(int combo) {
        for (int i = 0; i < STREAK_AT.length; i++)
            if (combo == STREAK_AT[i]) return STREAK_LABEL[i];
        return null;
    }

    /**
     * Hit-stop length in seconds for a kill: a heavy, a mothership and a boss phase change are
     * the beats the player should feel; a grunt is not. 0 means no hit-stop.
     */
    public static float hitStopFor(int type, boolean bossKill) {
        if (bossKill) return 0.16f;
        if (type == 4) return 0.09f;             // heavy
        if (type == 8) return 0.07f;             // mothership
        return 0f;
    }

    /** the world time scale during a hit-stop: a freeze, not a slow-motion */
    public static final float HIT_STOP_SCALE = 0.08f;

    /** how many off-screen threats one frame may mark with an edge arrow, so a full screen cannot spam */
    public static final int MAX_DANGER_ARROWS = 6;

    /**
     * How close the autopilot lets a bullet come before it dodges, in dp. The graze ring ends at
     * roughly dp(65) for a normal bullet, so dodging at dp(70) means bullets pass through the band
     * and the automated playtest produces real grazes instead of only hook ones.
     */
    public static final float AUTO_FLEE_DP = 70f;

    /** ...but right after taking a hit the autopilot backs off, so a non-god run stays alive */
    public static final float AUTO_FLEE_SAFE_DP = 104f;

    /** hit-stop is capped per second, so a streak cannot freeze the game into a stutter */
    public static final float HIT_STOP_BUDGET = 0.35f;

    static int clampDiff(int d) {
        return d < 0 ? 0 : (d > 2 ? 2 : d);
    }
}
