package dev.termux.planewar;

/**
 * The ship art contract: one row per aircraft - player, the 9 enemy types, the 3 bosses - holding
 * its identity colour, its hull/accent/cockpit palette, its silhouette id and its part ratios.
 *
 * <p>Pure Java - no Android imports - so plane/test_jvm.sh can assert it on the JVM (plane/test/
 * TestShips.java). The dependency runs one way: Ships.java (which does pull in the Android graphics
 * classes) consumes these constants and draws the silhouette named by {@link #silhouette(int)},
 * this file never references Ships, so the JVM suite compiles without android.jar. This is the same
 * split UiSpec/Icons established for the HUD.
 *
 * <p>Two rules in here are not design freedom, they are regression pins:
 * <ul>
 *   <li><b>identity colours are byte-exact.</b> plane/check.py samples a screenshot for nine RGB
 *       buckets with a tolerance of 34 per channel, and plane/test.sh gates on those samples. A
 *       glow is not enough to satisfy it: {@code glow()} paints the colour at alpha 0.35 over a
 *       near-black background, which reads as roughly (132,60,67) for the grunt red - outside the
 *       bucket. The bucket is hit because the hull used to be filled with the flat identity colour.
 *       So Ships must paint one fully opaque element in {@link #identity(int)} per aircraft, of at
 *       least {@link #coreRadiusDp(int)} - which is why that number has a floor.</li>
 *   <li><b>the buckets are shared.</b> miner (255,200,90) also lands in the gold-text, powerbox
 *       amber and darter buckets; mothership (120,200,255) shares the player-cyan bucket; sniper
 *       (255,60,60) is separated from grunt (255,108,108) by 48 in green, barely past the
 *       tolerance. Moving any of these bytes merges two buckets or empties one.</li>
 * </ul>
 *
 * <p>{@link #radiusDp(int)} carries the hit radius each aircraft already had, and the art layer
 * sizes itself from it. Gameplay never reads it - the radii live in GameView.spawnEnemy and
 * Boss.RADIUS_DP - so pinning them here is a machine-checkable statement that the art rewrite
 * changed no gameplay number.
 */
public final class ShipSpec {

    private ShipSpec() {}

    // ---- id space: 0..8 are the enemy types (the numbering Wave/GameView already use),
    //      9 is the player, 10..12 are the boss kinds in Boss.NAME order ----
    public static final int GRUNT = 0, DARTER = 1, GUNSHIP = 2, ELITE = 3, HEAVY = 4,
            SPLITTER = 5, MINER = 6, SNIPER = 7, MOTHER = 8;
    public static final int ENEMY_TYPES = 9;
    public static final int PLAYER = 9, BOSS_0 = 10, BOSS_1 = 11, BOSS_2 = 12;
    public static final int COUNT = 13;

    /** enemy type -> art id; -1 for out-of-range types, so callers can assert */
    public static int ofEnemy(int type) { return type >= 0 && type < ENEMY_TYPES ? type : -1; }

    /** boss kind -> art id; -1 for out-of-range kinds */
    public static int ofBoss(int kind) { return kind >= 0 && kind < Boss.COUNT ? BOSS_0 + kind : -1; }

    public static boolean valid(int id) { return id >= 0 && id < COUNT; }
    public static boolean isEnemy(int id) { return id >= 0 && id < ENEMY_TYPES; }
    public static boolean isPlayer(int id) { return id == PLAYER; }
    public static boolean isBoss(int id) { return id >= BOSS_0 && id < BOSS_0 + Boss.COUNT; }

    // ---- silhouettes: one id per aircraft, and Ships paints a distinct shape for each.
    //      They are ordered like the ids so a missing case in Ships shows up as a duplicate. ----
    public static final int SIL_INTERCEPTOR = 0, SIL_DIAMOND = 1, SIL_ARROW = 2, SIL_HEXPOD = 3,
            SIL_PENTAGON = 4, SIL_BROADSIDE = 5, SIL_LOBES = 6, SIL_BARGE = 7, SIL_NEEDLE = 8,
            SIL_SAUCER = 9, SIL_FORTRESS = 10, SIL_STORM = 11, SIL_WRAITH = 12;
    public static final int SIL_COUNT = 13;

