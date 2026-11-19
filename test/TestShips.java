package dev.xduo.plane;

/**
 * JVM assertions for the ship art contract in ShipSpec (no Android, no device).
 *
 * <p>What a device run cannot check cheaply, and what an art refactor breaks first: that the
 * identity colours are the exact bytes plane/check.py samples out of a screenshot, that the hit
 * radii the art is sized from are the numbers gameplay already used, that every aircraft has its
 * own silhouette so Ships has 13 distinct shapes to paint, that the part ratios stay inside the
 * unit box, that the opaque identity-colour core is big enough for check.py's 3-pixel sampling
 * grid, and that the per-aircraft layer budget keeps the worst-case frame inside the op count the
 * fps>=45 gate can survive.
 *
 * <p>The colour table is written out as literals on purpose: that is the regression pin, the same
 * trick TestUi uses for the power-up palette.
 */
public class TestShips {

    static int checks, failed;

    static void ok(boolean c, String what) {
        checks++;
        if (!c) { failed++; System.out.println("  FAIL: " + what); }
    }

    /** check.py's per-channel tolerance. */
    static final int TOL = 34;

    /** The pre-refactor identity colours, as literals: this is the regression pin. */
    static int oldIdentity(int id) {
        switch (id) {
            case ShipSpec.GRUNT: return 0xFFFF6C6C;      // GameView.E_GRUNT   255,108,108
            case ShipSpec.DARTER: return 0xFFFFAC40;     // GameView.E_DARTER  255,172,64
            case ShipSpec.GUNSHIP: return 0xFFBC7CFF;    // GameView.E_GUNSHIP 188,124,255
            case ShipSpec.ELITE: return 0xFF60ECB0;      // GameView.E_ELITE    96,236,176
            case ShipSpec.HEAVY: return 0xFFFF58BE;      // GameView.E_HEAVY   255,88,190
            case ShipSpec.SPLITTER: return 0xFF96FF82;   // 150,255,130
            case ShipSpec.MINER: return 0xFFFFC85A;      // 255,200,90
            case ShipSpec.SNIPER: return 0xFFFF3C3C;     // 255,60,60
            case ShipSpec.MOTHER: return 0xFF78C8FF;     // 120,200,255
            case ShipSpec.PLAYER: return 0xFF78E0FF;     // GameView.SHIP      120,224,255
            case ShipSpec.BOSS_0: return 0xFF96AAC8;     // Boss.COLOR[0]
            case ShipSpec.BOSS_1: return 0xFF78C8FF;     // Boss.COLOR[1]
            default: return 0xFFFF785A;                  // Boss.COLOR[2]
        }
    }

    /** The hit radii the art is sized from: GameView.spawnEnemy, drawShip, Boss.RADIUS_DP. */
    static float oldRadius(int id) {
        switch (id) {
            case ShipSpec.GRUNT: return 15f;
            case ShipSpec.DARTER: return 13f;
            case ShipSpec.GUNSHIP: return 23f;
            case ShipSpec.ELITE: return 19f;
            case ShipSpec.HEAVY: return 46f;
            case ShipSpec.SPLITTER: return 20f;
            case ShipSpec.MINER: return 24f;
            case ShipSpec.SNIPER: return 15f;
            case ShipSpec.MOTHER: return 38f;
            case ShipSpec.PLAYER: return 16f;
            default: return Boss.RADIUS_DP[id - ShipSpec.BOSS_0];
        }
    }

    /**
     * Pairs that share a check.py bucket, i.e. are within tolerance of each other. They are legal -
     * check.py only needs a non-zero count per bucket - but they are documented, so a colour change
     * that merges two buckets by accident shows up as an unexpected pair here.
     */
    static boolean sharedBucket(int a, int b) {
        int[][] pairs = {
                {ShipSpec.DARTER, ShipSpec.MINER},     // 255,172,64 / 255,200,90
                {ShipSpec.MOTHER, ShipSpec.PLAYER},    // 120,200,255 / 120,224,255
                {ShipSpec.MOTHER, ShipSpec.BOSS_1},    // identical colour
                {ShipSpec.PLAYER, ShipSpec.BOSS_1},    // 120,224,255 / 120,200,255
                {ShipSpec.GRUNT, ShipSpec.BOSS_2},     // 255,108,108 / 255,120,90
        };
        for (int[] p : pairs)
            if ((p[0] == a && p[1] == b) || (p[0] == b && p[1] == a)) return true;
        return false;
    }

    static int maxChannelDelta(int a, int b) {
        return Math.max(Math.abs(ShipSpec.red(a) - ShipSpec.red(b)),
                Math.max(Math.abs(ShipSpec.green(a) - ShipSpec.green(b)),
                        Math.abs(ShipSpec.blue(a) - ShipSpec.blue(b))));
    }

