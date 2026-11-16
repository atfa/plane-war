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
        grazeAndFever();
        bossFight();
        weapons();
        System.out.println(failed == 0
                ? "OK: " + checks + " checks passed"
                : "FAILED: " + failed + "/" + checks + " checks failed");
        if (failed > 0) System.exit(1);
    }

    // ---------------------------------------------------------------- Weapon

    static void weapons() {
        System.out.println("[Weapon]");
        ok(Weapon.pattern(1).length == 3, "LV1 fires one shot");
        ok(Weapon.shots(2) == 2 && Weapon.shots(3) == 3 && Weapon.shots(4) == 4,
                "LV2/3/4 fire 2/3/4 shots");
        ok(Weapon.shots(5) == 6, "LV5 opens up to six shots");
        ok(Weapon.shots(0) == 6 && Weapon.shots(99) == 6, "out of range levels clamp to the LV5 pattern");
        for (int lv = 1; lv <= Weapon.MAX_LEVEL; lv++) {
            float[] p = Weapon.pattern(lv);
            ok(p.length % 3 == 0, "pattern is flat triples of offset/angle/damage");
            for (int i = 0; i < p.length; i += 3) {
                ok(p[i] >= -20f && p[i] <= 20f, "offset stays inside the hull (dp)");
                ok(p[i + 1] >= -0.5f && p[i + 1] <= 0.5f, "slant stays narrow enough to be aimed");
                ok(p[i + 2] > 0f && p[i + 2] <= 1f, "damage scale never exceeds the base shot");
            }
        }
        ok(Weapon.spreadScale(4) == 1f, "LV4 keeps the base spread");
        ok(Weapon.spreadScale(5) > 1f, "LV5 widens the barrage");
        ok(Weapon.missiles(1) == 0 && Weapon.missiles(2) == 0, "no missiles below LV3");
        ok(Weapon.missiles(3) == 2 && Weapon.missiles(5) == 3, "missiles grow with the gun");
        ok(Weapon.missiles(0) == 0 && Weapon.missiles(99) == 3, "missile table clamps both ends");
        ok(Weapon.LASER_SEC == 8f && Weapon.HOMING_SEC == 12f, "power-up durations are the design values");
        ok(Weapon.BEAM_TICK > 0f, "the beam has a settlement period, it is not per-frame");
        ok(Weapon.beamDue(0f, 0.016f) && !Weapon.beamDue(0.1f, 0.016f),
                "the beam settles on a period, not every frame - a per-frame beam deletes an 80 hp boss in a second");
        ok(Weapon.beamCooldown() == Weapon.BEAM_TICK, "the cadence is fixed, so DPS cannot drift");
        // 60 fps for 8s: a per-frame beam would settle 480 times, a periodic beam 53
        int ticks = 0;
        float cd = 0f;
        for (int f = 0; f < 480; f++) {
            if (Weapon.beamDue(cd, 1f / 60f)) { ticks++; cd = Weapon.beamCooldown(); }
            cd -= 1f / 60f;
        }
        ok(ticks > 40 && ticks < 70, "one beam over 8s settles about 53 times, not 480");

        // target selection: nearest, boss legal, off-screen ignored, never "none" while something is in play
        float[] xs = {100f, 300f, 200f};
        float[] ys = {400f, 400f, 100f};
        ok(Weapon.pickTarget(xs, ys, 3, false, 0f, 0f, 210f, 420f, -20f) == 1,
                "the nearest enemy wins (300,400 is 90px away, 100,400 is 110px)");
        ok(Weapon.pickTarget(xs, ys, 3, true, 205f, 415f, 210f, 420f, -20f) == 3,
                "the boss is a legal target - it does not live in the enemy list, so forgetting it makes every new weapon useless on a boss wave");
        ok(Weapon.pickTarget(xs, ys, 3, false, 0f, 0f, 210f, 420f, 200f) == 1,
                "enemies above the top edge (still spawning) are ignored, so the lock falls back to the closest one in play");
        ok(Weapon.pickTarget(new float[0], new float[0], 0, false, 0f, 0f, 0f, 0f, -20f) == -1,
                "nothing on screen means no target");
        ok(Weapon.pickTarget(new float[0], new float[0], 0, true, 50f, 50f, 0f, 0f, -20f) == 0,
                "a lone boss is still a target, reported as index n");
        ok(Weapon.pickTarget(new float[]{1f}, new float[]{-999f}, 1, false, 0f, 0f, 0f, 0f, -20f) == -1,
                "an off-screen enemy is never locked, so a missile does not chase a spawn off the top");
        ok(Weapon.MAX_PLAYER_BULLETS >= 60,
                "player bullets are capped, so LV5 + missiles + laser cannot stack into a frame-time spike");
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

    /** graze band geometry and the fever threshold */
    static void grazeAndFever() {
        float hit = 30f;          // bullet radius + ship radius, px
        float band = 80f;        // graze band width, px
        ok(!Tuning.grazes(0f, 0f, hit, band), "dead centre is a hit, not a graze");
        ok(Tuning.grazes(hit, 0f, hit, band),
                "exactly on the hit box edge grazes - the hit test is strict (<), so it is not a hit");
        ok(Tuning.grazes(hit + 1f, 0f, hit, band), "just outside the hit box grazes");
        ok(Tuning.grazes(0f, hit + band - 0.5f, hit, band), "just inside the outer edge grazes");
        ok(!Tuning.grazes(hit + band, 0f, hit, band), "on the outer edge is out of the band");
        ok(!Tuning.grazes(hit + band + 1f, 0f, hit, band), "outside the band is no graze");
        // the band is a ring: for a fixed distance, grazing must not depend on the direction
        float d = hit + band * 0.5f;
        for (int a = 0; a < 360; a += 15) {
            double rad = Math.toRadians(a);
            ok(Tuning.grazes((float) (d * Math.cos(rad)), (float) (d * Math.sin(rad)), hit, band),
                    "graze band is round (" + a + " deg)");
        }
        // a wider band must never shrink the set of grazing positions
        for (float x = 0f; x < hit + band + 40f; x += 4f) {
            boolean narrow = Tuning.grazes(x, 0f, hit, band);
            boolean wide = Tuning.grazes(x, 0f, hit, band * 2f);
            ok(!narrow || wide, "a wider band grazes at least as much (x=" + x + ")");
        }
        ok(Tuning.GRAZE_RADIUS_DP > 0 && Tuning.GRAZE_RADIUS_DP <= 80f, "graze band width is sane");
        ok(Tuning.GRAZE_ENERGY > 0f, "a graze charges energy");
        ok(Tuning.GRAZE_COOLDOWN > 0f,
                "one bullet cannot be farmed every frame (" + Tuning.GRAZE_COOLDOWN + "s)");
        // with the cooldown a slow bullet hugging the ship cannot fill the bar in one pass:
        // ENERGY_MAX / GRAZE_ENERGY grazes are needed, and the cooldown caps the rate
        float grazesNeeded = Tuning.ENERGY_MAX / Tuning.GRAZE_ENERGY;
        ok(grazesNeeded * Tuning.GRAZE_COOLDOWN > 5f,
                "filling the energy bar by grazing alone takes > 5s of hugging a bullet");

        ok(!Tuning.fever(Tuning.FEVER_COMBO - 1), "one below the combo threshold is not fever");
        ok(Tuning.fever(Tuning.FEVER_COMBO), "the threshold itself is fever");
        ok(Tuning.fever(Tuning.FEVER_COMBO + 50), "deep combos stay in fever");
        ok(!Tuning.fever(0), "no combo is not fever");
        ok(Tuning.FEVER_SCORE_MULT > 1f, "fever pays more");
        ok(Tuning.FEVER_FIRE_RATE > 0f && Tuning.FEVER_FIRE_RATE < 1f,
                "fever fires faster (gap multiplier " + Tuning.FEVER_FIRE_RATE + ")");
    }

    /** the boss state machine: phases, caps, liveness, death */
    static void bossFight() {
        ok(Boss.COUNT == 3 && Boss.NAME.length == Boss.COUNT && Boss.SUB.length == Boss.COUNT
                && Boss.COLOR.length == Boss.COUNT && Boss.RADIUS_DP.length == Boss.COUNT
                && Boss.SCORE.length == Boss.COUNT, "boss tables all match COUNT");
        ok(Boss.pick(0) == 0 && Boss.pick(1) == 1 && Boss.pick(2) == 2,
                "the three bosses rotate in order");
        ok(Boss.pick(3) == 0 && Boss.pick(-1) == Boss.COUNT - 1, "pick() wraps both ways");
        java.util.HashSet<Integer> seen = new java.util.HashSet<Integer>();
        for (int i = 0; i < 30; i++) seen.add(Boss.pick(i));
        ok(seen.size() == Boss.COUNT, "30 fights cover every boss");

        // phase thresholds: descending, inside (0,1], and the edges are exact
        for (int i = 1; i < Boss.PHASES; i++)
            ok(Boss.PHASE_AT[i] < Boss.PHASE_AT[i - 1], "phase thresholds descend at " + i);
        ok(Boss.phaseFor(1f) == 0, "full hp is phase 0");
        ok(Boss.phaseFor(0.66f) == 1 && Boss.phaseFor(0.661f) == 0, "phase 1 starts at exactly 66%");
        ok(Boss.phaseFor(0.33f) == 2 && Boss.phaseFor(0.331f) == 1, "phase 2 starts at exactly 33%");
        ok(Boss.phaseFor(0f) == 2 && Boss.phaseFor(-1f) == 2, "empty bar is the last phase");

        // hp scales up with difficulty and with the number of bosses already killed
        for (int k = 0; k < Boss.COUNT; k++) {
            ok(Boss.hpFor(k, 1) > Boss.hpFor(k, 0) && Boss.hpFor(k, 2) > Boss.hpFor(k, 1),
                    "boss " + k + " hp grows with difficulty");
            ok(Boss.spawn(k, 0, 1).maxHp() > Boss.spawn(k, 0, 0).maxHp(),
                    "boss " + k + " hp grows after each kill");
            ok(Boss.spawn(k, 0, 0).kind() == Boss.pick(k), "spawn uses the rotated kind");
            ok(Boss.spawn(k, 0, 0).hpFrac() == 1f, "a fresh boss is at full hp");
        }
        ok(Boss.spawn(99, 7, -3).kind() >= 0 && Boss.spawn(99, 7, -3).maxHp() > 0f,
                "garbage arguments stay in the table");

        // liveness: no phase may stall for longer than maxGapSec, and later phases are busier
        ok(Boss.maxGapSec(0, 1) > Boss.maxGapSec(1, 1)
                && Boss.maxGapSec(1, 1) > Boss.maxGapSec(2, 1), "gap shrinks as phases advance");
        ok(Boss.maxGapSec(0, 2) < Boss.maxGapSec(0, 0), "higher difficulty fires faster");
        for (int k = 0; k < Boss.COUNT; k++) {
            for (int d = 0; d <= 2; d++) {
                Boss b = Boss.spawn(k, d, 0);
                float gap = Boss.maxGapSec(b.phase(), d);
                float since = 0f;
                float worst = 0f;
                int shots = 0;
                for (int f = 0; f < 3600; f++) {          // 60 s at 60 fps
                    int req = b.act(1f / 60f, 20);
                    boolean fired = (req & (Boss.ACT_RING | Boss.ACT_AIM | Boss.ACT_FAN)) != 0;
                    if (fired) { shots++; since = 0f; } else { since += 1f / 60f; }
                    if (since > worst) worst = since;
                    if (f == 60) b.hurt(b.hp() * 0.5f);   // drop into phase 1
                    if (f == 1200) b.hurt(b.hp() * 0.6f); // and later into phase 2
                    if (b.isDead()) break;
                }
                ok(shots > 60, "boss " + k + " diff " + d + " fires a lot over 60s (" + shots + ")");
                ok(worst <= gap + 0.05f, "boss " + k + " diff " + d + " never stalls: worst gap "
                        + worst + " <= " + gap);
            }
        }

        // summoning only happens in the last phase and never exceeds the free slots
        for (int k = 0; k < Boss.COUNT; k++) {
            Boss b = Boss.spawn(k, 0, 0);
            int req = 0;
            for (int f = 0; f < 600 && b.phase() < 2; f++) {
                req = b.act(1f / 30f, 30);
                ok((req & Boss.ACT_SUMMON) == 0, "no minions before the last phase");
                if ((req & Boss.ACT_SUMMON) != 0) break;
                if (f == 100) b.hurt(b.hp() * 0.8f);
            }
            b = Boss.spawn(k, 0, 0);
            b.hurt(b.hp() * 0.8f);                       // force the last phase
            int got = 0;
            for (int f = 0; f < 600 && got == 0; f++) {
                int r = b.act(1f / 30f, 2);
                if ((r & Boss.ACT_SUMMON) != 0) got = b.summonCount();
            }
            ok(got > 0 && got <= 2, "boss " + k + " respects the free-slot cap (got " + got + ")");
            b = Boss.spawn(k, 0, 0);
            b.hurt(b.hp() * 0.8f);
            int n = 0;
            for (int f = 0; f < 600 && n == 0; f++)
                if ((b.act(1f / 30f, 1) & Boss.ACT_SUMMON) != 0) n = b.summonCount();
            ok(n == 0, "boss " + k + " spawns no half formation when only one slot is free");
        }

        // death: hp clamps at zero, hpFrac stays in [0,1], hurt is a no-op afterwards,
        // and ACT_DIE is reported exactly once (a double report would double the payout)
        Boss b = Boss.spawn(0, 0, 0);
        float step = b.maxHp() / 10f;
        for (int i = 0; i < 9; i++) {
            b.hurt(step);
            ok(b.hpFrac() >= 0f && b.hpFrac() <= 1f, "hpFrac stays in [0,1] while hurt");
            ok(b.hp() >= 0f, "hp never goes negative");
        }
        ok(!b.isDead(), "nine of ten steps is not dead yet");
        ok(b.hurt(b.maxHp()), "the killing blow reports death");
        ok(b.hp() == 0f && b.hpFrac() == 0f, "a dead boss sits at exactly zero hp");
        ok(!b.hurt(10f), "a dead boss cannot be hurt again");
        int die = 0;
        for (int f = 0; f < 120; f++)
            if ((b.act(1f / 60f, 20) & Boss.ACT_DIE) != 0) die++;
        ok(die == 1, "ACT_DIE is reported exactly once (got " + die + ")");
        ok(b.act(1f / 60f, 20) == 0, "a dead boss requests nothing after the death report");

        // the phase never walks back: a healed boss keeps the phase it earned
        Boss c = Boss.spawn(1, 0, 0);
        c.hurt(c.maxHp() * 0.7f);
        int p = c.phase();
        ok(p == 2, "70% damage puts the boss in the last phase");
        c.hurt(-5f);
        ok(c.phase() == p, "negative damage does not walk the phase back");
        ok(c.hpFrac() <= 1f, "negative damage cannot push hp above max");
    }
}