    private static final int[] SIL = {
            SIL_DIAMOND, SIL_ARROW, SIL_HEXPOD, SIL_PENTAGON, SIL_BROADSIDE,
            SIL_LOBES, SIL_BARGE, SIL_NEEDLE, SIL_SAUCER,
            SIL_INTERCEPTOR, SIL_FORTRESS, SIL_STORM, SIL_WRAITH};

    private static final String[] SIL_NAME = {
            "diamond", "arrow", "hexpod", "pentagon", "broadside",
            "lobes", "barge", "needle", "saucer",
            "interceptor", "fortress", "storm", "wraith"};

    /** Chinese names, for logs and the title-screen legend; enemies 0..8 reuse Wave.TYPE_NAME. */
    private static final String[] NAME = {
            "杂兵", "俯冲机", "炮艇", "精英", "重型机", "分裂体", "布雷艇", "狙击手", "母舰",
            "玩家机", "玄铁堡垒", "雷穹母舰", "赤炎妖机"};

    public static int silhouette(int id) { return valid(id) ? SIL[id] : -1; }
    public static String silhouetteName(int id) { return valid(id) ? SIL_NAME[SIL[id]] : "?"; }
    public static String name(int id) { return valid(id) ? NAME[id] : "?"; }

    // ---- colours, as ARGB. The identity column is the byte-exact colour the game has always
    //      used for this aircraft; check.py samples it, so it is pinned in TestShips. ----
    private static final int[] IDENTITY = {
            0xFFFF6C6C,  // grunt   255,108,108  GameView.E_GRUNT
            0xFFFFAC40,  // darter  255,172,64   GameView.E_DARTER
            0xFFBC7CFF,  // gunship 188,124,255  GameView.E_GUNSHIP
            0xFF60ECB0,  // elite    96,236,176  GameView.E_ELITE
            0xFFFF58BE,  // heavy   255,88,190   GameView.E_HEAVY
            0xFF96FF82,  // splitter 150,255,130 GameView, inline
            0xFFFFC85A,  // miner   255,200,90   GameView, inline
            0xFFFF3C3C,  // sniper  255,60,60    GameView, inline
            0xFF78C8FF,  // mother  120,200,255  GameView, inline
            0xFF78E0FF,  // player  120,224,255  GameView.SHIP
            0xFF96AAC8,  // boss 0 玄铁堡垒       Boss.COLOR[0]
            0xFF78C8FF,  // boss 1 雷穹母舰       Boss.COLOR[1]
            0xFFFF785A}; // boss 2 赤炎妖机       Boss.COLOR[2]

    /** secondary plating: wings, pods, intakes - a dark, saturated version of the identity */
    private static final int[] ACCENT = {
            0xFF8C2030, 0xFF8A4410, 0xFF3C1E66, 0xFF14684C, 0xFF78104A,
            0xFF2C6E28, 0xFF6E4410, 0xFF6E1414, 0xFF14476E,
            0xFF1C60A8, 0xFF3C4A60, 0xFF184A78, 0xFF7A2410};

    /** cockpit glass base, drawn under the glass highlight */
    private static final int[] COCKPIT = {
            0xFF2A0A10, 0xFF2A1400, 0xFF160A2E, 0xFF04241A, 0xFF2E0620,
            0xFF0C2A08, 0xFF2A1A04, 0xFF280404, 0xFF04182A,
            0xFF142846, 0xFF101820, 0xFF081E32, 0xFF2A0A04};

