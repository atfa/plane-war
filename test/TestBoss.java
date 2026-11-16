package dev.termux.planewar;

/**
 * JVM assertions for the boss state machine (no Android, no device).
 *
 * <p>Covers what a device run cannot see cheaply: the exact phase boundaries, that a phase never
 * replays, that a live boss keeps firing (a boss that stalls is the worst boss bug on a phone),
 * that the summon request respects the caller's free enemy slots, and that a dead boss stops
 * asking for anything.
 */
public class TestBoss {

    static int checks, failed;

    static void ok(boolean c, String what) {
        checks++;
        if (!c) { failed++; System.out.println("  FAIL: " + what); }
    }

    public static void main(String[] a) {
        roster();
        phaseThresholds();
        spawnScaling();
        hurtRules();
        cadence();
        fullFight();
        System.out.println(failed == 0
                ? "OK: boss " + checks + " checks passed"
                : "FAILED: boss " + failed + "/" + checks + " checks failed");
        if (failed != 0) System.exit(1);
    }

    static void roster() {
        ok(Boss.COUNT == 3, "three bosses rotate");
        ok(Boss.NAME.length == Boss.COUNT && Boss.SUB.length == Boss.COUNT,
                "every boss has a name and a banner subtitle");
        boolean unique = true;
        for (int i = 0; i < Boss.COUNT; i++) {
            ok(Boss.NAME[i] != null && Boss.NAME[i].length() > 0, "boss " + i + " is named");
            ok(Boss.SUB[i] != null && Boss.SUB[i].length() > 0, "boss " + i + " has a subtitle");
            for (int j = i + 1; j < Boss.COUNT; j++)
                if (Boss.NAME[i].equals(Boss.NAME[j])) unique = false;
        }
        ok(unique, "boss names are distinct");
        for (int k = 0; k < Boss.COUNT; k++) {
            ok(Boss.spawn(k, 1, 0).score() > 0, "boss " + k + " pays a real kill score");
            ok(Boss.spawn(k, 1, 0).maxHp() > 0, "boss " + k + " has hit points");
        }
        // the request bits must stay single bits, so the view can test them independently
        int[] bits = {Boss.ACT_RING, Boss.ACT_AIM, Boss.ACT_FAN, Boss.ACT_SUMMON};
        for (int i = 0; i < bits.length; i++) {
            ok(Integer.bitCount(bits[i]) == 1, "action bit " + i + " is a single bit");
            for (int j = i + 1; j < bits.length; j++)
                ok((bits[i] & bits[j]) == 0, "action bits " + i + "/" + j + " do not overlap");
        }
    }

    static void phaseThresholds() {
        ok(Boss.PHASES == 3, "three phases");
        ok(Boss.PHASE_AT.length == Boss.PHASES, "one threshold per phase");
        for (int i = 1; i < Boss.PHASES; i++)
            ok(Boss.PHASE_AT[i] < Boss.PHASE_AT[i - 1] && Boss.PHASE_AT[i] > 0f,
                    "threshold " + i + " is below the previous one and above zero");
        ok(Boss.PHASE_AT[0] == 1.00f, "phase 0 starts at full health");

        ok(Boss.phaseFor(1.00f) == 0, "full health is phase 0");
        ok(Boss.phaseFor(0.67f) == 0, "just above the mid threshold is still phase 0");
        ok(Boss.phaseFor(0.66f) == 1, "the mid threshold itself flips to phase 1");
        ok(Boss.phaseFor(0.34f) == 1, "just above the low threshold is phase 1");
        ok(Boss.phaseFor(0.33f) == 2, "the low threshold flips to phase 2");
        ok(Boss.phaseFor(0.01f) == 2, "near death is phase 2");
        ok(Boss.phaseFor(0f) == 2, "zero health is phase 2");
        ok(Boss.phaseFor(-1f) == 2, "a negative fraction cannot escape the last phase");
        ok(Boss.phaseFor(2f) == 0, "an over-full fraction clamps to phase 0");
        // walking health down must never take the fight backwards
        int prev = -1;
        for (float f = 1.0f; f >= 0f; f -= 0.005f) {
            int p = Boss.phaseFor(f);
            ok(p >= 0 && p < Boss.PHASES, "phase index in range at hp=" + f);
            ok(p >= prev, "phase never goes backwards (hp=" + f + ")");
            prev = p;
        }
    }

    static void spawnScaling() {
        float easy = Boss.spawn(0, 0, 1).maxHp();
        float norm = Boss.spawn(0, 1, 1).maxHp();
        float hard = Boss.spawn(0, 2, 1).maxHp();
        ok(easy < norm && norm < hard, "hp rises with difficulty");
        ok(Boss.spawn(0, 0, 0).maxHp() < Boss.spawn(0, 0, 3).maxHp(),
                "later bosses in the same run are tougher");
        ok(Boss.spawn(0, -5, 0).maxHp() > 0, "a negative difficulty clamps");
        ok(Boss.spawn(0, 9, 0).maxHp() > 0, "an out-of-range difficulty clamps");
        // the roster rotates: fight index must wrap both ways
        for (int i = -4; i < 8; i++) {
            Boss b = Boss.spawn(i, 1, 0);
            ok(b.kind() >= 0 && b.kind() < Boss.COUNT, "kind wraps into range (index " + i + ")");
        }
        ok(Boss.spawn(0, 1, 0).hpFrac() == 1f, "a fresh boss is at full health");
        ok(Boss.spawn(3, 1, 0).kind() == Boss.spawn(0, 1, 0).kind(), "kind 3 is kind 0 again");
    }