    public static void main(String[] a) {
        ids();
        identity();
        radii();
        silhouettes();
        ratios();
        core();
        budget();
        colourMath();
        System.out.println(failed == 0
                ? "OK: ships " + checks + " checks passed"
                : "FAILED: ships " + failed + "/" + checks + " checks failed");
        if (failed != 0) System.exit(1);
    }

    static void ids() {
        ok(ShipSpec.COUNT == 13, "13 aircraft: player + 9 enemies + 3 bosses");
        ok(ShipSpec.ENEMY_TYPES == Wave.TYPES, "enemy id count matches Wave.TYPES");
        ok(ShipSpec.SPLITTER == Wave.SPLITTER && ShipSpec.MINER == Wave.MINER
                        && ShipSpec.SNIPER == Wave.SNIPER && ShipSpec.MOTHER == Wave.MOTHER,
                "enemy ids use the same numbering as Wave / GameView.spawnEnemy");
        ok(ShipSpec.ofEnemy(-1) == -1 && ShipSpec.ofEnemy(9) == -1, "out-of-range enemy type -> -1");
        for (int t = 0; t < ShipSpec.ENEMY_TYPES; t++)
            ok(ShipSpec.ofEnemy(t) == t && ShipSpec.isEnemy(ShipSpec.ofEnemy(t)),
                    "enemy type " + t + " maps to its art id");
        ok(ShipSpec.ofBoss(-1) == -1 && ShipSpec.ofBoss(Boss.COUNT) == -1,
                "out-of-range boss kind -> -1");
        for (int k = 0; k < Boss.COUNT; k++)
            ok(ShipSpec.isBoss(ShipSpec.ofBoss(k)) && ShipSpec.ofBoss(k) == ShipSpec.BOSS_0 + k,
                    "boss kind " + k + " maps to its art id");
        ok(ShipSpec.isPlayer(ShipSpec.PLAYER) && !ShipSpec.isEnemy(ShipSpec.PLAYER)
                && !ShipSpec.isBoss(ShipSpec.PLAYER), "the player is not an enemy or a boss");
        ok(!ShipSpec.valid(-1) && !ShipSpec.valid(ShipSpec.COUNT), "id range is [0,COUNT)");
        for (int id = 0; id < ShipSpec.COUNT; id++) {
            ok(ShipSpec.name(id).length() > 0 && !ShipSpec.name(id).equals("?"),
                    "aircraft " + id + " has a name");
            ok(ShipSpec.name(ShipSpec.ofBoss(0)).equals(Boss.NAME[0])
                            && ShipSpec.name(ShipSpec.ofBoss(1)).equals(Boss.NAME[1])
                            && ShipSpec.name(ShipSpec.ofBoss(2)).equals(Boss.NAME[2]),
                    "boss names match Boss.NAME");
        }
    }

    static void identity() {
        for (int id = 0; id < ShipSpec.COUNT; id++) {
            ok(ShipSpec.identity(id) == oldIdentity(id),
                    "identity colour of " + ShipSpec.name(id) + " is byte-identical");
            ok(ShipSpec.alphaOf(ShipSpec.identity(id)) == 255,
                    "identity colour of " + ShipSpec.name(id) + " is opaque");
            ok(ShipSpec.glow(id) == ShipSpec.identity(id),
                    "glow colour of " + ShipSpec.name(id) + " is its identity colour");
        }
        for (int k = 0; k < Boss.COUNT; k++)
            ok(ShipSpec.identity(ShipSpec.ofBoss(k)) == Boss.COLOR[k],
                    "boss " + k + " identity is Boss.COLOR[" + k + "]");

        // the six buckets check.py samples must stay mutually separable, or two of its nine
        // sample points collapse into one and the palette check stops proving anything
        int[] sampled = {ShipSpec.PLAYER, ShipSpec.GRUNT, ShipSpec.DARTER, ShipSpec.GUNSHIP,
                ShipSpec.ELITE, ShipSpec.HEAVY};
        for (int i = 0; i < sampled.length; i++)
            for (int j = i + 1; j < sampled.length; j++)
                ok(maxChannelDelta(ShipSpec.identity(sampled[i]), ShipSpec.identity(sampled[j])) > TOL,
                        "check.py buckets " + ShipSpec.name(sampled[i]) + " / "
                                + ShipSpec.name(sampled[j]) + " stay separable");

        // every other aircraft: separable from every other, unless it is a documented shared bucket
        for (int a = 0; a < ShipSpec.COUNT; a++)
            for (int b = a + 1; b < ShipSpec.COUNT; b++) {
                boolean near = maxChannelDelta(ShipSpec.identity(a), ShipSpec.identity(b)) <= TOL;
                ok(near == sharedBucket(a, b),
                        ShipSpec.name(a) + " / " + ShipSpec.name(b)
                                + (near ? " is a documented shared bucket" : " is a distinct bucket"));
            }

        // the powerbox amber is UiSpec's, not ours, and miner shares its bucket - pinned so a
        // change to either side is a deliberate decision, not a surprise in check.py
        ok(maxChannelDelta(ShipSpec.identity(ShipSpec.MINER), UiSpec.RGB_WEAPON) <= TOL,
                "miner stays in the powerbox amber bucket");
        ok(UiSpec.RGB_WEAPON == 0xFFFFBE50, "powerbox amber is unchanged (255,190,80)");
    }