    /** rim light / panel edging: a pale tint of the identity, used at low alpha for panel lines */
    private static final int[] TRIM = {
            0xFFFFD2C8, 0xFFFFE4B0, 0xFFE4D2FF, 0xFFD2FFEC, 0xFFFFC8E4,
            0xFFE0FFD0, 0xFFFFE8C0, 0xFFFFC8C8, 0xFFD8EEFF,
            0xFFDCFAFF, 0xFFE0ECFF, 0xFFD8F0FF, 0xFFFFD8C0};

    private static final int WHITE = 0xFFFFFFFF, DEEP = 0xFF070C18;

    /** hull gradient stops, derived from the identity so the ship reads as one colour family */
    private static final int[] HULL_TOP = new int[COUNT];
    private static final int[] HULL_BOT = new int[COUNT];

    static {
        for (int i = 0; i < COUNT; i++) {
            HULL_TOP[i] = mix(IDENTITY[i], WHITE, 0.42f);
            HULL_BOT[i] = mix(IDENTITY[i], DEEP, 0.70f);
        }
    }

    public static int identity(int id) { return valid(id) ? IDENTITY[id] : WHITE; }
    public static int hullTop(int id) { return valid(id) ? HULL_TOP[id] : WHITE; }
    public static int hullBottom(int id) { return valid(id) ? HULL_BOT[id] : WHITE; }
    public static int accent(int id) { return valid(id) ? ACCENT[id] : DEEP; }
    public static int cockpit(int id) { return valid(id) ? COCKPIT[id] : DEEP; }
    public static int trim(int id) { return valid(id) ? TRIM[id] : WHITE; }

    /**
     * Outer glow colour. It is the identity colour because that is what drawEnemies passed to
     * glow() before this file existed, and the halo is what makes a ship readable at a glance
     * across a screen full of bullets.
     */
    public static int glow(int id) { return identity(id); }

    // ---- part ratios, all fractions of the aircraft radius r, in unit space [-1,1] ----
    /** half-width of the wing plan, so 1.0 means the wings reach the edge of r */
    private static final float[] WING = {
            0.86f, 0.92f, 1.00f, 0.90f, 1.20f,
            0.96f, 1.00f, 0.72f, 1.00f,
            0.95f, 1.10f, 1.14f, 1.06f};

    /** engine pods on the tail (1..4); drives the flame count, never a gameplay number */
    private static final int[] ENGINES = {
            1, 2, 2, 2, 4,
            2, 2, 1, 3,
            2, 4, 3, 4};

    /** cockpit glass radius */
    private static final float[] COCKPIT_R = {
            0.26f, 0.22f, 0.30f, 0.28f, 0.34f,
            0.26f, 0.28f, 0.20f, 0.30f,
            0.24f, 0.30f, 0.28f, 0.26f};

    /** exhaust flame length, multiplied by the per-frame flicker in the view */
    private static final float[] FLAME = {
            0.90f, 1.30f, 0.70f, 0.95f, 0.60f,
            0.80f, 0.70f, 1.40f, 0.55f,
            1.60f, 0.50f, 0.70f, 1.10f};

    /**
     * Radius of the opaque core lamp painted in the exact identity colour. This is the element
     * check.py samples, so it has a floor: see the class comment.
     */
    private static final float[] CORE_R = {
            0.22f, 0.24f, 0.20f, 0.22f, 0.18f,
            0.22f, 0.20f, 0.24f, 0.18f,
            0.20f, 0.16f, 0.16f, 0.16f};

    /**
     * Hit radius in dp, byte for byte the number the game already used: GameView.spawnEnemy for
     * the enemies, dp(16) in drawShip for the player, Boss.RADIUS_DP for the bosses. Art reads it
     * to size itself; gameplay never does.
     */
    private static final float[] RADIUS_DP = {
            15f, 13f, 23f, 19f, 46f,
            20f, 24f, 15f, 38f,
            16f, 52f, 56f, 60f};

