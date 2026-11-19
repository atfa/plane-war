package dev.xduo.plane;

/**
 * A named multi-phase boss: a pure state machine with no Android types, so the phase logic,
 * the bullet cadence and the summon caps are unit testable on the JVM
 * (see plane/test/TestBoss.java, run by plane/test_jvm.sh).
 *
 * <p>The boss only <em>requests</em> actions ({@link #act(float, int)} returns a bitmask); the
 * view translates those requests into bullets and summoned enemies. That keeps the pattern
 * geometry, the phase thresholds and the caps out of the 2000-line GameView.
 *
 * <p>Invariants the view relies on (all asserted on the JVM):
 * <ul>
 *   <li>{@link #hpFrac()} is always in [0,1] - it drives a bar width, a negative width makes
 *       {@code Canvas.drawRoundRect} throw.</li>
 *   <li>after death {@link #hurt(float)} is a no-op and {@link #act(float, int)} reports
 *       {@link #ACT_DIE} exactly once, so a kill pays out score and drops once.</li>
 *   <li>every phase keeps firing: no window of {@link #maxGapSec(int, int)} seconds passes
 *       without an attack request, so a boss can never stall.</li>
 * </ul>
 */
public class Boss {

    public static final int COUNT = 3;
    public static final String[] NAME = {"玄铁堡垒", "雷穹母舰", "赤炎妖机"};
    public static final String[] SUB = {"重装环形弹幕", "雷暴扇区压制", "赤炎高速突袭"};
    /** ARGB hull colours, one per kind */
    public static final int[] COLOR = {0xFF96AAC8, 0xFF78C8FF, 0xFFFF785A};
    /** hull radius per kind, in dp */
    public static final float[] RADIUS_DP = {52f, 56f, 60f};

    public static final int PHASES = 3;
    /** hp fraction at which each phase starts, descending; phase never goes backwards */
    public static final float[] PHASE_AT = {1.00f, 0.66f, 0.33f};

    public static final int ACT_RING = 1;
    public static final int ACT_AIM = 2;
    public static final int ACT_FAN = 4;
    public static final int ACT_SUMMON = 8;
    /** latched, reported exactly once when the boss dies */
    public static final int ACT_DIE = 16;

    /** base hit points per kind, scaled by difficulty and by how many bosses already died */
    private static final float[] HP = {80f, 110f, 140f};
    /** seconds between attacks (at diff 0): ring / aimed burst / fan sweep / minion wave */
    private static final float[] RING_EVERY = {2.3f, 2.1f, 1.9f};
    private static final float[] AIM_EVERY = {1.2f, 1.1f, 1.0f};
    private static final float[] FAN_EVERY = {0.9f, 0.85f, 0.8f};
    private static final float[] SUMMON_EVERY = {6.5f, 6.0f, 5.5f};
    private static final int[] RING_N = {12, 14, 16};
    private static final int[] SUMMON_N = {2, 3, 3};
    /** score for killing the boss, per kind (scaled by difficulty in the view) */
    public static final int[] SCORE = {3000, 4200, 5600};
    /**
     * Longest stretch, per phase, in which at least one attack request is guaranteed - the
     * fastest timer that phase owns, worst case over the kinds. Descending: later phases are
     * busier, which is what makes a boss fight escalate.
     */
    private static final float[] MAX_GAP = {2.3f, 1.2f, 0.9f};

    private final int kind;
    private final int diff;
    private final float maxHp;
    private float hp;
    private int phase;
    private float t;
    private float ringT, aimT, fanT, summonT;
    private boolean dead;
    private boolean diePending;
    private int lastSummon;

    private Boss(int kind, int diff, float maxHp) {
        this.kind = kind;
        this.diff = diff;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.ringT = 1.0f;
        this.aimT = 1.6f;
        this.fanT = 1.2f;
        this.summonT = 3.0f;
    }

    /** boss kind for the n-th boss of a run, rotating through the table */
    public static int pick(int fightIndex) {
        int k = fightIndex % COUNT;
        return k < 0 ? k + COUNT : k;
    }