    static void radii() {
        for (int id = 0; id < ShipSpec.COUNT; id++) {
            ok(ShipSpec.radiusDp(id) == oldRadius(id),
                    "radius of " + ShipSpec.name(id) + " is the number gameplay already used");
            ok(ShipSpec.radiusDp(id) > 0f, "radius of " + ShipSpec.name(id) + " is positive");
        }
        for (int k = 0; k < Boss.COUNT; k++)
            ok(ShipSpec.radiusDp(ShipSpec.ofBoss(k)) == Boss.RADIUS_DP[k],
                    "boss " + k + " radius is Boss.RADIUS_DP[" + k + "]");
    }

    static void silhouettes() {
        boolean[] seen = new boolean[ShipSpec.SIL_COUNT];
        for (int id = 0; id < ShipSpec.COUNT; id++) {
            int s = ShipSpec.silhouette(id);
            ok(s >= 0 && s < ShipSpec.SIL_COUNT,
                    "silhouette id of " + ShipSpec.name(id) + " is in range");
            ok(!seen[s], "silhouette " + ShipSpec.silhouetteName(id) + " is used only once");
            seen[s] = true;
            ok(ShipSpec.silhouetteName(id).length() > 2,
                    "silhouette of " + ShipSpec.name(id) + " is named for the log");
        }
        ok(ShipSpec.silhouette(-1) == -1 && ShipSpec.silhouette(ShipSpec.COUNT) == -1,
                "out-of-range id has no silhouette");
        int n = 0;
        for (boolean b : seen) if (b) n++;
        ok(n == ShipSpec.COUNT, "every silhouette id is used exactly once");
    }

    static void ratios() {
        for (int id = 0; id < ShipSpec.COUNT; id++) {
            String n = ShipSpec.name(id);
            ok(ShipSpec.wingSpan(id) > 0.4f && ShipSpec.wingSpan(id) <= 1.25f,
                    n + " wing span is inside the unit box (plus pod overhang)");
            ok(ShipSpec.cockpitR(id) > 0f && ShipSpec.cockpitR(id) <= 0.4f,
                    n + " cockpit radius is in (0,0.4]");
            ok(ShipSpec.flameLen(id) > 0f && ShipSpec.flameLen(id) <= 2f,
                    n + " flame length is in (0,2]");
            ok(ShipSpec.coreR(id) >= 0.15f && ShipSpec.coreR(id) <= 0.5f,
                    n + " core radius fraction is in [0.15,0.5]");
            ok(ShipSpec.engines(id) >= 1 && ShipSpec.engines(id) <= 4,
                    n + " has 1..4 engine pods");
            ok(ShipSpec.layers(id) >= 3 && ShipSpec.layers(id) <= 9,
                    n + " layer budget is 3..9");
            ok(ShipSpec.alphaOf(ShipSpec.hullTop(id)) == 255
                            && ShipSpec.alphaOf(ShipSpec.hullBottom(id)) == 255
                            && ShipSpec.alphaOf(ShipSpec.accent(id)) == 255
                            && ShipSpec.alphaOf(ShipSpec.cockpit(id)) == 255
                            && ShipSpec.alphaOf(ShipSpec.trim(id)) == 255,
                    n + " palette is fully opaque");
            ok(luma(ShipSpec.hullTop(id)) > luma(ShipSpec.hullBottom(id)),
                    n + " hull gradient runs light to dark");
            ok(luma(ShipSpec.trim(id)) > luma(ShipSpec.identity(id)),
                    n + " rim light is lighter than the hull identity");
            ok(luma(ShipSpec.accent(id)) < luma(ShipSpec.identity(id)),
                    n + " secondary plating is darker than the hull identity");
            ok(luma(ShipSpec.cockpit(id)) < luma(ShipSpec.accent(id)),
                    n + " cockpit glass is the darkest part");
        }
        // the budget has to track size, or the small ships pay for the big ones
        for (int a = 0; a < ShipSpec.ENEMY_TYPES; a++)
            for (int b = 0; b < ShipSpec.ENEMY_TYPES; b++)
                if (ShipSpec.radiusDp(a) < ShipSpec.radiusDp(b))
                    ok(ShipSpec.layers(a) <= ShipSpec.layers(b),
                            ShipSpec.name(a) + " (smaller) does not out-draw " + ShipSpec.name(b));
    }

