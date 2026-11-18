package dev.termux.planewar;

/**
 * JVM assertions for the HUD contract in UiSpec (no Android, no device).
 *
 * <p>What a device run cannot check cheaply, and what a colour refactor breaks first: that the
 * eight power-up colours are the exact bytes check.py samples from the power-up box palette, that
 * every power-up kind has a label and a distinct icon, that out-of-range kinds fall back to the
 * weapon amber/glyph/icon the way the old switch in GameView did, and that the size budget stays
 * above the floor that keeps a glyph readable after the DeX fit scale is applied.
 */
public class TestUi {

    static int checks, failed;

    static void ok(boolean c, String what) {
        checks++;
        if (!c) { failed++; System.out.println("  FAIL: " + what); }
    }

    /** The pre-refactor table, written out as literals on purpose: this is the regression pin. */
    static int oldColor(int k) {
        switch (k) {
            case 1: return 0xFF000000 | (96 << 16) | (214 << 8) | 255;
            case 2: return 0xFF000000 | (110 << 16) | (240 << 8) | 150;
            case 3: return 0xFF000000 | (255 << 16) | (112 << 8) | 112;
            case 4: return 0xFF000000 | (255 << 16) | (220 << 8) | 120;
            case 5: return 0xFF000000 | (230 << 16) | (140 << 8) | 255;
            case 6: return 0xFF000000 | (120 << 16) | (255 << 8) | 235;
            case 7: return 0xFF000000 | (255 << 16) | (150 << 8) | 60;
            default: return 0xFF000000 | (255 << 16) | (190 << 8) | 80;
        }
    }

    static String oldLabel(int k) {
        switch (k) {
            case 1: return "盾";
            case 2: return "命";
            case 3: return "爆";
            case 4: return "分";
            case 5: return "速";
            case 6: return "激光";
            case 7: return "追";
            default: return "火";
        }
    }

    public static void main(String[] a) {
        kinds();
        colours();
        labels();
        icons();
        budget();
        System.out.println(failed == 0
                ? "OK: ui " + checks + " checks passed"
                : "FAILED: ui " + failed + "/" + checks + " checks failed");
        if (failed != 0) System.exit(1);
    }

    static void kinds() {
        ok(UiSpec.PW_WEAPON == 0 && UiSpec.PW_SHIELD == 1 && UiSpec.PW_HEAL == 2
                        && UiSpec.PW_BOMB == 3 && UiSpec.PW_SCORE == 4 && UiSpec.PW_RAPID == 5
                        && UiSpec.PW_LASER == 6 && UiSpec.PW_HOMING == 7,
                "power-up kind values are unchanged");
        ok(UiSpec.PW_COUNT == 8, "eight power-up kinds");
    }

    static void colours() {
        for (int k = -3; k < 12; k++) {
            ok(UiSpec.color(k) == oldColor(k), "colour for kind " + k + " is byte-identical");
        }
        // the amber box is what check.py samples; pin it as a literal so a rename cannot drift
        ok(UiSpec.color(UiSpec.PW_WEAPON) == 0xFFFFBE50, "powerbox amber is 255,190,80");
        ok((UiSpec.color(UiSpec.PW_WEAPON) >>> 24) == 0xFF, "powerbox amber is opaque");
        boolean distinct = true;
        for (int i = 0; i < UiSpec.PW_COUNT; i++) {
            for (int j = i + 1; j < UiSpec.PW_COUNT; j++) {
                if (UiSpec.color(i) == UiSpec.color(j)) distinct = false;
            }
        }
        ok(distinct, "every power-up reads as its own colour");
        for (int k = 0; k < UiSpec.PW_COUNT; k++) {
            int c = UiSpec.color(k);
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            ok(r > 80 && g > 80 && b > 50, "power colour " + k + " is bright enough for a dark HUD");
        }
    }

    static void labels() {
        for (int k = -3; k < 12; k++) {
            ok(UiSpec.label(k).equals(oldLabel(k)), "label for kind " + k + " is unchanged");
        }
        for (int k = 0; k < UiSpec.PW_COUNT; k++) {
            ok(UiSpec.label(k) != null && UiSpec.label(k).length() > 0, "kind " + k + " is labelled");
        }
        ok(UiSpec.label(UiSpec.PW_LASER).length() == 2, "laser keeps its two-glyph label");
    }

    static void icons() {
        for (int k = 0; k < UiSpec.PW_COUNT; k++) {
            int ic = UiSpec.iconOf(k);
            ok(UiSpec.hasIcon(ic), "power kind " + k + " maps to a real icon");
        }
        ok(UiSpec.iconOf(-1) == UiSpec.ICON_WEAPON && UiSpec.iconOf(99) == UiSpec.ICON_WEAPON,
                "out-of-range kinds fall back to the weapon icon, as colour and label do");
        boolean distinct = true;
        for (int i = 0; i < UiSpec.PW_COUNT; i++) {
            for (int j = i + 1; j < UiSpec.PW_COUNT; j++) {
                if (UiSpec.iconOf(i) == UiSpec.iconOf(j)) distinct = false;
            }
        }
        ok(distinct, "every power-up has its own icon shape");
        // the non-power icons the HUD needs: skill buttons, settings toggles, sensitivity, lives
        int[] hud = {UiSpec.ICON_SUPER, UiSpec.ICON_BOMB_BTN, UiSpec.ICON_MUSIC, UiSpec.ICON_SFX,
                UiSpec.ICON_VIBRO, UiSpec.ICON_SLOW, UiSpec.ICON_FAST, UiSpec.ICON_SWITCH,
                UiSpec.ICON_LIVES};
        for (int i = 0; i < hud.length; i++) {
            ok(UiSpec.hasIcon(hud[i]), "hud icon " + i + " exists");
        }
        ok(!UiSpec.hasIcon(-1) && !UiSpec.hasIcon(UiSpec.ICON_COUNT), "icon range is half-open");
        ok(UiSpec.ICON_COUNT == 17, "icon count matches the roster Icons.draw() implements");
    }

    static void budget() {
        ok(UiSpec.ICON_MIN_DP > 0, "there is a minimum icon size");
        ok(UiSpec.PILL_ICON_DP >= UiSpec.ICON_MIN_DP, "pill icons clear the readability floor");
        ok(UiSpec.LEGEND_ICON_DP >= UiSpec.ICON_MIN_DP, "legend icons clear the readability floor");
        ok(UiSpec.POWER_ICON_RATIO > 0.4f && UiSpec.POWER_ICON_RATIO <= 0.75f,
                "power icon leaves room for the box border (drop r = dp(16))");
        ok(UiSpec.SKILL_ICON_RATIO > 0.4f && UiSpec.SKILL_ICON_RATIO <= 0.75f,
                "skill icon leaves room for the button ring (button r = dp(21))");
        // DeX: fit ~= 0.76, uiScale floor 0.8 -> the glyph must be designed bigger than the floor
        ok(UiSpec.minReadableDp(0.76f) > UiSpec.ICON_MIN_DP,
                "DeX fit demands a larger source glyph than portrait does");
        ok(Math.abs(UiSpec.minReadableDp(1f) - UiSpec.ICON_MIN_DP / 0.8f) < 1e-4,
                "portrait budget is the floor divided by the uiScale floor");
        ok(UiSpec.minReadableDp(0f) == UiSpec.minReadableDp(1f), "a zero fit is clamped, not divided");
        ok(UiSpec.minReadableDp(3f) == UiSpec.minReadableDp(1f), "fit never inflates the budget");
    }
}
