package dev.termux.planewar;

/**
 * JVM assertions for the pure-logic classes (Tuning, Super, ...).
 * Run with ./test_jvm.sh - no device, no Android.
 */
public class TestLogic {

    static int checks = 0, failed = 0;

    static void ok(boolean c, String what) {
        checks++;
        if (!c) {
            failed++;
            System.out.println("  FAIL: " + what);
        }
    }

    static void near(float a, float b, float eps, String what) {
        ok(Math.abs(a - b) <= eps, what + " (" + a + " vs " + b + ")");
    }

    public static void main(String[] args) {
        tuning();
        superThemes();
        System.out.println(failed == 0
                ? "OK: " + checks + " checks passed"
                : "FAILED: " + failed + "/" + checks + " checks failed");
        if (failed > 0) System.exit(1);
    }

    // ---------------------------------------------------------------- Tuning

    static void tuning() {
        System.out.println("[Tuning]");
        ok(Tuning.ENERGY_MAX > 0, "energy max is positive");
        ok(Tuning.BOMB_START >= 1 && Tuning.BOMB_START <= Tuning.BOMB_MAX,
                "bomb start is inside the stock cap");
        ok(Tuning.KILL_ENERGY.length == 5, "one energy value per enemy type");
        ok(Tuning.BOMB_DMG.length == 3 && Tuning.SUPER_DMG.length == 3
                && Tuning.ENERGY_PER_SEC.length == 3, "difficulty-scaled tables have 3 entries");

        // harder difficulty must not charge faster, and must hit harder
        ok(Tuning.energyPerSec(0) >= Tuning.energyPerSec(2), "easy charges at least as fast as hard");
        ok(Tuning.superDamage(0) < Tuning.superDamage(2), "hard supers hit harder");
        ok(Tuning.bombDamage(0) < Tuning.bombDamage(2), "hard bombs hit harder");

        // out of range difficulties clamp instead of crashing
        ok(Tuning.energyPerSec(-3) == Tuning.energyPerSec(0), "negative difficulty clamps low");
        ok(Tuning.superDamage(9) == Tuning.superDamage(2), "big difficulty clamps high");

        // kills charge the bar: a heavy is worth far more than a grunt, and the bar is reachable
        ok(Tuning.killEnergy(0) < Tuning.killEnergy(4), "heavier enemies charge more");
        ok(Tuning.killEnergy(-1) == Tuning.killEnergy(0), "unknown type clamps to grunt");
        ok(Tuning.killEnergy(99) == Tuning.killEnergy(4), "unknown type clamps to heavy");
        int need = 0;
        while (need < 200 && sumKillEnergy(need) < Tuning.ENERGY_MAX) need++;
        ok(need <= 40, "a super is reachable in " + need + " kills (<= 40)");

        ok(Tuning.FEVER_COMBO > 5, "fever needs a real streak");
        ok(Tuning.FEVER_SCORE_MULT > 1f, "fever multiplies the score");
        ok(Tuning.FEVER_FIRE_RATE < 1f, "fever shortens the fire gap");
        ok(Tuning.GRAZE_RADIUS_DP > 20f && Tuning.GRAZE_RADIUS_DP < 90f,
                "graze band is a sane radius");
        ok(Tuning.GRAZE_ENERGY > 0f, "graze charges energy");
    }

    static float sumKillEnergy(int kills) {
        float s = 0;
        for (int i = 0; i < kills; i++) s += Tuning.killEnergy(i % 5);
        return s;
    }

    // ---------------------------------------------------------------- Super

    static void superThemes() {
        System.out.println("[Super]");
        ok(Super.COUNT == 5, "five super blast themes");
        ok(Super.MAX_IMPACTS >= 1, "MAX_IMPACTS is usable as an array size");
        int maxHits = 0;
        for (int t = 0; t < Super.COUNT; t++) maxHits = Math.max(maxHits, Super.impacts(t));
        ok(Super.MAX_IMPACTS == maxHits, "MAX_IMPACTS is the largest impact count of any theme");
        ok(Super.NAME.length == Super.COUNT && Super.SUB.length == Super.COUNT
                && Super.COLOR.length == Super.COUNT, "name/sub/colour tables match COUNT");
        for (int t = 0; t < Super.COUNT; t++) {
            ok(Super.name(t).length() > 0, "theme " + t + " has a name");
            ok(Super.sub(t).length() > 0, "theme " + t + " has a subtitle");
            ok(Super.damage(t, 0) > 0, "theme " + t + " does damage");
            ok(Super.clearsBullets(t), "theme " + t + " clears enemy bullets");
            ok(Super.impacts(t) >= 0, "theme " + t + " impact count is sane");
            if (Super.impacts(t) > 0)
                ok(Super.radiusDp(t) > 0, "theme " + t + " point impacts have a radius");
            // the view sizes its effect arrays with MAX_IMPACTS and indexes them with
            // 0..impacts(theme)-1; if that ever exceeds MAX_IMPACTS the game crashes on the
            // field (this is exactly the 万雷齐鸣 ArrayIndexOutOfBoundsException we shipped once)
            ok(Super.impacts(t) <= Super.MAX_IMPACTS,
                    "theme " + t + " impacts (" + Super.impacts(t) + ") fit the effect arrays ("
                            + Super.MAX_IMPACTS + ")");
            for (int i = 0; i < Super.impacts(t); i++)
                ok(i < Super.MAX_IMPACTS, "theme " + t + " impact index " + i + " is in bounds");
        }
        ok(Super.name(-5).equals(Super.name(0)), "negative theme clamps to the first");
        ok(Super.name(99).equals(Super.name(Super.COUNT - 1)), "big theme clamps to the last");

        // the difficulty scale must be applied
        ok(Super.damage(Super.METEOR, 2) > Super.damage(Super.METEOR, 0),
                "theme damage scales with difficulty");

        // exactly one theme heals, one freezes, one sweeps
        int heals = 0, freezes = 0, sweeps = 0;
        for (int t = 0; t < Super.COUNT; t++) {
            if (Super.heals(t)) heals++;
            if (Super.freezes(t)) freezes++;
            if (Super.sweeps(t)) sweeps++;
        }
        ok(heals == 1 && freezes == 1 && sweeps == 1, "one heal / one freeze / one sweep theme");
        ok(Super.freezeSeconds() > 1f && Super.freezeSeconds() < 10f, "freeze duration is sane");

        // pick() stays inside the table for many seeds
        java.util.Random r = new java.util.Random(7);
        boolean inRange = true;
        for (int i = 0; i < 500; i++) {
            int t = Super.pick(r);
            if (t < 0 || t >= Super.COUNT) inRange = false;
        }
        ok(inRange, "pick() stays inside the theme table");
        ok(Super.pick(null) == 0, "pick(null) falls back to the first theme");
    }
}
