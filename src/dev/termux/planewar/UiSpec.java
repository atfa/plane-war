package dev.termux.planewar;

/**
 * The HUD contract: power-up kinds, their colours, their Chinese labels, the icon each one maps to,
 * and the size budget that keeps an icon readable on the smallest layout.
 *
 * <p>Pure Java - no Android imports - so plane/test_jvm.sh can assert it on the JVM (plane/test/
 * TestUi.java). The dependency runs one way: Icons.java (which does pull in the Android graphics
 * classes) consumes these constants, this file never references Icons, so the JVM suite compiles
 * without android.jar.
 *
 * <p>The eight colours are not design freedom: plane/check.py samples the power-up box palette
 * (amber 255,190,80) and plane/test.sh depends on it, so moving them here moved them byte for byte.
 * Labels stay Chinese on purpose - the icon layer adds instant graphic recognition on top, it does
 * not replace the text.
 */
public final class UiSpec {

    private UiSpec() {}

    // ---- power-up kinds (moved from GameView, values unchanged) ----
    public static final int PW_WEAPON = 0, PW_SHIELD = 1, PW_HEAL = 2, PW_BOMB = 3,
            PW_SCORE = 4, PW_RAPID = 5, PW_LASER = 6, PW_HOMING = 7;

    /** number of power-up kinds; every one of them must have a colour, a label and an icon */
    public static final int PW_COUNT = 8;

    // ---- icon kinds, consumed by Icons.draw() ----
    public static final int ICON_SHIELD = 0, ICON_HEAL = 1, ICON_BOMB = 2, ICON_SCORE = 3,
            ICON_RAPID = 4, ICON_LASER = 5, ICON_HOMING = 6, ICON_WEAPON = 7, ICON_SUPER = 8,
            ICON_BOMB_BTN = 9, ICON_MUSIC = 10, ICON_SFX = 11, ICON_VIBRO = 12, ICON_SLOW = 13,
            ICON_FAST = 14, ICON_SWITCH = 15, ICON_LIVES = 16, ICON_COUNT = 17;

    /**
     * Power-up colours, byte for byte the values GameView.powerColor() returned before this file
     * existed: (96,214,255) shield, (110,240,150) heal, (255,112,112) bomb, (255,220,120) score,
     * (230,140,255) rapid, (120,255,235) laser, (255,150,60) homing, (255,190,80) weapon/default.
     */
    static final int RGB_SHIELD = 0xFF60D6FF, RGB_HEAL = 0xFF6EF096, RGB_BOMB = 0xFFFF7070,
            RGB_SCORE = 0xFFFFDC78, RGB_RAPID = 0xFFE68CFF, RGB_LASER = 0xFF78FFEB,
            RGB_HOMING = 0xFFFF963C, RGB_WEAPON = 0xFFFFBE50;

    /** Labels, byte for byte the strings GameView.powerLabel() returned before this file existed. */
    static final String LABEL_SHIELD = "盾", LABEL_HEAL = "命", LABEL_BOMB = "爆", LABEL_SCORE = "分",
            LABEL_RAPID = "速", LABEL_LASER = "激光", LABEL_HOMING = "追", LABEL_WEAPON = "火";

    /** Colour for a power-up kind; out-of-range kinds read as the weapon amber, as before. */
    public static int color(int pw) {
        switch (pw) {
            case PW_SHIELD: return RGB_SHIELD;
            case PW_HEAL: return RGB_HEAL;
            case PW_BOMB: return RGB_BOMB;
            case PW_SCORE: return RGB_SCORE;
            case PW_RAPID: return RGB_RAPID;
            case PW_LASER: return RGB_LASER;
            case PW_HOMING: return RGB_HOMING;
            default: return RGB_WEAPON;
        }
    }

    /** Label for a power-up kind; out-of-range kinds read as the weapon glyph, as before. */
    public static String label(int pw) {
        switch (pw) {
            case PW_SHIELD: return LABEL_SHIELD;
            case PW_HEAL: return LABEL_HEAL;
            case PW_BOMB: return LABEL_BOMB;
            case PW_SCORE: return LABEL_SCORE;
            case PW_RAPID: return LABEL_RAPID;
            case PW_LASER: return LABEL_LASER;
            case PW_HOMING: return LABEL_HOMING;
            default: return LABEL_WEAPON;
        }
    }

    /** Icon for a power-up kind; out-of-range kinds read as the weapon icon, as color() does. */
    public static int iconOf(int pw) {
        switch (pw) {
            case PW_SHIELD: return ICON_SHIELD;
            case PW_HEAL: return ICON_HEAL;
            case PW_BOMB: return ICON_BOMB;
            case PW_SCORE: return ICON_SCORE;
            case PW_RAPID: return ICON_RAPID;
            case PW_LASER: return ICON_LASER;
            case PW_HOMING: return ICON_HOMING;
            default: return ICON_WEAPON;
        }
    }

    /** True for every icon kind Icons.draw() is required to paint. */
    public static boolean hasIcon(int icon) {
        return icon >= 0 && icon < ICON_COUNT;
    }

    // ---- size budget, in dp ----
    // DeX runs the play field at fit ~= 0.76 with a 120px top bar, so a HUD glyph has to survive
    // being scaled down twice: once by fit, once by the uiScale floor of 0.8. Anything under
    // ICON_MIN_DP after that turns into a smudge the player reads as decoration, not as information.

    /** Smallest stroke-visible icon that still reads as a shape on the smallest layout. */
    public static final float ICON_MIN_DP = 9f;

    /** Icon box drawn at the left of a HUD status pill (pill height is dp(20)). */
    public static final float PILL_ICON_DP = 11f;

    /** Icon radius as a fraction of the power-up drop box half-size (drop r = dp(16)). */
    public static final float POWER_ICON_RATIO = 0.62f;

    /** Icon radius on the super / bomb skill buttons (button r = dp(21)). */
    public static final float SKILL_ICON_RATIO = 0.52f;

    /** Icon radius in the title-screen item legend row. */
    public static final float LEGEND_ICON_DP = 10f;

    /** Smallest icon that stays legible once the DeX fit scale has been applied. */
    public static float minReadableDp(float fit) {
        float f = fit <= 0f ? 1f : (fit > 1f ? 1f : fit);
        return ICON_MIN_DP / (f * 0.8f);
    }
}