    /** hit points of a kind at a difficulty, before the per-run escalation */
    public static float hpFor(int kind, int diff) {
        int k = pick(kind);
        return HP[k] * (1f + 0.35f * Tuning.clampDiff(diff));
    }

    /**
     * @param kind which boss, wrapped into {@link #COUNT}
     * @param diff 0..2, scales hit points and attack speed
     * @param killed how many bosses the run already killed - each later one is tougher
     */
    public static Boss spawn(int kind, int diff, int killed) {
        int d = Tuning.clampDiff(diff);
        float hp = hpFor(kind, d) * (1f + 0.25f * Math.max(0, killed));
        return new Boss(pick(kind), d, hp);
    }

    /** attack speed multiplier: higher difficulty fires the same patterns faster */
    private static float speed(int diff) {
        return 1f + 0.12f * Tuning.clampDiff(diff);
    }

    /**
     * Longest guaranteed gap between attack requests for a phase, in seconds.
     * Descending in the phase, and shorter on higher difficulties.
     */
    public static float maxGapSec(int phase, int diff) {
        int p = phase < 0 ? 0 : (phase >= PHASES ? PHASES - 1 : phase);
        return MAX_GAP[p] / speed(diff);
    }

    public int kind() { return kind; }
    public String name() { return NAME[kind]; }
    public String sub() { return SUB[kind]; }
    public int color() { return COLOR[kind]; }
    public float radiusDp() { return RADIUS_DP[kind]; }
    public int score() { return SCORE[kind]; }
    public float maxHp() { return maxHp; }
    public float hp() { return hp; }
    public int phase() { return phase; }
    public boolean isDead() { return dead; }
    public float age() { return t; }

    /** always in [0,1]; drives the health bar width */
    public float hpFrac() {
        if (maxHp <= 0f) return 0f;
        float f = hp / maxHp;
        return f < 0f ? 0f : (f > 1f ? 1f : f);
    }

    /** bullets in one ring burst; the view rotates the ring over time */
    public int ringCount() { return RING_N[kind] + phase * 2; }

    /** bullets in one fan sweep; the fan widens as the boss gets hurt */
    public int fanCount() { return 5 + phase * 2; }

    /** shots per aimed burst (phase 1 and up) */
    public int aimBurst() { return 2 + phase; }

    /** minions requested by the most recent {@link #act(float, int)} call */
    public int summonCount() { return lastSummon; }

    /**
     * How many minions a wave would take if the screen had {@code room} free slots right now.
     * Same cap as the request path: fewer than two free slots means no wave at all.
     */
    public int summonCount(int room) {
        return room < 2 ? 0 : Math.min(SUMMON_N[kind], room);
    }

    /** @return true if this hit killed the boss; a dead boss cannot be hurt again */
    public boolean hurt(float dmg) {
        if (dead || dmg <= 0f) return false;
        hp -= dmg;
        if (hp <= 0f) {
            hp = 0f;                 // never negative: the bar width must stay >= 0
            dead = true;
            diePending = true;       // reported once by act()
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

    /** convenience for callers that do not care about minion slots (tests, simple patterns) */
    public int act(float dt) { return act(dt, 99); }

    /**
     * Tick the attack timers and collect the actions the view should perform this frame.
     *
     * @param dt seconds since the last frame
     * @param freeSlots enemy slots still free on screen; caps a minion wave so it is never
     *                  spawned half-way (fewer than two slots means no wave at all)
     */
    public int act(float dt, int freeSlots) {
        lastSummon = 0;
        if (dead) {
            if (diePending) {
                diePending = false;
                return ACT_DIE;
            }
            return 0;
        }
        t += dt;
        float s = dt * speed(diff);
        ringT -= s;
        aimT -= s;
        fanT -= s;
        summonT -= s;
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
                if (summonT <= 0f) {
                    summonT = SUMMON_EVERY[kind];
                    lastSummon = freeSlots < 2 ? 0 : Math.min(SUMMON_N[kind], freeSlots);
                    if (lastSummon > 0) req |= ACT_SUMMON;
                }
                break;
        }
        return req;
    }
}