    static void core() {
        for (int id = 0; id < ShipSpec.COUNT; id++) {
            ok(ShipSpec.coreRadiusDp(id) >= ShipSpec.CORE_MIN_DP,
                    ShipSpec.name(id) + " core lamp is >= " + ShipSpec.CORE_MIN_DP
                            + "dp so check.py's 3px grid hits it (got "
                            + ShipSpec.coreRadiusDp(id) + "dp)");
        }
        // the smallest aircraft on screen is the darter at dp(13): it sets the floor for everyone
        ok(ShipSpec.coreRadiusDp(ShipSpec.DARTER) < ShipSpec.CORE_MIN_DP * 1.6f,
                "the smallest core is not so big it reads as a blob");
    }

    static void budget() {
        ok(ShipSpec.frameOps(0) <= ShipSpec.FRAME_OP_LIMIT
                        && ShipSpec.frameOps(1) <= ShipSpec.FRAME_OP_LIMIT
                        && ShipSpec.frameOps(2) <= ShipSpec.FRAME_OP_LIMIT,
                "worst-case frame is within the op budget (diff2 = " + ShipSpec.frameOps(2)
                        + " of " + ShipSpec.FRAME_OP_LIMIT + ")");
        ok(ShipSpec.frameOps(0) < ShipSpec.frameOps(2), "the op budget grows with the enemy cap");
        ok(ShipSpec.frameOps(2) == Tuning.ENEMY_CAP[2] * 8 + ShipSpec.layers(ShipSpec.PLAYER)
                        + ShipSpec.layers(ShipSpec.BOSS_0),
                "the worst case is a full field of the richest enemy + player + boss");
    }

    static void colourMath() {
        ok(ShipSpec.mix(0xFF000000, 0xFFFFFFFF, 0f) == 0xFF000000, "mix at t=0 is a");
        ok(ShipSpec.mix(0xFF000000, 0xFFFFFFFF, 1f) == 0xFFFFFFFF, "mix at t=1 is b");
        ok(ShipSpec.mix(0xFF000000, 0xFFFFFFFF, 0.5f) == 0xFF7F7F7F,
                "mix at t=0.5 is mid grey (truncating, so 127 not 128)");
        ok(ShipSpec.mix(0xFF000000, 0xFFFFFFFF, 5f) == 0xFFFFFFFF, "mix clamps t above 1");
        ok(ShipSpec.mix(0xFF000000, 0xFFFFFFFF, -5f) == 0xFF000000, "mix clamps t below 0");
        ok(ShipSpec.alpha(0xFFFF6C6C, 128) == 0x80FF6C6C, "alpha() replaces only the alpha channel");
        ok(ShipSpec.alpha(0xFFFF6C6C, -1) == 0x00FF6C6C, "alpha() clamps below 0");
        ok(ShipSpec.alpha(0xFFFF6C6C, 999) == 0xFFFF6C6C, "alpha() clamps above 255");
        ok(ShipSpec.red(0xFF123456) == 0x12 && ShipSpec.green(0xFF123456) == 0x34
                        && ShipSpec.blue(0xFF123456) == 0x56,
                "channel extractors read ARGB");
        ok(ShipSpec.shade(0xFFFF6C6C, 1f) == 0xFF000000, "shade at t=1 is black");
        ok(ShipSpec.tint(0xFFFF6C6C, 1f) == 0xFFFFFFFF, "tint at t=1 is white");
        for (int id = 0; id < ShipSpec.COUNT; id++) {
            int top = ShipSpec.hullTop(id), bot = ShipSpec.hullBottom(id);
            ok(top == ShipSpec.mix(ShipSpec.identity(id), 0xFFFFFFFF, 0.42f)
                            && bot == ShipSpec.mix(ShipSpec.identity(id), 0xFF070C18, 0.70f),
                    ShipSpec.name(id) + " hull stops derive from its identity colour");
        }
    }

    static int luma(int c) {
        return (ShipSpec.red(c) * 77 + ShipSpec.green(c) * 150 + ShipSpec.blue(c) * 29) >> 8;
    }
}
