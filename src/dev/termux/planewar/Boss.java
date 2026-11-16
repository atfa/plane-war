package dev.termux.planewar;

/**
 * A named multi-phase boss: a pure state machine with no Android types, so the phase logic and
 * the bullet cadence are unit testable on the JVM (see plane/test/TestLogic.java).
 *
 * <p>The boss only <em>requests</em> actions ({@link #act(float)} returns a bitmask); the view
 * translates those requests into bullets and summoned enemies. That keeps the pattern geometry,
 * the phase thresholds and the summon caps out of the 2000-line GameView.
 */
public class Boss {

    public static final int COUNT = 3;
    public static final String[] NAME = {"玄铁堡垒", "雷穹母舰", "赤炎妖机"};
    public static final String[] SUB = {"重装环形弹幕", "雷暴扇区压制", "赤炎高速突袭"};

    public static final int PHASES = 3;
    /** hp fraction at which each phase starts, descending; phase never goes backwards */
    public static final float[] PHASE_AT = {1.00f, 0.66f, 0.33f};

    public static final int ACT_RING = 1;
    public static final int ACT_AIM = 2;
    public static final int ACT_FAN = 4;
    public static final int ACT_SUMMON = 8;

    /** base hit points per kind, scaled by difficulty and by how many bosses already died */
    private static final float[] HP = {420f, 520f, 620f};
    /** seconds between attacks of each kind, per phase index (phase 0 ring, 1 aimed, 2 fan) */
    private static final float[] RING_EVERY = {2.3f, 2.1f, 1.9f};
    private static final float[] AIM_EVERY = {1.2f, 1.1f, 1.0f};
    private static final float[] FAN_EVERY = {1.7f, 1.6f, 1.5f};
    private static final float[] SUMMON_EVERY = {6.5f, 6.0f, 5.5f};
    private static final int[] RING_N = {12, 14, 16};
    private static final int[] SUMMON_N = {2, 3, 3};
    /** score for killing the boss, per kind (scaled by difficulty in the view) */
    private static final int[] SCORE = {3000, 4200, 5600};

    private final int kind;
    private final float maxHp;
    private float hp;
    private int phase;
    private float t;
    private float ringT, aimT, fanT, summonT;
    private boolean dead;

    private Boss(int kind, float maxHp) {
        this.kind = kind;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.ringT = 1.0f;
        this.aimT = 1.6f;
        this.fanT = 2.2f;
        this.summonT = 3.0f;
    }

    /**
     * @param kind which boss, wrapped into {@link #COUNT}
     * @param diff 0..2, scales hit points
     * @param killed how many bosses the run already killed - each later one is tougher
     */
    public static Boss spawn(int kind, int diff, int killed) {
        int k = kind % COUNT;
        if (k < 0) k += COUNT;
        int d = Tuning.clampDiff(diff);
        float hp = HP[k] * (1f + 0.35f * d) * (1f + 0.25f * Math.max(0, killed));
        return new Boss(k, hp);
    }

    public int kind() { return kind; }
    public String name() { return NAME[kind]; }
    public String sub() { return SUB[kind]; }
    public int score() { return SCORE[kind]; }
    public float maxHp() { return maxHp; }
    public float hp() { return hp; }
    public int phase() { return phase; }
    public float hpFrac() { return maxHp <= 0f ? 0f : hp / maxHp; }
    public boolean isDead() { return dead; }
    public float age() { return t; }

    /** bullets in one ring burst; the view rotates the ring over time */
    public int ringCount() { return RING_N[kind] + phase * 2; }

    /** bullets in one fan sweep; the fan widens as the boss gets hurt */
    public int fanCount() { return 5 + phase * 2; }

    /** shots per aimed burst (phase 1 and up) */
    public int aimBurst() { return 2 + phase; }

    /**
     * How many minions to spawn on an {@link #ACT_SUMMON} request.
     *
     * @param room enemy slots free on screen; a wave smaller than two is not worth spawning,
     *             so a nearly full screen returns 0 instead of a half formation
     */
    public int summonCount(int room) {
        if (room < 2) return 0;
        return Math.min(SUMMON_N[kind], room);
    }

    /** @return true if this hit killed the boss */
    public boolean hurt(float dmg) {
        if (dead || dmg <= 0f) return false;
        hp -= dmg;
        if (hp <= 0f) {
            hp = 0f;
            dead = true;
            return true;
        }
        // phase only moves forward, so a phase never plays twice
        int p = phaseFor(hpFrac());
        if (p > phase) phase = p;
        return false;
    }

    /** phase a given hp fraction belongs to */
    public static int phaseFor(float frac) {
        int p = 0;
        for (int i = 0; i < PHASES; i++) if (frac <= PHASE_AT[i]) p = i;
        return p;
    }

    /**
     * Tick the attack timers and collect the actions the view should perform this frame.
     * A dead boss requests nothing.
     */
    public int act(float dt) {
        if (dead) return 0;
        t += dt;
        ringT -= dt;
        aimT -= dt;
        fanT -= dt;
        summonT -= dt;
        int req = 0;
        switch (phase) {
            case 0:
                if (ringT <= 0f) { ringT = RING_EVERY[kind]; req |= ACT_RING; }
                break;
            case 1:
                if (aimT <= 0f) { aimT = AIM_EVERY[kind]; req |= ACT_AIM; }
                if (ringT <= 0f) { ringT = RING_EVERY[kind] * 1.5f; req |= ACT_RING; }
                break;
            default:
                if (fanT <= 0f) { fanT = FAN_EVERY[kind]; req |= ACT_FAN; }
                if (aimT <= 0f) { aimT = AIM_EVERY[kind] * 1.3f; req |= ACT_AIM; }
                if (summonT <= 0f) { summonT = SUMMON_EVERY[kind]; req |= ACT_SUMMON; }
                break;
        }
        return req;
    }
}