    /**
     * Draw-call budget per aircraft: how many layers Ships is allowed to paint. The render loop
     * runs at 60 fps with up to Tuning.ENEMY_CAP[2] = 34 enemies on screen, on a phone CPU, in
     * software Canvas, so a grunt at dp(15) must not carry the same 8-layer recipe as a boss at
     * dp(60). Small ships get glow + hull + core + flame; only the big ones get panel lines,
     * rim light, pods and intakes on top.
     */
    private static final int[] LAYERS = {
            4, 4, 6, 6, 7,
            6, 6, 4, 7,
            8, 9, 9, 9};

    public static float wingSpan(int id) { return valid(id) ? WING[id] : 0f; }
    public static int engines(int id) { return valid(id) ? ENGINES[id] : 0; }
    public static float cockpitR(int id) { return valid(id) ? COCKPIT_R[id] : 0f; }
    public static float flameLen(int id) { return valid(id) ? FLAME[id] : 0f; }
    public static float coreR(int id) { return valid(id) ? CORE_R[id] : 0f; }
    public static float radiusDp(int id) { return valid(id) ? RADIUS_DP[id] : 0f; }
    public static int layers(int id) { return valid(id) ? LAYERS[id] : 0; }

    /** Core lamp radius in dp: what the identity-colour element must be at least, for check.py. */
    public static float coreRadiusDp(int id) { return RADIUS_DP[id] * CORE_R[id]; }

    /** Smallest core that survives check.py's 3-pixel sampling grid on the smallest layout. */
    public static final float CORE_MIN_DP = 2.5f;

    // ---- frame budget ----
    private static int maxEnemyLayers() {
        int m = 0;
        for (int i = 0; i < ENEMY_TYPES; i++) if (LAYERS[i] > m) m = LAYERS[i];
        return m;
    }

    private static int maxBossLayers() {
        int m = 0;
        for (int i = BOSS_0; i < COUNT; i++) if (LAYERS[i] > m) m = LAYERS[i];
        return m;
    }

    /**
     * Worst-case ship draw calls in one frame: a full enemy field at the heaviest difficulty, each
     * at the richest enemy recipe plus its glow, plus the player and a boss. Ships must stay inside
     * this, because the fps>=45 gate in plane/test.sh is measured with exactly this on screen.
     */
    public static int frameOps(int diff) {
        return Tuning.enemyCap(diff) * (maxEnemyLayers() + 1) + LAYERS[PLAYER] + maxBossLayers();
    }

    /** The budget frameOps is asserted against, at the heaviest difficulty. */
    public static final int FRAME_OP_LIMIT = 300;

    // ---- colour maths, shared with Ships so the two sides mix colours identically ----

    /** Linear per-channel blend; t = 0 gives a, t = 1 gives b. Alpha blends too. */
    public static int mix(int a, int b, float t) {
        float u = t < 0f ? 0f : (t > 1f ? 1f : t);
        int aa = (a >>> 24) + (int) (((b >>> 24) - (a >>> 24)) * u);
        int ar = (a >> 16 & 0xFF) + (int) (((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * u);
        int ag = (a >> 8 & 0xFF) + (int) (((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * u);
        int ab = (a & 0xFF) + (int) (((b & 0xFF) - (a & 0xFF)) * u);
        return aa << 24 | ar << 16 | ag << 8 | ab;
    }

    /** t = 1 is black. Used for panel lines and shadowed plating. */
    public static int shade(int c, float t) { return mix(c, 0xFF000000, t); }

    /** t = 1 is white. Used for rim light and the lit side of a hull. */
    public static int tint(int c, float t) { return mix(c, WHITE, t); }

    /** Replace the alpha channel; keeps the colour, so a shader stop can fade without shifting hue. */
    public static int alpha(int c, int a) {
        int aa = a < 0 ? 0 : (a > 255 ? 255 : a);
        return aa << 24 | (c & 0x00FFFFFF);
    }

    public static int red(int c) { return c >> 16 & 0xFF; }
    public static int green(int c) { return c >> 8 & 0xFF; }
    public static int blue(int c) { return c & 0xFF; }
    public static int alphaOf(int c) { return c >>> 24; }
}