    static void hurtRules() {
        Boss b = Boss.spawn(0, 1, 0);
        float hp0 = b.hp();
        ok(!b.hurt(0f) && b.hp() == hp0, "zero damage does nothing");
        ok(!b.hurt(-10f) && b.hp() == hp0, "negative damage does nothing");
        ok(!b.hurt(hp0 * 0.5f) && !b.isDead(), "half health is not dead");
        ok(b.hurt(hp0), "the killing blow reports the kill");
        ok(b.isDead() && b.hp() == 0f, "hp clamps at zero, never negative (a negative bar width crashes the canvas)");
        ok(b.hpFrac() >= 0f && b.hpFrac() <= 1f, "hp fraction stays inside [0,1]");
        float after = b.hp();
        ok(!b.hurt(50f) && b.hp() == after, "a dead boss ignores further damage");
        ok(b.act(0.1f) == 0, "a dead boss requests nothing");
        for (int i = 0; i < 200; i++) ok(b.act(0.016f) == 0, "a dead boss stays silent");
    }

    /** a live boss must keep firing: no long silent gap in any phase */
    static void cadence() {
        for (int k = 0; k < Boss.COUNT; k++) {
            for (int d = 0; d < 3; d++) {
                Boss b = Boss.spawn(k, d, 0);
                float dt = 1f / 60f, gap = 0f, worst = 0f;
                int fires = 0;
                for (int f = 0; f < 60 * 60; f++) {          // 60 s in phase 0
                    int req = b.act(dt);
                    gap += dt;
                    if ((req & Boss.ACT_RING) != 0) {
                        fires++;
                        if (fires > 1) worst = Math.max(worst, gap);
                        gap = 0f;
                    }
                    ok(req >= 0, "act() never returns a negative mask");
                }
                ok(fires >= 20, "boss " + k + " diff " + d + " fires the ring often enough (" + fires + " in 60s)");
                ok(worst < 4f, "boss " + k + " diff " + d + " never goes silent for >4s (worst " + worst + "s)");
            }
        }
    }

    /** drive a boss through all three phases with damage and check what it asks for */
    static void fullFight() {
        for (int k = 0; k < Boss.COUNT; k++) {
            Boss b = Boss.spawn(k, 1, 0);
            float dt = 1f / 60f;
            boolean[] seen = new boolean[Boss.PHASES];
            boolean summonOutsideLast = false, sawSummon = false, sawFan = false, sawAim = false;
            int room = 4;
            for (int f = 0; f < 60 * 120 && !b.isDead(); f++) {
                int req = b.act(dt);
                int p = b.phase();
                seen[p] = true;
                if (p != Boss.PHASES - 1 && (req & Boss.ACT_SUMMON) != 0) summonOutsideLast = true;
                if ((req & Boss.ACT_SUMMON) != 0) {
                    sawSummon = true;
                    int n = b.summonCount(room);
                    ok(n <= room, "summon never exceeds the free slots (room=" + room + " -> " + n + ")");
                    ok(n == 0 || n >= 2, "a summon wave is never a single straggler");
                }
                if ((req & Boss.ACT_FAN) != 0) sawFan = true;
                if ((req & Boss.ACT_AIM) != 0) sawAim = true;
                ok(b.hpFrac() >= 0f && b.hpFrac() <= 1f, "hp fraction stays in [0,1] mid-fight");
                if (f % 30 == 0) b.hurt(b.maxHp() / 120f);      // chip away, one hit per half second
            }
            ok(b.isDead(), "boss " + k + " dies when the damage keeps coming");
            for (int p = 0; p < Boss.PHASES; p++)
                ok(seen[p], "boss " + k + " plays phase " + p);
            ok(!summonOutsideLast, "boss " + k + " only summons in the last phase");
            ok(sawSummon, "boss " + k + " summons minions in its last phase");
            ok(sawFan, "boss " + k + " uses the fan sweep");
            ok(sawAim, "boss " + k + " uses aimed bursts");
            ok(b.summonCount(1) == 0 && b.summonCount(0) == 0,
                    "a nearly full screen summons nothing instead of a half formation");
            ok(b.summonCount(99) >= 2, "an empty screen gets a real wave");
        }
        // escalation: the fight must get denser as the boss gets hurt
        Boss b = Boss.spawn(0, 1, 0);
        int ring0 = b.ringCount(), fan0 = b.fanCount(), aim0 = b.aimBurst();
        b.hurt(b.maxHp() * 0.5f);
        int ring1 = b.ringCount(), fan1 = b.fanCount(), aim1 = b.aimBurst();
        b.hurt(b.maxHp() * 0.9f);
        ok(b.ringCount() >= ring1 && ring1 >= ring0, "the ring grows with each phase");
        ok(b.fanCount() >= fan1 && fan1 >= fan0, "the fan widens with each phase");
        ok(b.aimBurst() >= aim1 && aim1 >= aim0, "aimed bursts get longer with each phase");
    }
}
