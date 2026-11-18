package dev.termux.planewar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.Random;

/**
 * 雷霆战机 - vertical scrolling plane shooter.
 *
 * Control: drag anywhere on the screen - the drag vector moves the plane itself
 * (up/down/left/right), the nose always points up and the guns fire automatically.
 * The finger never has to touch the plane, so it never hides it.
 */
public class GameView extends View {

    static final String TAG = "planewar";

    static final int TITLE = 0, RUNNING = 1, PAUSED = 2, OVER = 3;

    // power-up kinds: the values live in UiSpec so the JVM suite can pin them
    static final int PW_WEAPON = UiSpec.PW_WEAPON, PW_SHIELD = UiSpec.PW_SHIELD,
            PW_HEAL = UiSpec.PW_HEAL, PW_BOMB = UiSpec.PW_BOMB, PW_SCORE = UiSpec.PW_SCORE,
            PW_RAPID = UiSpec.PW_RAPID, PW_LASER = UiSpec.PW_LASER, PW_HOMING = UiSpec.PW_HOMING;

    // ---- palette ----
    static final int BG_TOP = Color.rgb(9, 12, 26);
    static final int BG_BOT = Color.rgb(3, 5, 12);
    static final int NEB_A = Color.rgb(58, 26, 96);
    static final int NEB_B = Color.rgb(12, 62, 96);
    /** The player's identity colour - ShipSpec is the truth table for it now. */
    static final int SHIP = ShipSpec.identity(ShipSpec.PLAYER);
    static final int BULLET = Color.rgb(255, 236, 150);
    static final int TXT = Color.rgb(232, 240, 255);
    static final int TXT_DIM = Color.rgb(136, 156, 186);
    static final int GOLD = Color.rgb(255, 206, 96);

    // ---- tuning ----
    // drag sensitivity: the plane follows the finger at a fraction of the finger speed,
    // so a fast flick does not throw it across the whole screen
    static final float MAX_SPEED = 760f;        // dp/s cap at the standard level
    static final float DRAG_SMOOTH = 13f;       // higher = snappier, lower = softer
    /** 7 selectable drag sensitivities: 3 slower + standard (the old "灵敏", 0.85) + 3 faster.
        Value = how much of the finger's drag distance the plane covers. */
    static final float[] SENS = {0.30f, 0.45f, 0.65f, 0.85f, 1.10f, 1.40f, 1.80f};
    static final int SENS_STD = 5;              // default = 档 6
    static final String OPTS = "planewar_opts";

    /** 3 difficulty levels; 0 = 简单 is the original tuning.
       Each column scales one aspect of the game: spawn rate, enemy hp/speed/fire,
       event frequency, power-up drops, enemy cap and the score multiplier. */
    static final String[] DIFF_NAME = {"简单", "普通", "困难"};
    static final int[] DIFF_COL = {Color.rgb(96, 236, 176), GOLD, Color.rgb(255, 108, 108)};
    static final int DIFF_STD = 0;
    // the difficulty tables now live in Tuning (pure Java, asserted by plane/test_jvm.sh);
    // these aliases keep the call sites unchanged
    static final float[] D_SPAWN = Tuning.SPAWN;
    static final float[] D_NUM   = Tuning.NUM;
    static final float[] D_HP    = Tuning.HP;
    static final float[] D_SPEED = Tuning.SPEED;
    static final float[] D_FIRE  = Tuning.FIRE;
    static final float[] D_EVENT = Tuning.EVENT;
    static final float[] D_DROP  = Tuning.DROP;
    static final float[] D_SCORE = Tuning.SCORE;
    static final float[] D_RAMP  = Tuning.RAMP;
    static final int[] D_CAP     = Tuning.ENEMY_CAP;
    static final float BULLET_SPEED = 1050f;
    static final int MAX_WEAPON = 5;

    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rect = new RectF();
    final Path path = new Path();
    final Random rnd = new Random();
    final Handler ui = new Handler(Looper.getMainLooper());

    AudioFx audio;
    Vibro vibro;

    float W, H, dp;
    float topPad, botPad;
    /** screen adaptation: uiScale scales the whole game from the physical screen size,
       fit shrinks the option block on short screens. Both are computed in relayout(). */
    float uiScale = 1f, fit = 1f;
    float insetTop, insetBot;
    int state = TITLE;

    // ship: the nose always points up, dragging moves the plane
    float sx, sy, vx, vy, bank, moveX, moveY;
    int lives = 3, weapon = 1, shield = 0;
    float invuln, fireTimer, deathT, wallFx;
    float rapidT, frenzyT, slowT, bombSlow;

    // world
    final ArrayList<Bullet> bullets = new ArrayList<Bullet>();
    final ArrayList<Bullet> ebullets = new ArrayList<Bullet>();
    final ArrayList<Enemy> enemies = new ArrayList<Enemy>();
    final ArrayList<PowerUp> powers = new ArrayList<PowerUp>();
    final ArrayList<Particle> parts = new ArrayList<Particle>();
    final ArrayList<FloatText> texts = new ArrayList<FloatText>();
    final ArrayList<float[]> pending = new ArrayList<float[]>();  // delayed spawns: delay,type,x
    Star[] stars;
    float bgScroll;

    // scoring
    float scoreF, comboT;
    int score, kills, best, combo, maxCombo;
    float time, timeScale = 1f, lastVib;
    float spawnTimer, eventTimer;
    int eventsSeen;

    // effects
    float shake, shakeX, shakeY, flashRed, flashWhite, flashCyan, vignette;
    String banner = "", bannerSub = "";
    float bannerT;

    // ---- player active skills: the energy super blast and the bomb stock ----
    // The plane used to have no verb at all (drag + auto fire); these two give the player
    // a panic button with a charge-up, and a stock they choose when to spend.
    float energy;
    int bombStock, superCount, bombFired;
    int superTheme = -1;
    String superName = "";
    float superFxT;               // banner/glow timer of the last super blast
    float freezeT;                // 冰封万里: enemies are frozen for this long
    float quakeY;                 // 大地裂变: the wave travels up from here
    boolean quakeOn;
    final float[] boltX = new float[Super.MAX_IMPACTS];   // 万雷齐鸣 columns (visual)
    final float[] boltT = new float[Super.MAX_IMPACTS];
    int graze, bossAlive;         // reported in the periodic stats line
    Boss boss;                    // the live boss, null when none is on screen
    float bossX, bossY, bossEnter, bossHitFx;
    int bossesKilled;             // scales the next boss
    float nextBossAt = 80f;       // natural arrival time
    int bossHook;                 // test hook: force N boss arrivals
    float bossHookNext;
    boolean feverWas;             // rising edge detector for the fever banner
    int grazeHook;                // test hook: fire N grazes during a run
    int grazeHooked;              // grazes produced by the hook, counted apart from real ones
    float grazeHookNext;
    int superHook, bombHook;      // test hooks: fire the skill N times during a run
    int superAll;                 // test hook: fire one super per theme, so all five get covered
    float superAllNext;           //   next fire time
    int superThemeForce = -1;     // >= 0: force this theme on the next fireSuper()
    int superFails;               // test hook: themes that threw, logged at gameover
    float autoSkillT;             // autopilot skill throttle
    float fpsAvg = 60f, fpsAcc;
    int fpsFrames;
    final RectF btnSuper = new RectF(), btnBomb = new RectF();

    // options
    boolean musicOn = true, soundOn = true, vibrateOn = true;
    int sensIdx = SENS_STD;
    int diff = DIFF_STD;
    final RectF[] btnSens = new RectF[SENS.length];
    final RectF[] btnDiff = new RectF[DIFF_NAME.length];

    // test hooks
    boolean autopilot, godMode;
    float autoDie;
    int reportSec;
    float lastReport;

    // touch
    boolean touching;
    float lastTx, lastTy, downX, downY, moved;
    long downTime;

    // toggle buttons (title / pause / over screens)
    final RectF btnMusic = new RectF(), btnSound = new RectF(), btnVibe = new RectF();

    GameView(Context c) {
        super(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
        audio = new AudioFx(c);
        vibro = new Vibro(c);
        textPaint.setTextAlign(Paint.Align.LEFT);
        installInsets();
        for (int i = 0; i < btnSens.length; i++) btnSens[i] = new RectF();
        for (int i = 0; i < btnDiff.length; i++) btnDiff[i] = new RectF();
        resetRun();
        android.content.SharedPreferences o = c.getSharedPreferences(OPTS, 0);
        sensIdx = Math.max(0, Math.min(SENS.length - 1, o.getInt("sens", SENS_STD)));
        diff = Math.max(0, Math.min(DIFF_NAME.length - 1, o.getInt("diff", DIFF_STD)));
        best = Scores.best(c);
        Log.i(TAG, "loaded best=" + best + " board=" + Scores.load(c).size() + " entries");
        lastNs = System.nanoTime();
    }

    float dp(float v) { return v * dp * uiScale; }

    /** Scale everything from the physical screen: 360dp is the reference phone width,
       so a 5" 720p phone and a 7" tablet get the same layout, not a fixed pixel one. */
    private void relayout() {
        if (W <= 0 || H <= 0) return;
        float minSideDp = Math.min(W, H) / dp;
        uiScale = Math.max(0.8f, Math.min(1.5f, minSideDp / 360f));
        topPad = Math.max(insetTop + dp(12), dp(50));
        botPad = Math.max(insetBot + dp(8), dp(24));
        float need = (dp(30) + dp(20)) * 2 + dp(30) + dp(14) * 2;
        fit = Math.min(1f, (H - topPad - botPad) * 0.42f / need);
        Log.i(TAG, "layout " + (int) W + "x" + (int) H + " density=" + dp
                + " uiScale=" + String.format(java.util.Locale.US, "%.2f", uiScale)
                + " fit=" + String.format(java.util.Locale.US, "%.2f", fit)
                + " top=" + (int) topPad + " bot=" + (int) botPad);
    }

    /** insets on API 30+ (status bar, notch, cutout, gesture bar); older devices use the
       framework dimens, which is the usual fallback without androidx. */
    private void installInsets() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override public android.view.WindowInsets onApplyWindowInsets(
                        View v, android.view.WindowInsets in) {
                    android.graphics.Insets sb = in.getInsets(
                            android.view.WindowInsets.Type.systemBars()
                                    | android.view.WindowInsets.Type.displayCutout());
                    insetTop = sb.top;
                    insetBot = sb.bottom;
                    relayout();
                    return in;
                }
            });
        } else {
            insetTop = systemDimen("status_bar_height");
            insetBot = systemDimen("navigation_bar_height");
        }
    }

    private float systemDimen(String name) {
        int id = getResources().getIdentifier(name, "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        W = w;
        H = h;
        dp = getResources().getDisplayMetrics().density;
        android.util.Log.i(TAG, "size " + W + "x" + H + " dp=" + dp);
        relayout();
        stars = new Star[110];
        for (int i = 0; i < stars.length; i++) {
            stars[i] = new Star();
            stars[i].x = rnd.nextFloat() * W;
            stars[i].y = rnd.nextFloat() * H;
            stars[i].z = 0.25f + rnd.nextFloat() * rnd.nextFloat() * 1.1f;
        }
        sx = W / 2f;
        sy = H * 0.72f;
    }

    // ------------------------------------------------------------------ run setup

    void resetRun() {
        bullets.clear();
        ebullets.clear();
        laserT = homingT = beamCd = 0;
        laserHits = missilesFired = 0;
        enemies.clear();
        powers.clear();
        parts.clear();
        texts.clear();
        pending.clear();
        lives = 3;
        weapon = 1;
        shield = 0;
        scoreF = 0;
        score = 0;
        kills = 0;
        combo = 0;
        maxCombo = 0;
        comboT = 0;
        time = 0;
        vx = vy = 0;
        bank = 0;
        moveX = moveY = 0;
        invuln = 1.2f;
        fireTimer = 0;
        deathT = 0;
        rapidT = frenzyT = slowT = bombSlow = 0;
        spawnTimer = 0.6f;
        eventTimer = 16f;
        eventsSeen = 0;
        shake = flashRed = flashWhite = flashCyan = vignette = 0;
        bannerT = 0;
        timeScale = 1f;
        energy = 0f;
        bombStock = Tuning.BOMB_START;
        superCount = 0;
        superFails = 0;
        bombFired = 0;
        superTheme = -1;
        superName = "";
        superFxT = 0f;
        freezeT = 0f;
        quakeOn = false;
        quakeY = 0f;
        graze = 0;
        grazeHooked = 0;
        feverWas = false;
        bossAlive = 0;
        boss = null;
        bossesKilled = 0;
        nextBossAt = 80f;
        bossEnter = 0f;
        bossHitFx = 0f;
        autoSkillT = 0f;
        for (int i = 0; i < boltT.length; i++) boltT[i] = 0f;
        if (W > 0) {
            sx = W / 2f;
            sy = H * 0.72f;
        }
    }

    void startGame() {
        resetRun();
        state = RUNNING;
        audio.startMusic();
        audio.play(AudioFx.START);
        vibro.power();
        banner("开始！", "拖动屏幕移动飞机");
        Log.i(TAG, "start");
    }

    void pauseGame() {
        if (state == RUNNING) {
            state = PAUSED;
            audio.stopMusic();
            vibro.stop();
        }
    }

    void resumeGame() {
        if (state == PAUSED) {
            state = RUNNING;
            audio.startMusic();
        }
    }

    void resume() { audio.resume(); }

    void release() { audio.release(); }

    /** @return true if the back press was consumed by the game */
    boolean onBackPressed() {
        if (state == RUNNING) {
            pauseGame();
            return true;
        }
        if (state == PAUSED) {
            resumeGame();
            return true;
        }
        if (state == OVER) {
            toTitle();
            return true;
        }
        return false;
    }

    void toTitle() {
        state = TITLE;
        resetRun();
        audio.stopMusic();
        invalidate();
    }

    // ------------------------------------------------------------------ test hooks

    void setAutopilot(boolean b) { autopilot = b; }
    void setGod(boolean b) { godMode = b; }
    void setReport(int sec) { reportSec = sec; }

    /** test hooks: fire the super blast / the bomb N times during the run (same code path) */
    void hookSuper(int n) { superHook = n; }
    void hookBomb(int n) { bombHook = n; }

    /** test hook: N grazes during the run, through the collision code's own entry point */
    void hookGraze(int n) {
        grazeHook = n;
        grazeHookNext = 6f;
    }

    /** test hook: force N boss arrivals, starting at t=6s */
    void hookBoss(int n) {
        bossHook = n;
        bossHookNext = 6f;
    }

    /** test hook: fire every super theme once, spaced out, so the test can assert all five names */
    void hookSuperAll(int n) {
        superAll = Math.min(n, Super.COUNT);
        superAllNext = 5f;
    }

    void hookPower(int kind) {
        // spawn it a few seconds into the run, stacked right above the ship: at launch sx/sy are not
        // laid out yet and reset() clears the field, so a power-up added from the intent hook would
        // never be collected and the hook would prove nothing
        hookQueue.add(kind);
        if (hookPowerIn <= 0) hookPowerIn = 6f + hookPowerSeq++ * 1.5f;
    }

    int hookPowerKind = -1, hookPowerSeq;
    float hookPowerIn;
    final ArrayList<Integer> hookQueue = new ArrayList<Integer>();

    /**
     * --ei powerall N: queue one drop box of every power-up kind, laid out in a row, so a single
     * screenshot shows the whole icon set (the icon layer is what a screenshot has to prove).
     */
    void hookPowerAll(int n) {
        hookPowerAll = Math.max(1, Math.min(UiSpec.PW_COUNT, n));
        hookQueue.add(UiSpec.PW_WEAPON);   // update() only drains the queue when it is non-empty
        if (hookPowerIn <= 0) hookPowerIn = 6f;
    }

    int hookPowerAll;

    private void spawnHookPower() {
        if (hookPowerAll > 0) {
            int n = hookPowerAll;
            hookPowerAll = 0;
            hookQueue.clear();
            for (int i = 0; i < n; i++) {
                PowerUp p = new PowerUp();
                p.kind = i;
                p.r = dp(16);
                p.x = W * (i + 1) / (n + 1);
                p.y = H * 0.28f + (i % 2) * dp(44);
                p.vx = 0;
                p.vy = 0;          // hover: the row has to survive long enough to be screenshotted
                p.t = i * 0.37f;
                powers.add(p);
            }
            Log.i(TAG, "pup hook row n=" + n);
            return;
        }
        hookPowerKind = hookQueue.size() > 0 ? hookQueue.remove(0) : -1;
        for (int i = 0; i < 9; i++) {
            PowerUp p = new PowerUp();
            p.kind = hookPowerKind;
            p.r = dp(16);
            p.x = sx + ((i % 3) - 1) * dp(26);
            p.y = sy - dp(50) - (i / 3) * dp(46);
            p.vx = 0;
            p.vy = dp(170);
            p.t = 0;
            powers.add(p);
        }
        Log.i(TAG, "pup hook drop kind=" + hookPowerKind);
    }

    void hookSpawn(int type) { spawnEnemy(type, W / 2f); }

    /**
     * --ei show N: park one of each of the first N enemy types in a row across the field, so a
     * single screenshot shows the whole enemy art set (the same trick hookPowerAll plays for the
     * icon layer). The row is invulnerable and does not shoot: it is there to be looked at.
     */
    void hookShow(int n) {
        hookShow = Math.max(1, Math.min(ShipSpec.ENEMY_TYPES, n));
        if (hookShowIn <= 0) hookShowIn = 6f;
    }

    int hookShow;
    float hookShowIn;
    boolean showNoFire;

    private void spawnHookShow() {
        int n = hookShow;
        hookShow = 0;
        showNoFire = true;
        // two rows, small types first: the heavy and the mothership are 3x the span of a grunt and
        // would overlap their neighbours in a single evenly spaced line
        for (int i = 0; i < n; i++) {
            int row = i < 5 ? 0 : 1;
            int col = row == 0 ? i : i - 5;
            int per = row == 0 ? Math.min(n, 5) : n - 5;
            spawnEnemy(i, W * (col + 1) / (per + 1f));
            Enemy e = enemies.get(enemies.size() - 1);
            e.y = H * (row == 0 ? 0.24f : 0.46f);
            e.vx = 0;
            e.vy = 0;              // hover: the row has to survive the screenshot
            e.fire = 999f;         // and stay quiet while it is photographed
            e.hp = e.maxhp = 1e9f;  // the player's fire (and the autopilot's) cannot clear it
        }
        Log.i(TAG, "show n=" + n);
    }

    /** --ei wave N: N formations, spaced out, so a 26s test run can see them all.
     *  Deferred: the intent hooks run before the run starts, and resetRun() clears the spawn queue. */
    int waveHook;

    void hookWave(int n) { waveHook = n; }

    private void applyWaveHook() {
        int n = waveHook;
        waveHook = 0;
        for (int i = 0; i < n; i++) {
            pending.add(new float[]{1.0f + i * 1.6f, -2f, i % Wave.COUNT});
            // the four new types are queued too, so a 26s field run sees every one of them
            // the mothership goes first: it needs the most runway to be seen producing minions
            int[] order = {Wave.MOTHER, Wave.SPLITTER, Wave.MINER, Wave.SNIPER};
            pending.add(new float[]{1.3f + i * 1.6f, order[i % order.length], -1f});
        }
        Log.i(TAG, "wave hook armed n=" + n);
    }

    void hookEvent(int k) { triggerEvent(k); }

    void setAutoDie(float sec) { autoDie = sec; }

    /** haptics are throttled so a bullet-hell does not turn into a continuous buzz */
    boolean vibGate(float gap) {
        if (time - lastVib < gap) return false;
        lastVib = time;
        return true;
    }

    // ------------------------------------------------------------------ input

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touching = true;
                lastTx = downX = x;
                lastTy = downY = y;
                moved = 0;
                downTime = System.currentTimeMillis();
                Log.i(TAG, "touch down state=" + state + " at " + (int) x + "," + (int) y);
                if (state == RUNNING) audio.play(AudioFx.PICK, 1.6f);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = x - lastTx, dy = y - lastTy;
                lastTx = x;
                lastTy = y;
                float d = (float) Math.hypot(dx, dy);
                moved += d;
                if (state == RUNNING) {
                    moveX += dx;
                    moveY += dy;
                } else if (state == TITLE) {
                    if (moved > dp(18f)) startGame();
                } else if (state == OVER) {
                    if (moved > dp(18f) && deathT > 0.9f) toTitle();
                } else if (state == PAUSED) {
                    if (moved > dp(14f)) resumeGame();
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                boolean tap = moved < dp(14f) && System.currentTimeMillis() - downTime < 400;
                if (tap) onTap(x, y);
                return true;

            default:
                return true;
        }
    }

    /** Taps are used for the menus and for the two skill buttons during play. */
    private void onTap(float x, float y) {
        if (state == RUNNING) {
            if (btnSuper.contains(x, y)) { fireSuper(); return; }
            if (btnBomb.contains(x, y)) { useBomb(); return; }
            return;
        }
        if (state == TITLE || state == PAUSED) {
            for (int i = 0; i < btnSens.length; i++)
                if (btnSens[i].contains(x, y)) { setSens(i); return; }
            for (int i = 0; i < btnDiff.length; i++)
                if (btnDiff[i].contains(x, y)) { setDiff(i); return; }
            if (state == PAUSED) {
                resumeGame();
                return;
            }
            if (btnMusic.contains(x, y)) { setMusic(!musicOn); return; }
            if (btnSound.contains(x, y)) { setSound(!soundOn); return; }
            if (btnVibe.contains(x, y)) { setVibe(!vibrateOn); return; }
            startGame();
            return;
        }
        if (state == OVER && deathT > 0.9f) toTitle();
    }

    void setMusic(boolean on) {
        musicOn = on;
        audio.setMusicOn(on);
        vibro.tap(18);
    }

    void setSound(boolean on) {
        soundOn = on;
        audio.setSoundOn(on);
        if (on) audio.play(AudioFx.PICK);
    }

    float sens() { return SENS[sensIdx]; }

    void setSens(int i) {
        sensIdx = Math.max(0, Math.min(SENS.length - 1, i));
        getContext().getSharedPreferences(OPTS, 0).edit().putInt("sens", sensIdx).apply();
        android.util.Log.i(TAG, "sens=" + (sensIdx + 1) + " x" + SENS[sensIdx]);
        audio.play(AudioFx.PICK);
        vibro.tap(20);
    }

    void setDiff(int i) {
        diff = Math.max(0, Math.min(DIFF_NAME.length - 1, i));
        getContext().getSharedPreferences(OPTS, 0).edit().putInt("diff", diff).apply();
        android.util.Log.i(TAG, "difficulty=" + DIFF_NAME[diff] + " score x" + D_SCORE[diff]);
        audio.play(AudioFx.PICK);
        vibro.tap(24);
    }

    /** enemy fire interval scaled by the difficulty */
    float ef(float sec) { return sec / D_FIRE[diff]; }

    /** difficulty ramp: harder levels reach the "late game" enemy mix sooner */
    float ramp() { return time * D_RAMP[diff]; }

    void setVibe(boolean on) {
        vibrateOn = on;
        vibro.setOn(on);
        if (on) vibro.power();
    }

    @Override public boolean onKeyDown(int code, KeyEvent ev) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_SPACE) {
            if (state == TITLE) startGame();
            else if (state == PAUSED) resumeGame();
            else if (state == OVER && deathT > 0.9f) toTitle();
            return true;
        }
        if (code == KeyEvent.KEYCODE_ESCAPE) {
            if (onBackPressed()) return true;
        }
        return super.onKeyDown(code, ev);
    }

    // ------------------------------------------------------------------ main loop

    long lastNs;

    @Override protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = (now - lastNs) / 1e9f;
        lastNs = now;
        if (dt > 0.05f) dt = 0.05f;
        if (dt < 0f) dt = 0f;

        if (state == RUNNING) {
            if (bombSlow > 0) bombSlow -= dt;
            timeScale = bombSlow > 0 ? 0.35f : 1f;
            update(dt * timeScale);
        } else {
            if (state == OVER) deathT += dt;
            updateBg(dt);
            updateParticles(dt);
            decayFx(dt);
        }
        // rolling frame-rate average, reported in the stats line and asserted by test.sh
        fpsAcc += dt;
        fpsFrames++;
        if (fpsAcc >= 1f) {
            fpsAvg = fpsFrames / fpsAcc;
            fpsAcc = 0f;
            fpsFrames = 0;
        }
        render(c);
        postInvalidateOnAnimation();
    }

    private void update(float dt) {
        realDt = dt;
        time += dt;
        scoreF += dt * 12f;
        if (slowT > 0) slowT -= dt;
        if (frenzyT > 0) frenzyT -= dt;
        if (rapidT > 0) rapidT -= dt;
        if (invuln > 0) invuln -= dt;
        if (bannerT > 0) bannerT -= dt;
        if (comboT > 0) {
            comboT -= dt;
            if (comboT <= 0) combo = 0;
        }
        if (reportSec > 0 && time - lastReport >= reportSec) {
            lastReport = time;
            Log.i(TAG, "stats t=" + (int) time + " score=" + score + " kills=" + kills
                    + " weapon=" + weapon + " lives=" + lives + " enemies=" + enemies.size()
                    + " energy=" + (int) energy + " bomb=" + bombStock + " graze=" + graze
                    + " fever=" + (feverActive() ? 1 : 0)
                    + " boss=" + bossAlive + (boss != null ? "/" + (int) boss.hp() : "")
                    + " fps=" + String.format(java.util.Locale.US, "%.1f", fpsAvg));
        }

        // test hooks: force the skills during a short autopilot run (same entry points)
        if (superHook > 0 && time > 5f) {
            superHook--;
            energy = Tuning.ENERGY_MAX;
            fireSuper();
        }
        if (bombHook > 0 && time > 8f) {
            bombHook--;
            useBomb();
        }
        // test hook: grazes go through the same grazeHit() the collision code uses
        if (grazeHook > 0 && time >= grazeHookNext) {
            grazeHookNext += 0.35f;
            grazeHook--;
            grazeHit(sx + dp(34), sy - dp(10), Color.rgb(255, 120, 120), true);
        }
        // test hook: walk every theme in turn - a theme that throws kills the run, and the
        // test asserts all five names show up, so no theme can hide behind the random pick
        if (superAll > 0 && time >= superAllNext) {
            superAllNext += 1.4f;
            superThemeForce = (Super.COUNT - superAll) % Super.COUNT;
            energy = Tuning.ENERGY_MAX;
            try {
                fireSuper();
            } catch (RuntimeException ex) {
                superFails++;
                Log.e(TAG, "super theme=" + superThemeForce + " name=" + Super.name(superThemeForce)
                        + " threw " + ex);
            }
            superThemeForce = -1;
            superAll--;
        }

        // test hook: force a boss arrival - a 26s autopilot run never reaches t=80s
        if (bossHook > 0 && time >= bossHookNext) {
            bossHookNext += 12f;
            bossHook--;
            spawnBoss(bossesKilled);
        }
        updateBoss(dt);

        // passive charge: a run always earns at least one super blast
        energy = Math.min(Tuning.ENERGY_MAX, energy + Tuning.energyPerSec(diff) * dt);
        if (superFxT > 0) superFxT -= dt;
        if (freezeT > 0) freezeT -= dt;
        for (int i = 0; i < boltT.length; i++)
            if (boltT[i] > 0) boltT[i] -= dt;
        if (quakeOn) updateQuake(dt);
        if (autopilot) autoSkill(dt);

        if (autoDie > 0 && time > autoDie) {
            autoDie = 0;
            gameOver();
            return;
        }
        eventTimer -= dt;
        if (eventTimer <= 0) triggerEvent(-1);
        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            spawnCount++;
            float rt = ramp();
            // every third spawn is a formation, so the run has waves instead of a random drizzle
            if (spawnCount % 3 == 0 && rt > 20f) spawnWave(waveSeq++ % Wave.COUNT);
            else spawnEnemy(-1, -1);
            // the new enemy types join on their own cadence so the field test can see all four
            if (rt > 30f && spawnCount % 7 == 0) spawnNewTypeWave();
            spawnTimer = Math.max(0.35f, 1.5f - rt * 0.008f) * D_SPAWN[diff]
                    * (0.6f + rnd.nextFloat() * 0.8f);
        }

        // delayed wave spawns
        for (int i = pending.size() - 1; i >= 0; i--) {
            float[] p = pending.get(i);
            p[0] -= dt;
            if (p[0] <= 0) {
                int ty = (int) p[1];
                if (ty == -2) spawnWave((int) p[2]);   // a queued formation
                else if (ty < 0) spawnPendingPower(p[2]);   // type -1 = power-up crate
                else spawnEnemy(ty, p[2]);
                pending.remove(i);
            }
        }

        // hit-stop scales the world (enemies, bullets) only: the run clock, the autodie timer and
        // the boss entrance camera keep running on real time, so a freeze can never stall a test
        if (hitStop > 0f) {
            hitStop -= dt;
            hitStopBudget = Math.max(0f, hitStopBudget - dt);
            if (hitStop <= 0f) hitStop = 0f;
        } else {
            hitStopBudget = Math.min(Tuning.HIT_STOP_BUDGET, hitStopBudget + dt);
        }
        float wdt = hitStop > 0f ? dt * Tuning.HIT_STOP_SCALE : dt;
        steer(dt);
        fire(dt);
        if (waveHook > 0) applyWaveHook();
        if (hookShow > 0 && time >= hookShowIn) spawnHookShow();
        if (!hookQueue.isEmpty()) {
            hookPowerIn -= dt;
            if (hookPowerIn <= 0) {
                spawnHookPower();
                hookPowerIn = 1.5f;
            }
        }
        moveBullets(wdt);
        moveEnemies(wdt);
        movePowers(dt);
        collisions();
        updateBg(dt);
        updateParticles(dt);
        decayFx(dt);

        score = (int) scoreF;
        if (score > best) best = score;
    }

    private void decayFx(float dt) {
        shake = decay(shake, dt, 6f);
        flashRed = decay(flashRed, dt, 3.4f);
        flashWhite = decay(flashWhite, dt, 5f);
        flashCyan = decay(flashCyan, dt, 4f);
        vignette = decay(vignette, dt, 2f);
        wallFx = decay(wallFx, dt, 8f);
        if (shake > 0.05f) {
            shakeX = (rnd.nextFloat() * 2 - 1) * shake * dp;
            shakeY = (rnd.nextFloat() * 2 - 1) * shake * dp;
        } else {
            shakeX = shakeY = 0;
        }
    }

    static float decay(float v, float dt, float rate) {
        return (float) (v * Math.exp(-rate * dt));
    }

    // ------------------------------------------------------------------ ship

    /** Drag anywhere on the screen: the drag vector moves the plane (nose stays up). */
    private void steer(float dt) {
        if (autopilot) autoPilot();
        // this frame's finger delta becomes a target velocity (amplified a little)
        float cap = MAX_SPEED * (0.5f + 0.95f * sens()) * dp;
        float tvx = clamp(moveX * sens() / Math.max(dt, 1e-4f), -cap, cap);
        float tvy = clamp(moveY * sens() / Math.max(dt, 1e-4f), -cap, cap);
        moveX = moveY = 0;
        float k = 1f - (float) Math.exp(-DRAG_SMOOTH * dt);
        vx += (tvx - vx) * k;
        vy += (tvy - vy) * k;
        if (!touching) {                       // release: glide to a stop
            float f = (float) Math.exp(-3.4f * dt);
            vx *= f;
            vy *= f;
        }
        sx += vx * dt;
        sy += vy * dt;

        bank = clamp(vx / (MAX_SPEED * dp), -1f, 1f) * 0.6f;   // visual banking

        float m = dp(18);
        boolean hit = false;
        if (sx < m) { sx = m; if (vx < 0) vx = 0; hit = true; }
        else if (sx > W - m) { sx = W - m; if (vx > 0) vx = 0; hit = true; }
        if (sy < topPad + m) { sy = topPad + m; if (vy < 0) vy = 0; hit = true; }
        else if (sy > H - botPad - m) { sy = H - botPad - m; if (vy > 0) vy = 0; hit = true; }
        if (hit && wallFx <= 0 && Math.hypot(vx, vy) > dp(120)) {
            wallFx = 0.3f;
            audio.play(AudioFx.WALL);
            vibro.tick();
            shake = Math.max(shake, 2.5f);
            for (int i = 0; i < 8; i++) spark(sx, sy, Color.rgb(160, 220, 255), dp(90));
        }

        // engine trail straight out of the tail
        if (rnd.nextFloat() < 0.9f) {
            Particle p = new Particle();
            p.kind = 4;
            p.x = sx + (rnd.nextFloat() * 2 - 1) * dp(4);
            p.y = sy + dp(15) + (rnd.nextFloat() * 2 - 1) * dp(3);
            p.vx = -vx * 0.15f + (rnd.nextFloat() * 2 - 1) * dp(30);
            p.vy = dp(110) + Math.abs(vy) * 0.1f;
            p.life = p.max = 0.3f + rnd.nextFloat() * 0.16f;
            p.size = dp(5) + rnd.nextFloat() * dp(4);
            p.color = frenzyT > 0 || feverActive() ? Color.rgb(255, 140, 70) : Color.rgb(90, 200, 255);
            parts.add(p);
        }
    }

    static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    /** simple AI so the game can be screenshot / play-tested from a shell */
    /** simple AI so the game can be screenshot / play-tested from a shell */
    private void autoPilot() {
        float tx = sx, ty = H * 0.68f;
        float bd = 1e9f;
        for (PowerUp p : powers) {
            float d = (p.x - sx) * (p.x - sx) + (p.y - sy) * (p.y - sy);
            if (d < bd) { bd = d; tx = p.x; ty = p.y; }
        }
        float be = 1e9f;
        for (Enemy e : enemies) {
            float d = (e.x - sx) * (e.x - sx) + (e.y - sy) * (e.y - sy);
            if (d < be) { be = d; tx = e.x; ty = Math.min(sy, e.y + dp(150)); }
        }
        // a boss is worth lining up on: it counts as a target at up to 2.5x the grunt range
        if (boss != null && bossEnter > 1.5f) {
            float d = (bossX - sx) * (bossX - sx) + (bossY - sy) * (bossY - sy);
            if (d < be * 2.5f) { tx = bossX; ty = Math.min(sy, bossY + dp(150)); }
        }
        // hug the graze band: dodging at the outer edge turns dodges into grazes, which is the
        // whole risk/reward loop; right after a hit it backs off so a non-god run still survives
        float flee = dp(invuln > 0f ? Tuning.AUTO_FLEE_SAFE_DP : Tuning.AUTO_FLEE_DP);
        for (Bullet b : ebullets) {
            if (Math.hypot(b.x - sx, b.y - sy) < flee) {
                tx = sx + (sx - b.x) * 3;
                ty = sy + (sy - b.y) * 2;
                break;
            }
        }
        float k = Math.min(1f, 26f * 0.016f);
        moveX += (tx - sx) * k;
        moveY += (ty - sy) * k;
    }

    static float norm(float a) {
        while (a > (float) Math.PI) a -= (float) (2 * Math.PI);
        while (a < (float) -Math.PI) a += (float) (2 * Math.PI);
        return a;
    }

    float laserT, homingT, beamCd;      // laser beam / lock-on power-up timers, beam settlement cooldown
    int laserHits, missilesFired;       // totals for the log lines

    private void fire(float dt) {
        if (showNoFire) return;   // --ei show: the row is there to be photographed, not shot at
        laserT -= dt;
        homingT -= dt;
        beamCd -= dt;
        if (laserT > 0 && Weapon.beamDue(beamCd, dt)) {
            beamCd = Weapon.beamCooldown();
            beamSettle();
        }
        fireTimer -= dt;
        float gap = rapidT > 0 || frenzyT > 0 ? 0.065f : 0.135f;
        if (feverActive()) gap *= Tuning.FEVER_FIRE_RATE;
        if (fireTimer > 0) return;
        fireTimer = gap;

        float sp = BULLET_SPEED * dp;
        float dmg = 1f + (weapon - 1) * 0.35f;
        int col = frenzyT > 0 ? Color.rgb(255, 170, 90) : BULLET;
        // off = sideways offset (perpendicular to the heading), a = extra angle
        float[] pat = Weapon.pattern(weapon);
        float spread = Weapon.spreadScale(weapon);
        for (int i = 0; i + 2 < pat.length; i += 3) {
            shot(pat[i] * spread * dp, pat[i + 1], sp, dmg * pat[i + 2], col);
        }
        // lock-on missiles: only while the 追踪 power-up is live, and only from LV3 up
        int ms = homingT > 0 ? Weapon.missiles(weapon) : 0;
        for (int i = 0; i < ms; i++) missile(sp, dmg * 1.6f, i == 0 ? -dp(12) : dp(12));
        // muzzle flash
        Particle p = new Particle();
        p.kind = 4;
        p.x = sx;
        p.y = sy - dp(18);
        p.life = p.max = 0.08f;
        p.size = dp(14);
        p.color = col;
        parts.add(p);
        audio.play(AudioFx.SHOT, 0.95f + weapon * 0.05f);
    }

    /** one bullet: fired straight up from a sideways offset, optionally slanted */
    private void shot(float off, float a, float sp, float dmg, int col) {
        Bullet b = new Bullet();
        b.x = sx + off;
        b.y = sy - dp(10);
        b.vx = (float) Math.sin(a) * sp;
        b.vy = -(float) Math.cos(a) * sp;
        b.r = dp(3.6f) + weapon * dp(0.5f);
        b.dmg = dmg;
        b.life = a == 0 ? 1.5f : 1.3f;
        b.color = col;
        bullets.add(b);
    }

    /** homing missile: locks the nearest legal target (the boss included) and steers onto it */
    private void missile(float off, float dmg, float sp) {
        if (bullets.size() >= Weapon.MAX_PLAYER_BULLETS) return;
        Bullet b = new Bullet();
        b.x = sx + off;
        b.y = sy - dp(8);
        b.vx = 0;
        b.vy = -sp;
        b.r = dp(5f);
        b.dmg = dmg;
        b.life = 2.4f;
        b.color = Color.rgb(255, 150, 60);
        b.homing = true;
        b.target = Weapon.pickTarget(tx, ty, tn(), bossAlive(), bossX, bossY, sx, sy, -dp(20));
        bullets.add(b);
        missilesFired++;
        if (missilesFired % 10 == 1) Log.i(TAG, "missile n=" + missilesFired);
        audio.play(AudioFx.MISSILE, 1f);
    }

    /** the laser column: one settlement per target per Weapon.BEAM_TICK, never one per frame */
    private void beamSettle() {
        float half = Weapon.BEAM_HALF_DP * dp;
        int hit = 0;
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (e.hp <= 0 || Math.abs(e.x - sx) > half + e.r) continue;
            e.hp -= Weapon.BEAM_DMG;
            hit++;
            if (e.hp <= 0) killEnemy(e, i);
        }
        if (boss != null && !boss.isDead() && bossEnter > 1.2f && Math.abs(bossX - sx) < half + bossRadius()) {
            boss.hurt(Weapon.BEAM_DMG);
            hit++;
        }
        laserHits += hit;
        Particle p = new Particle();
        p.kind = 1;
        p.x = sx;
        p.y = sy - dp(60);
        p.life = p.max = 0.18f;
        p.size = dp(30);
        p.color = Color.rgb(120, 255, 235);
        parts.add(p);
        if (hit > 0 && laserHits % 20 < 20) Log.i(TAG, "laser tick n=" + hit + " total=" + laserHits);
    }

    /** number of live enemies, for the pure-JVM target picker */
    private int tn() {
        int n = 0;
        for (Enemy e : enemies) { tx[n] = e.x; ty[n] = e.y; n++; }
        return n;
    }

    private boolean bossAlive() { return boss != null && !boss.isDead(); }

    private float bossRadius() { return bossR(); }

    /** scratch arrays for the pure-JVM target picker (no allocation per frame) */
    final float[] tx = new float[64], ty = new float[64];

    /** steer a missile onto its locked target, re-acquiring when it dies or leaves the screen */
    private void steer(Bullet b, float dt) {
        int n = tn();
        float cx = 0, cy = 0;
        boolean live = false;
        if (b.target >= 0 && b.target < n) { cx = tx[b.target]; cy = ty[b.target]; live = true; }
        else if (b.target == n && bossAlive()) { cx = bossX; cy = bossY; live = true; }
        if (!live) {
            b.target = Weapon.pickTarget(tx, ty, n, bossAlive(), bossX, bossY, sx, sy, -dp(20));
            if (b.target < 0) return;
            if (b.target < n) { cx = tx[b.target]; cy = ty[b.target]; }
            else { cx = bossX; cy = bossY; }
        }
        float a = (float) Math.atan2(cy - b.y, cx - b.x);
        float ca = (float) Math.atan2(b.vy, b.vx);
        float da = a - ca;
        while (da > (float) Math.PI) da -= 2 * (float) Math.PI;
        while (da < -(float) Math.PI) da += 2 * (float) Math.PI;
        float turn = (float) Math.min(Math.abs(da), 4.2f * dt) * (da > 0 ? 1 : -1);
        float sp = (float) Math.hypot(b.vx, b.vy);
        ca += turn;
        b.vx = (float) Math.cos(ca) * sp;
        b.vy = (float) Math.sin(ca) * sp;
    }

    // ------------------------------------------------------------------ spawning

    /** type < 0 = pick a random type for the current time; x < 0 = random lane */
    private void spawnEnemy(int type, float x) {
        if (enemies.size() > D_CAP[diff]) return;
        float rt = ramp();
        if (type < 0) {
            double r = rnd.nextDouble();
            if (rt < 18) type = 0;
            else if (rt < 45) type = r < 0.72 ? 0 : 1;
            else if (rt < 90) type = r < 0.55 ? 0 : (r < 0.82 ? 1 : 2);
            else if (rt < 150) type = r < 0.42 ? 0 : (r < 0.66 ? 1 : (r < 0.86 ? 2 : 3));
            else type = r < 0.34 ? 0 : (r < 0.58 ? 1 : (r < 0.78 ? 2 : 3));
        }
        Enemy e = new Enemy();
        e.type = type;
        e.t = 0;
        e.flash = 0;
        e.phase = rnd.nextFloat() * (float) (2 * Math.PI);
        float hpScale = (1f + rt / 75f) * D_HP[diff];
        float spScale = (1f + rt / 260f) * D_SPEED[diff];
        if (x < 0) x = dp(30) + rnd.nextFloat() * (W - dp(60));
        e.x = x;
        e.y = -dp(40);
        switch (type) {
            case 1: // darter - fast, homes in on the player
                e.r = dp(13);
                e.hp = e.maxhp = 2 * hpScale;
                e.vx = (rnd.nextFloat() * 2 - 1) * dp(60);
                e.vy = dp(150) * spScale;
                e.score = 180;
                break;
            case 2: // gunship - hovers and shoots aimed bursts
                e.r = dp(23);
                e.hp = e.maxhp = 11 * hpScale;
                e.vx = (rnd.nextBoolean() ? 1 : -1) * dp(60);
                e.vy = dp(90);
                e.score = 460;
                e.fire = ef(1.1f);
                break;
            case 3: // elite - sine path, 3-way spread
                e.r = dp(19);
                e.hp = e.maxhp = 17 * hpScale;
                e.vx = 0;
                e.vy = dp(70) * spScale;
                e.score = 720;
                e.fire = ef(1.4f);
                break;
            case 5: // splitter - breaks into two grunts when it dies
                e.r = dp(20);
                e.hp = e.maxhp = 7 * hpScale;
                e.vx = (rnd.nextBoolean() ? 1 : -1) * dp(90);
                e.vy = dp(115) * spScale;
                e.score = 380;
                break;
            case 6: // miner - hovers high and drops hanging mines
                e.r = dp(24);
                e.hp = e.maxhp = 26 * hpScale;
                e.vx = (rnd.nextBoolean() ? 1 : -1) * dp(70);
                e.vy = dp(150);
                e.score = 620;
                e.fire = 0.05f;          // acts on arrival: the autopilot deletes new types in under a second
                break;
            case 7: // sniper - telegraphs a red line, then fires one fast shot
                e.r = dp(15);
                e.hp = e.maxhp = 14 * hpScale;
                e.vx = 0;
                e.vy = dp(190);
                e.score = 540;
                e.fire = 0.05f;          // telegraph on arrival, so a fast kill cannot hide it
                break;
            case 8: // mothership - parks at the top and keeps producing grunts
                e.r = dp(38);
                e.hp = e.maxhp = 90 * hpScale;
                e.vx = (rnd.nextBoolean() ? 1 : -1) * dp(45);
                e.vy = dp(90);
                e.score = 2600;
                e.fire = 0.05f;          // first minion on arrival
                break;
            case 4: // heavy - mini boss
                e.r = dp(46);
                e.hp = e.maxhp = 95 * hpScale;
                e.vx = (rnd.nextBoolean() ? 1 : -1) * dp(40);
                e.vy = dp(60);
                e.score = 4200;
                e.fire = ef(1.6f);
                break;
            default: // grunt - straight down with a drift
                e.r = dp(15);
                e.hp = e.maxhp = 2 * hpScale;
                e.vx = (rnd.nextFloat() * 2 - 1) * dp(70);
                e.vy = dp(135) * spScale;
                e.score = 100;
                break;
        }
        e.color = ShipSpec.identity(type);   // the art truth table owns enemy colours
        enemies.add(e);
        Log.i(TAG, "enemy spawn type=" + type);
    }

    int wavesSent, spawnCount, waveSeq;

    /** formation wave: lanes come from Wave (pure Java) and are mapped into [r, W-r] when they spawn */
    private void spawnWave(int kind) {
        float rt = ramp();
        int n = Wave.size(kind, diff, rt);
        float[] lanes = Wave.lanes(kind, n);
        float[] dels = Wave.delays(kind, n);
        int type = Wave.typeFor(kind, rt);
        int live = 0;
        for (int i = 0; i < n; i++) {
            if (lanes[i] < 0f) continue;                 // the gap in a wall: nobody spawns there
            // the lane is resolved to pixels here: [r, W-r] with a dp(30) margin, so no member
            // can spawn off screen, and the existing delayed-spawn queue carries it
            float x = dp(30) + lanes[i] * (W - dp(60));
            pending.add(new float[]{dels[i], type, x});
            live++;
        }
        wavesSent++;
        Log.i(TAG, "wave kind=" + Wave.NAME[kind] + " n=" + live + " type=" + type + " total=" + wavesSent);
    }

    /** one new-type wave: guarantees the four new enemies are on screen, for the field test */
    private void spawnNewTypeWave() {
        newTypeSeq++;
        int type = Wave.newTypeFor(newTypeSeq, ramp());
        spawnEnemy(type, -1);
    }

    int newTypeSeq;

    /** every clear-bullets entry point goes through here, so mines never outlive a bomb */
    private int clearEnemyBullets() {
        int mines = 0;
        for (Bullet b : ebullets) if (b.mine) mines++;
        ebullets.clear();
        if (mines > 0) Log.i(TAG, "clear mines=" + mines);
        return mines;
    }

    private void spawnPower(int kind, float x, float y) {
        PowerUp p = new PowerUp();
        p.kind = kind;
        p.r = dp(16);
        p.x = x;
        p.y = y;
        p.vx = (rnd.nextFloat() * 2 - 1) * dp(30);
        p.vy = dp(85);
        p.t = 0;
        powers.add(p);
    }

    private void spawnRandomPower(float x, float y) {
        double r = rnd.nextDouble();
        int k = r < 0.30 ? PW_WEAPON : r < 0.48 ? PW_SHIELD : r < 0.62 ? PW_HEAL
                : r < 0.72 ? PW_SCORE : r < 0.84 ? PW_RAPID : r < 0.92 ? PW_BOMB
                : r < 0.96 ? PW_LASER : PW_HOMING;
        spawnPower(k, x, y);
    }

    // ------------------------------------------------------------------ events

    /** k < 0 = random. Every event has its own banner, sound, haptics and screen effect. */
    private void triggerEvent(int k) {
        eventTimer = (20f + rnd.nextFloat() * 12f) * D_EVENT[diff];
        if (k < 0) {
            double r = rnd.nextDouble();
            if (time < 40) k = r < 0.45 ? 0 : (r < 0.75 ? 1 : 3);
            else if (time < 100) k = r < 0.34 ? 0 : (r < 0.58 ? 1 : (r < 0.78 ? 3 : 4));
            else k = r < 0.28 ? 0 : (r < 0.48 ? 1 : (r < 0.66 ? 3 : (r < 0.84 ? 4 : 5)));
        }
        eventsSeen++;
        audio.play(AudioFx.EVENT);
        vibro.event();
        flashCyan = 0.5f;
        shake = Math.max(shake, 6f);
        switch (k) {
            case 0: { // enemy wave in a V formation
                int n = (int) ((6 + rnd.nextInt(5) + Math.min(6, (int) (time / 60))) * D_NUM[diff]);
                banner("敌袭！", "敌方编队接近");
                for (int i = 0; i < n; i++) {
                    float lane = W * (0.18f + 0.64f * (n == 1 ? 0.5f : (float) i / (n - 1)));
                    pending.add(new float[]{i * 0.16f, i % 3 == 2 ? 1 : 0, lane});
                }
                break;
            }
            case 1: // frenzy: double fire rate
                frenzyT = 9f;
                banner("狂暴模式！", "射速翻倍 9 秒");
                flashRed = 0.45f;
                audio.play(AudioFx.POWER, 0.8f);
                break;
            case 2: // EMP
                banner("磁暴！", "全屏幕电磁冲击");
                bomb(true);
                break;
            case 3: // power-up rain
                banner("宝箱雨！", "收集道具");
                for (int i = 0; i < 7; i++)
                    pending.add(new float[]{i * 0.22f, -1, dp(40) + rnd.nextFloat() * (W - dp(80))});
                break;
            case 4: // slow field
                slowT = 7f;
                banner("缓速场！", "敌机速度下降");
                flashCyan = 0.8f;
                break;
            case 5: // heavy bomber
                banner("巨型机来袭！", "集中火力");
                pending.add(new float[]{0.6f, 4, W * (0.3f + rnd.nextFloat() * 0.4f)});
                break;
            default:
                banner("敌袭！", "敌方编队接近");
                break;
        }
        Log.i(TAG, "event " + k + " " + banner);
    }

    /** pending entries with type -1 are power-ups */
    private void spawnPendingPower(float x) {
        spawnRandomPower(x, -dp(30));
    }

    private void banner(String s, String sub) { banner(s, sub, 1); }

    /** banner priority: a streak must never overwrite a boss entrance (test.sh reads the name off screen) */
    private void banner(String s, String sub, int pri) {
        if (bannerT > 0 && pri < bannerPri) {
            Log.i(TAG, "banner dropped (lower priority): " + s);
            return;
        }
        bannerPri = pri;
        banner = s;
        bannerSub = sub;
        bannerT = 2.1f;
    }

    // ------------------------------------------------------------------ movement

    private void updateBg(float dt) {
        bgScroll += dt * (state == RUNNING ? 1f : 0.45f);
        if (stars == null) return;
        float base = state == RUNNING ? 150f : 60f;
        for (Star s : stars) {
            s.y += base * s.z * dp * dt;
            if (s.y > H + 4) {
                s.y = -4;
                s.x = rnd.nextFloat() * W;
                s.z = 0.25f + rnd.nextFloat() * rnd.nextFloat() * 1.1f;
            }
        }
    }

    private void moveBullets(float dt) {
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            if (b.homing) steer(b, dt);
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            b.life -= dt;
            if (b.life <= 0 || b.y < -dp(30) || b.y > H + dp(30) || b.x < -dp(30) || b.x > W + dp(30))
                bullets.remove(i);
        }
        float slow = slowT > 0 ? 0.45f : 1f;
        for (int i = ebullets.size() - 1; i >= 0; i--) {
            Bullet b = ebullets.get(i);
            b.x += b.vx * dt * slow;
            b.y += b.vy * dt * slow;
            b.life -= dt;
            if (b.grazeT > 0f) b.grazeT -= dt;
            if (b.life <= 0 || b.y < -dp(40) || b.y > H + dp(40) || b.x < -dp(40) || b.x > W + dp(40))
                ebullets.remove(i);
        }
    }

    private void moveEnemies(float dt) {
        float slow = slowT > 0 ? 0.45f : 1f;
        if (freezeT > 0) slow = 0f;              // 冰封万里: everything stops in place
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            e.t += dt * slow;
            if (e.flash > 0) e.flash--;
            switch (e.type) {
                case 1: // darter dives at the player after entering
                    if (e.t > 0.7f) {
                        float a = (float) Math.atan2(sx - e.x, sy - e.y);
                        float sp = dp(300);
                        e.vx = (float) Math.sin(a) * sp;
                        e.vy = (float) Math.cos(a) * sp;
                    }
                    break;
                case 2: // gunship: settle at the top third, strafe
                    if (e.y > H * 0.24f) {
                        e.hover = 1;
                        e.vy = 0;
                    }
                    if (e.hover == 1) {
                        if (e.x < dp(40) || e.x > W - dp(40)) e.vx = -e.vx;
                        e.vy = (float) Math.sin(e.t * 1.3f) * dp(24);
                    }
                    e.fire -= dt * slow;
                    if (e.fire <= 0) {
                        e.fire = ef(1.35f);
                        enemyShot(e, (float) Math.atan2(sx - e.x, sy - e.y), dp(270));
                    }
                    break;
                case 3: // elite: sine descent + spread
                    e.vx = (float) Math.cos(e.t * 1.6f + e.phase) * dp(150);
                    e.fire -= dt * slow;
                    if (e.fire <= 0) {
                        e.fire = ef(1.9f);
                        float base = (float) Math.atan2(sx - e.x, sy - e.y);
                        for (int k = -1; k <= 1; k++) enemyShot(e, base + k * 0.26f, dp(250));
                    }
                    break;
                case 5: // splitter: drifts down in a wide weave, no shots - the danger is the split
                    e.vx = (float) Math.sin(e.t * 2.4f + e.phase) * dp(110);
                    break;
                case 6: // miner: parks in the upper third and drops hanging mines
                    if (e.y > H * 0.18f) { e.hover = 1; e.vy = 0; }
                    if (e.fire > 0.9f) e.fire = 0.9f;          // first mine lands fast, before the autopilot deletes it
                    if (e.hover == 1) e.vx = (float) Math.sin(e.t * 0.9f) * dp(80);
                    e.fire -= dt * slow;
                    if (e.fire <= 0) {
                        e.fire = ef(1.7f);
                        dropMine(e);
                    }
                    break;
                case 7: // sniper: 0.7s red telegraph locked on the player, then one fast shot
                    if (e.y > H * 0.25f) { e.hover = 1; e.vy = 0; }
                    if (e.fire > 0.9f) e.fire = 0.9f;          // the telegraph must start while it is still alive
                    e.fire -= dt * slow;
                    if (e.aimT <= 0f && e.fire <= 0.7f) {
                        e.aimT = 0.7f;
                        e.aimX = sx;                       // locked once: a tracking telegraph is undodgeable
                        e.aimY = sy;
                        Log.i(TAG, "sniper aim telegraph");
                    }
                    if (e.aimT > 0f) e.aimT -= dt * slow;
                    if (e.fire <= 0f) {
                        e.fire = ef(2.4f);
                        enemyShot(e, (float) Math.atan2(e.aimX - e.x, e.aimY - e.y), dp(520));
                        sniperShots++;
                        Log.i(TAG, "sniper aim=" + sniperShots);
                    }
                    break;
                case 8: // mothership: parks at the top and keeps producing grunts
                    if (e.y > H * 0.14f) { e.hover = 1; e.vy = 0; }
                    if (e.fire > 0.7f) e.fire = 0.7f;
                    if (e.hover == 1) e.vx = (float) Math.sin(e.t * 0.5f) * dp(60);
                    e.fire -= dt * slow;
                    if (e.fire <= 0) {
                        e.fire = ef(2.6f);
                        // only a live boss reserves summon slots; otherwise the mothership is
                        // allowed to fill the field, which is its whole identity
                        int cap = bossAlive() ? D_CAP[diff] - 6 : D_CAP[diff];
                        if (enemies.size() < cap) {
                            spawnEnemy(0, e.x + (rnd.nextFloat() * 2 - 1) * dp(30));
                            motherSpawns++;
                            Log.i(TAG, "mother spawn n=" + motherSpawns);
                        } else if (motherBlocks < 3) {
                            motherBlocks++;
                            Log.i(TAG, "mother blocked room=" + (cap - enemies.size()) + " cap=" + cap);
                        }
                    }
                    break;
                case 4: // heavy: slow descent, radial bursts
                    if (e.y > H * 0.26f) {
                        e.hover = 1;
                        e.vy = 0;
                    }
                    if (e.hover == 1) e.vx = (float) Math.sin(e.t * 0.7f) * dp(70);
                    e.fire -= dt * slow;
                    if (e.fire <= 0) {
                        e.fire = ef(2.1f);
                        int n = 14;
                        for (int k = 0; k < n; k++)
                            enemyShot(e, (float) (k * 2 * Math.PI / n) + e.t, dp(210));
                        shake = Math.max(shake, 4f);
                    }
                    break;
                default: // grunt
                    e.vx = (float) Math.sin(e.t * 1.9f + e.phase) * dp(80);
                    break;
            }
            e.x += e.vx * dt * slow;
            e.y += e.vy * dt * slow;
            if (e.x < e.r) { e.x = e.r; e.vx = Math.abs(e.vx); }
            if (e.x > W - e.r) { e.x = W - e.r; e.vx = -Math.abs(e.vx); }
            if (e.y > H + dp(70)) enemies.remove(i);
        }
    }

    int sniperShots, motherSpawns, minesDropped, splits, motherBlocks, splitBlocks;
    // polish: hit-stop, streak banners, edge danger arrows
    float hitStop, hitStopBudget = Tuning.HIT_STOP_BUDGET;
    int streaks, dangers, hitStops;
    int bannerPri;
    float realDt;

    /** a splitter breaks into two grunts; capped, and the children never split again */
    private void splitEnemy(Enemy e) {
        int room = D_CAP[diff] - enemies.size();
        if (room < 2) {
            // the field is full: the split is skipped on purpose, and the log says so, so a
            // missing "split n=" line is never confused with a broken splitter
            if (splitBlocks < 3) {
                splitBlocks++;
                Log.i(TAG, "split blocked room=" + room + " cap=" + D_CAP[diff]);
            }
            return;
        }
        for (int k = 0; k < 2; k++) {
            Enemy c = new Enemy();
            c.type = 0;                       // grunt: children do not split, so there is no recursion
            c.r = dp(11);
            c.hp = c.maxhp = Math.max(1f, e.maxhp * 0.25f);
            c.x = e.x + (k == 0 ? -dp(18) : dp(18));
            c.y = e.y;
            c.vx = (k == 0 ? -1 : 1) * dp(120);
            c.vy = e.vy * 0.9f;
            c.score = 60;
            c.color = Color.rgb(190, 255, 170);
            c.t = 0f;
            enemies.add(c);
        }
        splits++;
        Log.i(TAG, "split n=2 total=" + splits);
    }

    /** a mine hangs where it was dropped: it is an enemy bullet, so every clear-bullets path clears it */
    private void dropMine(Enemy e) {
        Bullet b = new Bullet();
        b.x = e.x;
        b.y = e.y + e.r;
        b.vx = 0;
        b.vy = dp(26);
        b.r = dp(9);
        b.dmg = 1;
        b.life = 9f;
        b.color = Color.rgb(255, 200, 90);
        b.enemy = true;
        b.mine = true;
        ebullets.add(b);
        minesDropped++;
        Log.i(TAG, "mine n=" + minesDropped);
    }

    private void enemyShot(Enemy e, float a, float sp) {
        Bullet b = new Bullet();
        b.x = e.x;
        b.y = e.y + e.r * 0.6f;
        b.vx = (float) Math.sin(a) * sp * D_SPEED[diff];
        b.vy = (float) Math.cos(a) * sp * D_SPEED[diff];
        b.r = e.type == 4 ? dp(7) : dp(5);
        b.dmg = 1;
        b.life = 6f;
        b.color = e.type == 4 ? Color.rgb(255, 130, 210) : Color.rgb(255, 120, 120);
        b.enemy = true;
        ebullets.add(b);
        audio.play(AudioFx.WALL, 0.7f);
    }

    private void movePowers(float dt) {
        for (int i = powers.size() - 1; i >= 0; i--) {
            PowerUp p = powers.get(i);
            p.t += dt;
            // magnet: drift toward the plane when close
            float dx = sx - p.x, dy = sy - p.y;
            float d = (float) Math.hypot(dx, dy);
            if (d < dp(150) && d > 1) {
                float pull = dp(420) * (1f - d / dp(150));
                p.vx += dx / d * pull * dt * 3f;
                p.vy += dy / d * pull * dt * 3f;
            }
            p.vx *= (float) Math.exp(-1.6 * dt);
            p.vy += (dp(60) - p.vy) * dt * 1.5f;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.x < p.r) { p.x = p.r; p.vx = Math.abs(p.vx); }
            if (p.x > W - p.r) { p.x = W - p.r; p.vx = -Math.abs(p.vx); }
            if (p.y > H + dp(50)) powers.remove(i);
        }
    }

    // ------------------------------------------------------------------ collisions

    private void collisions() {
        float sr = dp(13);   // player hit box is smaller than the sprite - forgiving

        // player bullets -> enemies
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            for (int j = enemies.size() - 1; j >= 0; j--) {
                Enemy e = enemies.get(j);
                float dx = b.x - e.x, dy = b.y - e.y;
                if (dx * dx + dy * dy < (e.r + b.r) * (e.r + b.r)) {
                    bullets.remove(i);
                    e.hp -= b.dmg;
                    e.flash = 3;
                    if (e.hp <= 0) {
                        killEnemy(e, j);
                    } else {
                        audio.play(AudioFx.HIT, 1.3f + rnd.nextFloat() * 0.3f);
                        for (int k = 0; k < 3; k++) spark(b.x, b.y, e.color, dp(140));
                    }
                    break;
                }
            }
        }

        // power-ups -> player
        for (int i = powers.size() - 1; i >= 0; i--) {
            PowerUp p = powers.get(i);
            float dx = p.x - sx, dy = p.y - sy;
            if (dx * dx + dy * dy < (p.r + sr + dp(6)) * (p.r + sr + dp(6))) {
                powers.remove(i);
                collect(p);
            }
        }

        if (invuln > 0 || state != RUNNING) return;

        // player bullets -> boss (once it has finished its entrance, so the intro is safe)
        if (boss != null && bossEnter > 1.2f) {
            float br = bossR();
            for (int i = bullets.size() - 1; i >= 0 && boss != null; i--) {
                Bullet b = bullets.get(i);
                float dx = b.x - bossX, dy = b.y - bossY;
                if (dx * dx + dy * dy < (br + b.r) * (br + b.r)) {
                    bullets.remove(i);
                    hitBoss(b);
                }
            }
        }

        // enemy bullets -> player
        float grazeBand = dp(Tuning.GRAZE_RADIUS_DP);
        for (int i = ebullets.size() - 1; i >= 0; i--) {
            Bullet b = ebullets.get(i);
            float dx = b.x - sx, dy = b.y - sy;
            if (dx * dx + dy * dy < (b.r + sr) * (b.r + sr)) {
                ebullets.remove(i);
                for (int k = 0; k < 6; k++) spark(b.x, b.y, b.color, dp(160));
                damagePlayer();
                return;
            }
            // not a hit - if it just brushed past, charge up. Checked after the hit test on
            // purpose: taking a hit must never also pay out a graze bonus.
            if (b.grazeT <= 0f && Tuning.grazes(dx, dy, b.r + sr, grazeBand)) {
                b.grazeT = Tuning.GRAZE_COOLDOWN;
                grazeHit(b.x, b.y, b.color, false);
            }
        }

        // enemies -> player
        for (int j = enemies.size() - 1; j >= 0; j--) {
            Enemy e = enemies.get(j);
            float dx = e.x - sx, dy = e.y - sy;
            float rr = e.r + sr;
            if (dx * dx + dy * dy < rr * rr) {
                e.hp -= 6;
                e.flash = 4;
                if (e.hp <= 0) killEnemy(e, j);
                damagePlayer();
                return;
            }
        }
        // the boss hull blocks the player: ramming it costs a life
        if (boss != null && bossEnter > 1.5f) {
            float dx = bossX - sx, dy = bossY - sy;
            float rr = bossR() * 0.7f + sr;
            if (dx * dx + dy * dy < rr * rr) damagePlayer();
        }
    }

    private void killEnemy(Enemy e, int index) {
        enemies.remove(index);
        if (e.type == Wave.SPLITTER) splitEnemy(e);
        kills++;
        combo++;
        String streak = Tuning.streakBanner(combo);
        if (streak != null) {
            banner(streak, "x" + combo + " 连杀", 1);
            audio.play(AudioFx.STREAK, 1f);
            streaks++;
            Log.i(TAG, "streak n=" + combo);
        }
        float hs = Tuning.hitStopFor(e.type, false);
        if (hs > 0f && hitStopBudget >= hs) {
            hitStop = hs;
            hitStops++;
            Log.i(TAG, "hitstop n=" + hitStops + " t=" + hs);
        }
        if (combo > maxCombo) maxCombo = combo;
        comboT = 2.4f;
        checkFever();
        energy = Math.min(Tuning.ENERGY_MAX, energy + Tuning.killEnergy(e.type));
        float mult = comboMult();
        int pts = (int) (e.score * mult * D_SCORE[diff]);
        scoreF += pts;
        floatText(e.x, e.y, "+" + pts, e.color, dp(15));

        boolean big = e.type >= 2;
        boolean vibe = vibGate(big ? 0.18f : 0.13f);
        explode(e.x, e.y, e.color, big ? 34 : 16, e.r);
        ring(e.x, e.y, e.color, e.r * (big ? 5f : 2.6f), big ? dp(9) : dp(5));
        audio.play(big ? AudioFx.BIGKILL : AudioFx.KILL, e.type == 4 ? 0.8f : 1f);
        if (big) {
            if (vibe) vibro.bigKill();
            shake = Math.max(shake, e.type == 4 ? 16f : 8f);
            flashWhite = Math.max(flashWhite, e.type == 4 ? 0.5f : 0.2f);
            if (e.type == 4) bombSlow = 0.45f;
        } else {
            if (vibe) vibro.kill();
            shake = Math.max(shake, 3f);
        }
        if (combo > 1 && combo % 5 == 0)
            floatText(sx, sy - dp(46), "连击 x" + combo, GOLD, dp(18));

        // drops
        double r = rnd.nextDouble();
        if (e.type == 4) {
            spawnPower(PW_WEAPON, e.x - dp(24), e.y);
            spawnPower(PW_BOMB, e.x + dp(24), e.y);
        } else if (e.type == 2 || e.type == 3) {
            if (r < 0.55 * D_DROP[diff]) spawnRandomPower(e.x, e.y);
        } else if (r < 0.11 * D_DROP[diff]) {
            spawnRandomPower(e.x, e.y);
        }
    }

    float comboMult() {
        float m = 1f + Math.min(combo, 40) * 0.05f;
        return feverActive() ? m * Tuning.FEVER_SCORE_MULT : m;
    }

    private void collect(PowerUp p) {
        audio.play(AudioFx.POWER);
        vibro.power();
        flashCyan = 0.45f;
        ring(p.x, p.y, powerColor(p.kind), dp(70), dp(5));
        for (int k = 0; k < 14; k++) spark(p.x, p.y, powerColor(p.kind), dp(200));
        switch (p.kind) {
            case PW_WEAPON:
                weapon = Math.min(MAX_WEAPON, weapon + 1);
                floatText(sx, sy - dp(40), "火力 LV" + weapon, powerColor(PW_WEAPON), dp(19));
                break;
            case PW_SHIELD:
                shield = Math.min(3, shield + 1);
                floatText(sx, sy - dp(40), "护盾 x" + shield, powerColor(PW_SHIELD), dp(19));
                break;
            case PW_HEAL:
                lives = Math.min(5, lives + 1);
                floatText(sx, sy - dp(40), "生命 +1", powerColor(PW_HEAL), dp(19));
                break;
            case PW_BOMB:
                if (bombStock < Tuning.BOMB_MAX) {
                    bombStock++;
                    floatText(sx, sy - dp(40), "炸弹 +1 (" + bombStock + ")", powerColor(PW_BOMB), dp(20));
                } else {
                    scoreF += Tuning.BOMB_FULL_SCORE;
                    floatText(sx, sy - dp(40), "炸弹已满 +" + Tuning.BOMB_FULL_SCORE,
                            powerColor(PW_SCORE), dp(18));
                }
                break;
            case PW_SCORE: {
                int pts = (int) (600 * comboMult());
                scoreF += pts;
                floatText(sx, sy - dp(40), "+" + pts, powerColor(PW_SCORE), dp(20));
                break;
            }
            case PW_LASER:
                laserT = Weapon.LASER_SEC;
                audio.play(AudioFx.LASER, 1f);
                floatText(sx, sy - dp(40), "激光 " + (int) Weapon.LASER_SEC + " 秒", powerColor(PW_LASER), dp(19));
                Log.i(TAG, "pup kind=laser t=" + Weapon.LASER_SEC);
                break;
            case PW_HOMING:
                homingT = Weapon.HOMING_SEC;
                // 追踪弹挂在 LV3 以上的炮台上；拾取时直接解锁，否则道具在低火力下是空报
                if (weapon < 3) weapon = 3;
                audio.play(AudioFx.MISSILE, 1f);
                floatText(sx, sy - dp(40), "追踪 " + (int) Weapon.HOMING_SEC + " 秒", powerColor(PW_HOMING), dp(19));
                Log.i(TAG, "pup kind=homing t=" + Weapon.HOMING_SEC);
                break;
            default:
                rapidT = 9f;
                floatText(sx, sy - dp(40), "速射 9 秒", powerColor(PW_RAPID), dp(19));
                break;
        }
    }

    // ------------------------------------------------------------------ active skills

    /**
     * A bullet brushed past the ship: the risk/reward loop. Charges energy, feeds the combo
     * (and through it fever), pays a little score. Collisions and the test hook both come
     * through here, so the hook cannot fake a graze the player could not get.
     */
    void grazeHit(float x, float y, int color, boolean hook) {
        if (hook) grazeHooked++;
        else graze++;
        combo++;
        if (combo > maxCombo) maxCombo = combo;
        comboT = 2.4f;
        energy = Math.min(Tuning.ENERGY_MAX, energy + Tuning.GRAZE_ENERGY);
        scoreF += 12f * comboMult();
        spark(x, y, color, dp(90));
        floatText(sx, sy - dp(34), "擦弹", Color.rgb(120, 240, 255), dp(13));
        audio.play(AudioFx.GRAZE, 0.55f);
        if (hook) {
            Log.i(TAG, "grazeHook n=" + grazeHooked);
        } else if (graze == 1 || graze == 10 || graze % 25 == 0) {
            Log.i(TAG, "graze n=" + graze + " combo=" + combo + " energy=" + (int) energy);
        }
        checkFever();
    }

    /** fever is entered on a rising edge only, so the banner does not spam every kill */
    void checkFever() {
        boolean now = feverActive();
        if (now && !feverWas) {
            banner("连击狂热", "得分 x2 · 射速提升");
            audio.play(AudioFx.EVENT, 0.8f);
            floatText(sx, sy - dp(58), "狂热!", GOLD, dp(20));
        }
        feverWas = now;
    }

    /** true when the combo is deep enough to be in fever (score x2, faster guns) */
    boolean feverActive() {
        return combo >= Tuning.FEVER_COMBO;
    }

    /**
     * The super blast: spends a full energy bar, picks one of five themes and shreds the
     * screen. Called by the HUD button, the test hook and the autopilot - one code path.
     */
    void fireSuper() {
        if (state != RUNNING) return;
        if (energy < Tuning.ENERGY_MAX) {
            floatText(sx, sy - dp(40), "能量不足", TXT_DIM, dp(15));
            audio.play(AudioFx.PICK, 0.7f);
            return;
        }
        energy = 0f;
        superCount++;
        int theme = superThemeForce >= 0 ? superThemeForce : Super.pick(rnd);
        superTheme = theme;
        superName = Super.name(theme);
        superFxT = 1.1f;
        float dmg = Super.damage(theme, diff);
        banner("超必杀 · " + superName, Super.SUB[theme]);
        audio.play(AudioFx.SUPER);
        vibro.bomb();
        flashWhite = 0.7f;
        flashCyan = 0.6f;
        shake = Math.max(shake, 18f);
        if (Super.clearsBullets(theme)) clearEnemyBullets();

        int killed = 0;
        switch (theme) {
            case Super.METEOR: {
                // three meteor impacts on random enemies
                for (int i = 0; i < Super.impacts(theme); i++) {
                    Enemy t = enemies.isEmpty() ? null
                            : enemies.get(rnd.nextInt(enemies.size()));
                    float x = t != null ? t.x : W * (0.2f + rnd.nextFloat() * 0.6f);
                    float y = t != null ? t.y : H * 0.3f;
                    killed += blast(x, y, dp(Super.radiusDp(theme)), dmg,
                            Color.rgb(255, 130, 50));
                }
                break;
            }
            case Super.LIGHTNING: {
                // six vertical bolt columns
                for (int i = 0; i < Super.impacts(theme); i++) {
                    float x = dp(20) + rnd.nextFloat() * (W - dp(40));
                    boltX[i] = x;
                    boltT[i] = 0.45f;
                    killed += boltHit(x, dp(Super.radiusDp(theme)), dmg);
                }
                shake = Math.max(shake, 22f);
                break;
            }
            case Super.HOLY: {
                killed += damageAll(dmg);
                if (Super.heals(theme) && lives < 5) {
                    lives++;
                    floatText(sx, sy - dp(58), "生命 +1", powerColor(PW_HEAL), dp(19));
                }
                flashWhite = 1f;
                break;
            }
            case Super.QUAKE: {
                // the wave travels up the screen and cuts everything it passes (updateQuake)
                quakeOn = true;
                quakeY = H + dp(20);
                for (Enemy e : enemies) e.swept = false;
                break;
            }
            default: {
                killed += damageAll(dmg);
                if (Super.freezes(theme)) {
                    freezeT = Super.freezeSeconds();
                    flashCyan = 1f;
                }
                break;
            }
        }
        float pts = Tuning.superScore(diff) * (1f + killed * 0.25f);
        scoreF += pts;
        floatText(sx, sy - dp(76), "+" + (int) pts, Super.color(theme), dp(22));
        Log.i(TAG, "super name=" + superName + " theme=" + theme + " killed=" + killed
                + " dmg=" + (int) dmg + " n=" + superCount);
    }

    /** player bomb: spends one bomb from the stock (the EMP event keeps its own path) */
    void useBomb() {
        if (state != RUNNING) return;
        if (bombStock <= 0) {
            floatText(sx, sy - dp(40), "没有炸弹", TXT_DIM, dp(15));
            audio.play(AudioFx.PICK, 0.7f);
            return;
        }
        bombStock--;
        bombFired++;
        bomb(false);
        Log.i(TAG, "bomb n=" + bombStock + " fired=" + bombFired + " enemies=" + enemies.size());
    }

    /** the autopilot uses the same skills, so the automated playtest covers them */
    private void autoSkill(float dt) {
        if (autoSkillT > 0) {
            autoSkillT -= dt;
            return;
        }
        // do not waste a charge on an empty screen - that is also what a player would do
        // a boss is worth a charge even on a sparse screen - that is what the skill is for
        if (energy >= Tuning.ENERGY_MAX && (enemies.size() >= 2 || boss != null)) {
            fireSuper();
            autoSkillT = Tuning.AUTO_SKILL_GAP;
        } else if (bombStock > 0 && (enemies.size() >= Tuning.AUTO_BOMB_ENEMIES || boss != null)) {
            useBomb();
            autoSkillT = Tuning.AUTO_SKILL_GAP * 2f;
        }
    }

    /** damage every enemy within r of (x,y); @return how many died */
    private int blast(float x, float y, float r, float dmg, int col) {
        int killed = 0;
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            float dx = e.x - x, dy = e.y - y;
            if (dx * dx + dy * dy > r * r) continue;
            e.hp -= dmg;
            e.flash = 5;
            if (e.hp <= 0) { killEnemy(e, i); killed++; }
        }
        ring(x, y, col, r * 1.6f, dp(9));
        ring(x, y, Color.WHITE, r * 0.9f, dp(5));
        explode(x, y, col, 20, r * 0.5f);
        // skills must hit the boss too, or a super blast feels useless on a boss screen
        if (boss != null && bossEnter > 1.2f) {
            float dx = bossX - x, dy = bossY - y;
            if (dx * dx + dy * dy <= (r + bossR()) * (r + bossR())) boss.hurt(dmg);
        }
        return killed;
    }

    /** damage every enemy within a vertical strip at x (万雷齐鸣) */
    private int boltHit(float x, float r, float dmg) {
        int killed = 0;
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (Math.abs(e.x - x) > r + e.r) continue;
            e.hp -= dmg;
            e.flash = 5;
            if (e.hp <= 0) { killEnemy(e, i); killed++; }
        }
        return killed;
    }

    /** damage every enemy on screen (圣光普照 / 冰封万里) */
    private int damageAll(float dmg) {
        int killed = 0;
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            e.hp -= dmg;
            e.flash = 5;
            if (e.hp <= 0) { killEnemy(e, i); killed++; }
        }
        ring(sx, sy, Color.rgb(255, 250, 220), (float) Math.hypot(W, H) * 0.8f, dp(12));
        if (boss != null && bossEnter > 1.2f) boss.hurt(dmg);
        return killed;
    }

    /** 大地裂变: a wave sweeps from the bottom edge to the top, cutting what it passes */
    private void updateQuake(float dt) {
        quakeY -= dp(1500f) * dt;
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (e.swept || e.y < quakeY) continue;
            e.swept = true;
            e.hp -= Super.damage(Super.QUAKE, diff);
            e.flash = 5;
            if (e.hp <= 0) killEnemy(e, i);
        }
        if (rnd.nextFloat() < 0.9f) {
            float x = rnd.nextFloat() * W;
            spark(x, quakeY + (rnd.nextFloat() * 2 - 1) * dp(10), Color.rgb(220, 170, 100), dp(200));
        }
        if (quakeY < -dp(30)) quakeOn = false;
    }

    /** clears enemy bullets and shreds everything on screen (emp = stronger) */
    private void bomb(boolean emp) {
        audio.play(AudioFx.BOMB);
        vibro.bomb();
        flashWhite = 1f;
        shake = Math.max(shake, 20f);
        bombSlow = 0.5f;
        ring(sx, sy, Color.WHITE, (float) Math.hypot(W, H), dp(16));
        ring(sx, sy, Color.rgb(120, 220, 255), (float) Math.hypot(W, H) * 0.7f, dp(10));
        clearEnemyBullets();
        float dmg = emp ? 60f : Tuning.bombDamage(diff);
        for (int j = enemies.size() - 1; j >= 0; j--) {
            Enemy e = enemies.get(j);
            e.hp -= dmg;
            e.flash = 6;
            if (e.hp <= 0) killEnemy(e, j);
        }
    }

    private void damagePlayer() {
        if (godMode) {
            invuln = 0.6f;
            return;
        }
        if (shield > 0) {
            shield--;
            invuln = 1.1f;
            audio.play(AudioFx.SHIELD);
            vibro.shield();
            flashCyan = 0.8f;
            ring(sx, sy, Color.rgb(120, 220, 255), dp(90), dp(6));
            shake = Math.max(shake, 8f);
            return;
        }
        lives--;
        weapon = Math.max(1, weapon - 1);
        invuln = 2.2f;
        combo = 0;
        audio.play(AudioFx.HURT);
        vibro.hurt();
        flashRed = 0.9f;
        shake = Math.max(shake, 14f);
        for (int k = 0; k < 20; k++) spark(sx, sy, SHIP, dp(240));
        if (lives <= 0) gameOver();
    }

    private void gameOver() {
        state = OVER;
        deathT = 0;
        score = (int) scoreF;
        audio.stopMusic();
        audio.play(AudioFx.OVER);
        vibro.gameOver();
        explode(sx, sy, SHIP, 46, dp(40));
        ring(sx, sy, Color.WHITE, dp(260), dp(12));
        flashWhite = 0.9f;
        flashRed = 1f;
        shake = 24f;
        vignette = 1f;
        final int rank = Scores.submit(getContext(), Scores.lastName(getContext()),
                score, kills, (int) time, diff);
        Log.i(TAG, "gameover score=" + score + " kills=" + kills + " time=" + (int) time
                + " rank=" + rank + " events=" + eventsSeen + " diff=" + DIFF_NAME[diff]
                + " superFails=" + superFails + " graze=" + graze + " missile=" + missilesFired
                + " laser=" + laserHits + " wave=" + wavesSent + " split=" + splits
                + " mine=" + minesDropped + " sniper=" + sniperShots + " mother=" + motherSpawns
                + " streak=" + streaks + " hitstop=" + hitStops + " danger=" + dangers
                + " grazeHook=" + grazeHooked);
        if (rank >= 0) {
            ui.postDelayed(new Runnable() {
                @Override public void run() { promptName(rank); }
            }, 900);
        }
    }

    private void promptName(final int index) {
        final Activity a = getContext() instanceof Activity ? (Activity) getContext() : null;
        if (a == null || state != OVER) return;
        final EditText et = new EditText(a);
        et.setText(Scores.lastName(a));
        et.setSelectAllOnFocus(true);
        et.setSingleLine(true);
        new AlertDialog.Builder(a)
                .setTitle("上榜！第 " + (index + 1) + " 名")
                .setMessage("得分 " + score)
                .setView(et)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Scores.rename(getContext(), index, et.getText().toString());
                        d.dismiss();
                    }
                })
                .setNegativeButton("跳过", null)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override public void onCancel(DialogInterface d) { }
                })
                .show();
    }

    // ------------------------------------------------------------------ particles

    private void spark(float x, float y, int color, float speed) {
        Particle p = new Particle();
        p.kind = 0;
        double a = rnd.nextFloat() * (2 * Math.PI);
        float s = speed * (0.35f + rnd.nextFloat() * 0.9f);
        p.x = x;
        p.y = y;
        p.vx = (float) Math.cos(a) * s;
        p.vy = (float) Math.sin(a) * s;
        p.life = p.max = 0.22f + rnd.nextFloat() * 0.34f;
        p.size = dp(2) + rnd.nextFloat() * dp(2.6f);
        p.color = color;
        parts.add(p);
    }

    private void explode(float x, float y, int color, int n, float r) {
        for (int i = 0; i < n; i++) spark(x, y, color, dp(230));
        for (int i = 0; i < n / 3; i++) {
            Particle p = new Particle();
            p.kind = 1;
            double a = rnd.nextFloat() * (2 * Math.PI);
            p.x = x + (float) Math.cos(a) * r * 0.4f;
            p.y = y + (float) Math.sin(a) * r * 0.4f;
            p.vx = (float) Math.cos(a) * dp(60);
            p.vy = (float) Math.sin(a) * dp(60);
            p.life = p.max = 0.5f + rnd.nextFloat() * 0.5f;
            p.size = r * (0.5f + rnd.nextFloat() * 0.7f);
            p.color = Color.rgb(255, 190, 120);
            parts.add(p);
        }
        for (int i = 0; i < 5; i++) {
            Particle p = new Particle();
            p.kind = 3;
            double a = rnd.nextFloat() * (2 * Math.PI);
            p.x = x;
            p.y = y;
            p.vx = (float) Math.cos(a) * dp(200);
            p.vy = (float) Math.sin(a) * dp(200);
            p.rot = rnd.nextFloat() * (float) Math.PI;
            p.vr = (rnd.nextFloat() * 2 - 1) * 9f;
            p.life = p.max = 0.6f + rnd.nextFloat() * 0.4f;
            p.size = r * 0.22f;
            p.color = color;
            parts.add(p);
        }
    }

    private void ring(float x, float y, int color, float r, float thick) {
        Particle p = new Particle();
        p.kind = 2;
        p.x = x;
        p.y = y;
        p.size = r;
        p.life = p.max = 0.45f;
        p.color = color;
        p.vx = thick;
        parts.add(p);
    }

    private void floatText(float x, float y, String s, int color, float size) {
        FloatText t = new FloatText();
        t.x = x;
        t.y = y;
        t.s = s;
        t.color = color;
        t.size = size;
        t.vy = -dp(46);
        t.life = 0.95f;
        texts.add(t);
    }

    private void updateParticles(float dt) {
        while (parts.size() > 900) parts.remove(0);
        while (texts.size() > 24) texts.remove(0);
        for (int i = parts.size() - 1; i >= 0; i--) {
            Particle p = parts.get(i);
            p.life -= dt;
            if (p.life <= 0) {
                parts.remove(i);
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.rot += p.vr * dt;
            if (p.kind == 0) {
                p.vx *= (float) Math.exp(-2.4 * dt);
                p.vy = p.vy * (float) Math.exp(-2.4 * dt) + dp(120) * dt;
            } else if (p.kind == 1) {
                p.vx *= (float) Math.exp(-1.2 * dt);
                p.vy = p.vy * (float) Math.exp(-1.2 * dt) - dp(10) * dt;
                p.size += dp(30) * dt;
            } else if (p.kind == 3) {
                p.vy += dp(160) * dt;
            }
        }
        for (int i = texts.size() - 1; i >= 0; i--) {
            FloatText t = texts.get(i);
            t.t += dt;
            t.y += t.vy * dt;
            t.vy *= (float) Math.exp(-1.6 * dt);
            if (t.t >= t.life) texts.remove(i);
        }
    }

    // ------------------------------------------------------------------ render

    final java.util.HashMap<Integer, RadialGradient> glowCache =
            new java.util.HashMap<Integer, RadialGradient>();
    LinearGradient bgGrad;
    RadialGradient nebA, nebB;

    void glow(Canvas c, float x, float y, float r, int color, float alpha) {
        if (r <= 0f) return;
        RadialGradient g = glowCache.get(color);
        if (g == null) {
            g = new RadialGradient(0, 0, 1, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP);
            glowCache.put(color, g);
        }
        paint.setShader(g);
        paint.setAlpha((int) (Math.max(0f, Math.min(1f, alpha)) * 255));
        c.save();
        c.translate(x, y);
        c.scale(r, r);
        c.drawCircle(0, 0, 1, paint);
        c.restore();
        paint.setAlpha(255);
        paint.setShader(null);
    }

    float text(Canvas c, String s, float x, float y, float size, int color, int align) {
        textPaint.setTextSize(size);
        textPaint.setColor(color);
        textPaint.setFakeBoldText(size >= dp(16));
        textPaint.setTextAlign(align == 1 ? Paint.Align.CENTER
                : align == 2 ? Paint.Align.RIGHT : Paint.Align.LEFT);
        c.drawText(s, x, y, textPaint);
        return size;
    }

    private void render(Canvas c) {
        if (W == 0) return;
        c.save();
        c.translate(shakeX, shakeY);
        drawBackground(c);
        drawPowers(c);
        drawEnemies(c);
        drawDangerArrows(c);
        drawBoss(c);
        drawBullets(c);
        drawParticles(c);
        drawSuperFx(c);
        if (state != OVER) drawShip(c);
        drawTexts(c);
        c.restore();

        // full screen flashes
        if (flashWhite > 0.01f) {
            paint.setColor(Color.WHITE);
            paint.setAlpha((int) (flashWhite * 190));
            c.drawRect(0, 0, W, H, paint);
            paint.setAlpha(255);
        }
        if (flashRed > 0.01f) {
            paint.setColor(Color.rgb(255, 40, 40));
            paint.setAlpha((int) (flashRed * 120));
            c.drawRect(0, 0, W, H, paint);
            paint.setAlpha(255);
        }
        if (flashCyan > 0.01f) {
            glow(c, sx, sy, dp(300), Color.rgb(120, 220, 255), flashCyan * 0.5f);
        }
        if (vignette > 0.01f) {
            paint.setColor(Color.BLACK);
            paint.setAlpha((int) (vignette * 140));
            c.drawRect(0, 0, W, H, paint);
            paint.setAlpha(255);
        }

        if (state == RUNNING || state == PAUSED) drawHud(c);
        drawBanner(c);
        if (state == TITLE) drawTitle(c);
        if (state == PAUSED) drawPause(c);
        if (state == OVER) drawOver(c);
    }

    // ------------------------------------------------------------------ background

    private void drawBackground(Canvas c) {
        if (bgGrad == null) {
            bgGrad = new LinearGradient(0, 0, 0, H, BG_TOP, BG_BOT, Shader.TileMode.CLAMP);
            nebA = new RadialGradient(0, 0, 1, NEB_A, NEB_A & 0x00FFFFFF, Shader.TileMode.CLAMP);
            nebB = new RadialGradient(0, 0, 1, NEB_B, NEB_B & 0x00FFFFFF, Shader.TileMode.CLAMP);
        }
        paint.setShader(bgGrad);
        c.drawRect(0, 0, W, H, paint);
        paint.setShader(null);

        // two slow nebula bands scrolling down = the vertical scroll feel
        float span = H * 1.1f;
        for (int i = 0; i < 3; i++) {
            float y = ((bgScroll * dp(22f) + i * span) % (span * 2f)) - span * 0.5f;
            glow(c, i % 2 == 0 ? W * 0.25f : W * 0.78f, y, W * 0.85f, i % 2 == 0 ? NEB_A : NEB_B, 0.55f);
        }

        // scrolling grid lines
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.rgb(60, 90, 140));
        float gap = dp(110);
        float off = (bgScroll * dp(60f)) % gap;
        for (float y = off - gap; y < H + gap; y += gap) {
            paint.setAlpha(38);
            c.drawLine(0, y, W, y, paint);
        }
        for (int i = 1; i < 5; i++) {
            paint.setAlpha(20);
            c.drawLine(W * i / 5f, 0, W * i / 5f, H, paint);
        }
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);

        // parallax stars
        if (stars != null) {
            for (Star s : stars) {
                int a = (int) (60 + s.z * 190);
                paint.setColor(Color.rgb(200, 220, 255));
                paint.setAlpha(Math.min(255, a));
                float r = dp(0.6f + s.z * 1.5f);
                c.drawRect(s.x, s.y, s.x + r, s.y + r * (1f + s.z * 3f), paint);
            }
            paint.setAlpha(255);
        }

        // speed streaks
        paint.setColor(Color.rgb(140, 190, 255));
        for (int i = 0; i < 10; i++) {
            float y = (bgScroll * dp(700f) + i * H / 10f) % (H + dp(120)) - dp(60);
            float x = ((i * 977) % 100) / 100f * W;
            paint.setAlpha(26);
            c.drawRect(x, y, x + dp(1.6f), y + dp(46), paint);
        }
        paint.setAlpha(255);
    }

    // ------------------------------------------------------------------ entities

    private void drawShip(Canvas c) {
        if (state == RUNNING || state == PAUSED) {
            float s = dp(16);
            float blink = invuln > 0 ? (0.45f + 0.55f * (float) Math.abs(Math.sin(time * 22f))) : 1f;
            glow(c, sx, sy, s * 2.6f,
                    frenzyT > 0 || feverActive() ? Color.rgb(255, 150, 70) : SHIP, 0.38f * blink);
            c.save();
            c.translate(sx, sy);
            c.rotate((float) Math.toDegrees(bank));
            paint.setAlpha((int) (255 * blink));

            // 分层矢量战机（Ships）：尾焰长度就是 fever / frenzy 的视觉反馈
            Ships.draw(c, paint, path, rect, ShipSpec.PLAYER, 0, 0, s, time, 0,
                    frenzyT > 0 ? 1.7f : feverActive() ? 1.45f : 1f, 0, 0);
            paint.setAlpha(255);
            c.restore();

            // shield bubbles
            if (shield > 0) {
                for (int i = 0; i < shield; i++) {
                    float rr = s * (1.9f + i * 0.28f) + (float) Math.sin(time * 3f + i) * dp(2);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(2));
                    paint.setColor(Color.rgb(120, 220, 255));
                    paint.setAlpha((int) (110 + 60 * Math.sin(time * 4f + i)));
                    c.drawCircle(sx, sy, rr, paint);
                    paint.setAlpha(255);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
        }
    }

    /** edge arrows for off-screen long-range threats, clamped inside the play area (DeX insets) */
    private void drawDangerArrows(Canvas c) {
        int n = 0;
        for (Enemy e : enemies) {
            if (e.type != Wave.SNIPER && e.type != Wave.MOTHER) continue;
            if (e.y >= topPad) continue;
            if (n++ >= Tuning.MAX_DANGER_ARROWS) break;
            float ax = Math.max(dp(20), Math.min(W - dp(20), e.x));
            float ay = topPad + dp(14);
            paint.setColor(Color.rgb(255, 90, 90));
            path.reset();
            path.moveTo(ax, ay + dp(10));
            path.lineTo(ax - dp(9), ay - dp(6));
            path.lineTo(ax + dp(9), ay - dp(6));
            path.close();
            c.drawPath(path, paint);
        }
        if (n > 0) {
            dangers += n;
            if (dangers % 20 < n) Log.i(TAG, "danger n=" + n + " total=" + dangers);
        }
    }

    private void drawEnemies(Canvas c) {
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            // 受击白闪与冰封万里都是整机色调，Ships 逐层应用
            int tint = e.flash > 0 ? Color.WHITE
                    : (freezeT > 0 ? Color.rgb(150, 220, 250) : 0);
            glow(c, e.x, e.y, e.r * 2.2f, e.color, 0.35f);

            if (e.type == 7 && e.aimT > 0f) {   // 狙击锁定线，世界坐标
                c.save();
                c.translate(e.x, e.y);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.6f));
                paint.setColor(Color.rgb(255, 60, 60));
                paint.setAlpha((int) (120 + 100 * Math.sin(e.t * 22f)));
                c.drawLine(0, 0, e.aimX - e.x, e.aimY - e.y, paint);
                paint.setAlpha(255);
                paint.setStyle(Paint.Style.FILL);
                c.restore();
            }

            // darters bank into their dive; everything else flies nose-down
            float rot = e.type == 1 ? (float) Math.toDegrees(Math.atan2(e.vx, e.vy)) : 0f;
            Ships.draw(c, paint, path, rect, ShipSpec.ofEnemy(e.type), e.x, e.y, e.r,
                    time + e.t, tint, 1f, rot, 0);

            if (e.maxhp > 6) {
                float w = e.r * 2f, h = dp(3.4f);
                float f = Math.max(0f, e.hp / e.maxhp);
                rect.set(e.x - w / 2, e.y - e.r - dp(9), e.x + w / 2, e.y - e.r - dp(9) + h);
                paint.setColor(Color.rgb(20, 26, 36));
                c.drawRoundRect(rect, h, h, paint);
                paint.setColor(Color.rgb(255, 90, 120));
                rect.set(rect.left, rect.top, rect.left + rect.width() * f, rect.bottom);
                c.drawRoundRect(rect, h, h, paint);
            }
        }
    }

    private void drawBullets(Canvas c) {
        for (int i = 0; i < bullets.size(); i++) {
            Bullet b = bullets.get(i);
            glow(c, b.x, b.y, b.r * 4f, b.color, 0.5f);
            float len = b.r * 3.4f;
            float a = (float) Math.atan2(b.vy, b.vx);
            c.save();
            c.translate(b.x, b.y);
            c.rotate((float) Math.toDegrees(a));
            paint.setColor(b.color);
            c.drawRoundRect(new RectF(-len, -b.r, len, b.r), b.r, b.r, paint);
            paint.setColor(Color.WHITE);
            c.drawRoundRect(new RectF(-len * 0.4f, -b.r * 0.45f, len * 0.6f, b.r * 0.45f),
                    b.r * 0.4f, b.r * 0.4f, paint);
            c.restore();
        }
        for (int i = 0; i < ebullets.size(); i++) {
            Bullet b = ebullets.get(i);
            glow(c, b.x, b.y, b.r * 4.5f, b.color, 0.55f);
            paint.setColor(b.color);
            c.drawCircle(b.x, b.y, b.r, paint);
            paint.setColor(Color.WHITE);
            c.drawCircle(b.x, b.y, b.r * 0.45f, paint);
        }
    }

    static int powerColor(int k) { return UiSpec.color(k); }

    static String powerLabel(int k) { return UiSpec.label(k); }

    private void drawPowers(Canvas c) {
        for (int i = 0; i < powers.size(); i++) {
            PowerUp p = powers.get(i);
            int col = powerColor(p.kind);
            float pulse = 1f + 0.12f * (float) Math.sin(p.t * 6f);
            float r = p.r * pulse;
            glow(c, p.x, p.y, r * 3f, col, 0.55f);
            c.save();
            c.translate(p.x, p.y);
            c.rotate((float) Math.sin(p.t * 2.2f) * 12f);
            // capsule plate + coloured rim; the vector icon replaces the old one-glyph placeholder
            paint.setColor(Color.rgb(10, 16, 26));
            rect.set(-r, -r, r, r);
            c.drawRoundRect(rect, dp(6), dp(6), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2.4f));
            paint.setColor(col);
            c.drawRoundRect(rect, dp(6), dp(6), paint);
            paint.setStyle(Paint.Style.FILL);
            Icons.draw(c, paint, path, rect, UiSpec.iconOf(p.kind), 0, 0,
                    r * UiSpec.POWER_ICON_RATIO, col);
            c.restore();
            // the label moves under the box: inside it now belongs to the icon
            text(c, powerLabel(p.kind), p.x, p.y + r + dp(11), dp(11), col, 1);
        }
    }

    private void drawParticles(Canvas c) {
        for (int i = 0; i < parts.size(); i++) {
            Particle p = parts.get(i);
            float k = p.life / p.max;
            paint.setAlpha((int) (255 * k));
            switch (p.kind) {
                case 0: // spark streak
                    paint.setColor(p.color);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(p.size);
                    c.drawLine(p.x, p.y, p.x - p.vx * 0.03f, p.y - p.vy * 0.03f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    break;
                case 1: // smoke
                    paint.setColor(p.color);
                    paint.setAlpha((int) (70 * k));
                    c.drawCircle(p.x, p.y, p.size, paint);
                    break;
                case 2: { // shockwave ring
                    float rr = p.size * (1f - k);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(p.vx * k);
                    paint.setColor(p.color);
                    c.drawCircle(p.x, p.y, rr, paint);
                    paint.setStyle(Paint.Style.FILL);
                    break;
                }
                case 3: // debris
                    paint.setColor(p.color);
                    c.save();
                    c.translate(p.x, p.y);
                    c.rotate((float) Math.toDegrees(p.rot));
                    c.drawRect(-p.size, -p.size * 0.4f, p.size, p.size * 0.4f, paint);
                    c.restore();
                    break;
                default: // glow puff
                    glow(c, p.x, p.y, p.size, p.color, 0.7f * k);
                    break;
            }
            paint.setAlpha(255);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    /** the visual aftermath of a super blast: bolt columns and the quake wave */
    private void drawSuperFx(Canvas c) {
        for (int i = 0; i < boltT.length; i++) {
            if (boltT[i] <= 0f) continue;
            float k = boltT[i] / 0.45f;
            float w = dp(9) * k;
            paint.setColor(Color.rgb(190, 230, 255));
            paint.setAlpha((int) (200 * k));
            c.drawRect(boltX[i] - w / 2, 0, boltX[i] + w / 2, H, paint);
            paint.setColor(Color.WHITE);
            paint.setAlpha((int) (255 * k));
            c.drawRect(boltX[i] - w / 6, 0, boltX[i] + w / 6, H, paint);
            paint.setAlpha(255);
            glow(c, boltX[i], H * 0.5f, dp(90) * k, Color.rgb(150, 210, 255), 0.5f * k);
        }
        if (quakeOn) {
            float h = dp(26);
            paint.setColor(Color.rgb(230, 170, 90));
            paint.setAlpha(190);
            c.drawRect(0, quakeY - h / 2, W, quakeY + h / 2, paint);
            paint.setColor(Color.rgb(255, 230, 170));
            paint.setAlpha(255);
            c.drawRect(0, quakeY - h / 6, W, quakeY + h / 6, paint);
            glow(c, W / 2f, quakeY, W * 0.7f, Color.rgb(240, 180, 100), 0.5f);
        }
    }

    private void drawTexts(Canvas c) {
        for (int i = 0; i < texts.size(); i++) {
            FloatText t = texts.get(i);
            float k = 1f - t.t / t.life;
            paint.setAlpha((int) (255 * Math.min(1f, k * 1.6f)));
            text(c, t.s, t.x, t.y, t.size, t.color, 1);
            paint.setAlpha(255);
        }
    }

    // ------------------------------------------------------------------ hud

    // ------------------------------------------------------------------- boss

    float bossR() { return dp(boss.radiusDp()); }

    int bossColor() { return boss.color(); }

    void spawnBoss(int kind) {
        if (boss != null || state != RUNNING) return;
        boss = Boss.spawn(kind, diff, bossesKilled);
        bossAlive = 1;
        bossEnter = 0f;
        bossHitFx = 0f;
        bossX = W * 0.5f;
        bossY = -bossR() - dp(20);
        banner("BOSS 登场 · " + boss.name(), boss.sub(), 3);
        audio.play(AudioFx.ALARM, 1f);
        vibro.bomb();
        shake = Math.max(shake, 14f);
        Log.i(TAG, "boss spawn name=" + boss.name() + " hp=" + (int) boss.maxHp()
                + " kind=" + boss.kind() + " phase=" + boss.phase()
                + " gap=" + String.format(java.util.Locale.US, "%.2f",
                Boss.maxGapSec(boss.phase(), diff))
                + " n=" + (bossesKilled + 1));
    }

    private void updateBoss(float dt) {
        if (boss == null) {
            if (time >= nextBossAt) {
                nextBossAt += 80f;
                spawnBoss(bossesKilled);
            }
            return;
        }
        bossEnter += realDt;          // real time: a hit-stop or a pause must not strand the entrance
        if (bossHitFx > 0) bossHitFx -= dt;
        float slow = slowT > 0 ? 0.45f : 1f;
        float home = topPad + dp(120);
        if (bossEnter < 2f) {
            // the entrance: slide down from above the top edge
            bossY += (home - bossY) * Math.min(1f, 2.2f * dt);
            bossX += (W * 0.5f - bossX) * Math.min(1f, 2.2f * dt);
        } else {
            bossX += (float) Math.sin(bossEnter * 0.7f) * dp(150) * dt * slow;
            bossX = Math.max(bossR(), Math.min(W - bossR(), bossX));
            float ty = home + (float) Math.sin(bossEnter * 1.6f) * dp(16);
            bossY += (ty - bossY) * Math.min(1f, 3f * dt);
        }
        int room = D_CAP[diff] - enemies.size();
        int req = boss.act(dt * slow, Math.max(0, room));
        if ((req & Boss.ACT_DIE) != 0) { killBoss(); return; }
        if ((req & Boss.ACT_RING) != 0) bossRing();
        if ((req & Boss.ACT_AIM) != 0) bossAim();
        if ((req & Boss.ACT_FAN) != 0) bossFan();
        if ((req & Boss.ACT_SUMMON) != 0) bossSummon();
    }

    private void bossShot(float x, float y, float a, float sp, int color, float r) {
        Bullet b = new Bullet();
        b.x = x;
        b.y = y;
        b.vx = (float) Math.sin(a) * sp * D_SPEED[diff];
        b.vy = (float) Math.cos(a) * sp * D_SPEED[diff];
        b.r = r;
        b.dmg = 1;
        b.life = 7f;
        b.color = color;
        b.enemy = true;
        ebullets.add(b);
    }

    /** phase 0: a full ring, rotated by the boss age */
    private void bossRing() {
        int n = boss.ringCount();
        int col = bossColor();
        for (int k = 0; k < n; k++)
            bossShot(bossX, bossY, (float) (k * 2 * Math.PI / n) + boss.age(), dp(200), col, dp(6));
        audio.play(AudioFx.SHOT, 0.7f);
    }

    /** phase 1+: a short aimed burst at the player */
    private void bossAim() {
        int n = boss.aimBurst();
        float base = (float) Math.atan2(sx - bossX, sy - bossY);
        for (int i = 0; i < n; i++)
            bossShot(bossX, bossY, base + (i - (n - 1) * 0.5f) * 0.13f, dp(330),
                    Color.rgb(255, 90, 90), dp(6));
        audio.play(AudioFx.SHOT, 1.1f);
    }

    /** phase 2: a wide fan sweep downwards */
    private void bossFan() {
        int n = boss.fanCount();
        for (int i = 0; i < n; i++) {
            float a = (i - (n - 1) * 0.5f) * 0.19f;
            bossShot(bossX, bossY, a, dp(260), Color.rgb(255, 170, 60), dp(7));
        }
        shake = Math.max(shake, 6f);
        audio.play(AudioFx.WALL, 0.8f);
    }

    /** phase 2: spawn minions, capped by the on-screen enemy cap so no wave is cut in half */
    private void bossSummon() {
        int n = boss.summonCount();
        int room = D_CAP[diff] - enemies.size();
        if (n <= 0) {
            Log.i(TAG, "boss summon n=0 room=" + room + " cap=" + D_CAP[diff]);
            return;
        }
        for (int i = 0; i < n; i++)
            spawnEnemy(0, bossX + (i - (n - 1) * 0.5f) * dp(46));
        Log.i(TAG, "boss summon n=" + n + " room=" + room + " cap=" + D_CAP[diff]);
        audio.play(AudioFx.PICK, 0.8f);
    }

    void hitBoss(Bullet b) {
        bossHitFx = 0.08f;
        for (int k = 0; k < 3; k++) spark(b.x, b.y, bossColor(), dp(150));
        // the kill itself is handled by the ACT_DIE request next frame, so score and drops
        // are paid out exactly once
        if (boss.hurt(b.dmg)) {
            audio.play(AudioFx.BIGKILL, 0.6f);
            return;
        }
        if (rnd.nextInt(3) == 0) audio.play(AudioFx.HIT, 0.9f);
    }

    void killBoss() {
        int pts = (int) (boss.score() * D_SCORE[diff] * comboMult());
        scoreF += pts;
        bossesKilled++;
        bossAlive = 0;
        clearEnemyBullets();
        slowT = Math.max(slowT, 2.5f);      // the slow motion that sells the kill
        bombSlow = 0.55f;
        shake = Math.max(shake, 26f);
        flashWhite = 0.9f;
        audio.play(AudioFx.BIGKILL);
        vibro.bomb();
        banner("BOSS 击破 · " + boss.name(), "+" + pts + " 全屏清弹");
        floatText(bossX, bossY, "+" + pts, GOLD, dp(26));
        for (int k = 0; k < 26; k++) spark(bossX, bossY, bossColor(), dp(340));
        ring(bossX, bossY, bossColor(), dp(200), dp(6));
        // a boss always pays out the goods
        spawnPower(PW_WEAPON, bossX - dp(30), bossY);
        spawnPower(PW_BOMB, bossX + dp(30), bossY);
        spawnPower(PW_HEAL, bossX, bossY + dp(26));
        Log.i(TAG, "boss killed name=" + boss.name() + " score=" + pts
                + " phase=" + boss.phase() + " n=" + bossesKilled);
        boss = null;
    }

    private void drawBoss(Canvas c) {
        if (boss == null) return;
        float r = bossR();
        int col = bossHitFx > 0f ? Color.WHITE : bossColor();
        float pulse = 0.5f + 0.5f * (float) Math.sin(bossEnter * 3f);
        glow(c, bossX, bossY, r * 1.7f, col, 0.30f + 0.12f * pulse);
        // 分层矢量 BOSS（Ships）：三盏阶段核心灯由 Ships 按 boss.phase() 点亮
        Ships.draw(c, paint, path, rect, ShipSpec.ofBoss(boss.kind()), bossX, bossY, r, time,
                bossHitFx > 0f ? Color.WHITE : 0, 1f, 0, boss.phase());
    }

    /** the big health bar, drawn under topPad so it never covers the HUD pills */
    private void drawBossBar(Canvas c) {
        if (boss == null) return;
        float m = dp(14);
        float w = W - m * 2;
        float y = topPad + dp(34);
        float h = dp(12);
        paint.setColor(Color.argb(150, 10, 14, 26));
        rect.set(m, y, m + w, y + h);
        c.drawRoundRect(rect, dp(6), dp(6), paint);
        float frac = Math.max(0f, Math.min(1f, boss.hpFrac()));
        int col = boss.phase() == 0 ? Color.rgb(255, 90, 90)
                : boss.phase() == 1 ? Color.rgb(255, 170, 60) : Color.rgb(255, 60, 200);
        paint.setColor(col);
        if (frac > 0f) {
            rect.set(m, y, m + w * frac, y + h);
            c.drawRoundRect(rect, dp(6), dp(6), paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(180, 255, 255, 255));
        rect.set(m, y, m + w, y + h);
        c.drawRoundRect(rect, dp(6), dp(6), paint);
        paint.setStyle(Paint.Style.FILL);
        // phase ticks
        for (int i = 1; i < Boss.PHASES; i++) {
            float x = m + w * Boss.PHASE_AT[i];
            paint.setColor(Color.argb(200, 255, 255, 255));
            c.drawRect(x, y, x + dp(1), y + h, paint);
        }
        text(c, boss.name(), m + dp(4), y + h + dp(13), dp(12), col, 0);
        text(c, (int) (frac * 100) + "%", m + w - dp(4), y + h + dp(13), dp(12), TXT_DIM, 2);
    }

    private void drawHud(Canvas c) {
        drawBossBar(c);
        float m = dp(14);
        text(c, "得分", m, topPad - dp(18), dp(12), TXT_DIM, 0);
        text(c, String.valueOf(score), m, topPad + dp(12), dp(30), TXT, 0);
        text(c, "最高 " + best, m, topPad + dp(31), dp(12), TXT_DIM, 0);

        // lives + weapon on the right
        for (int i = 0; i < lives; i++) {
            float x = W - m - dp(9) - i * dp(20), y = topPad - dp(16);
            path.reset();
            path.moveTo(x, y - dp(8));
            path.lineTo(x + dp(7), y + dp(6));
            path.lineTo(x, y + dp(2));
            path.lineTo(x - dp(7), y + dp(6));
            path.close();
            paint.setColor(SHIP);
            c.drawPath(path, paint);
        }
        text(c, "火力 LV" + weapon, W - m, topPad + dp(6), dp(14), powerColor(PW_WEAPON), 2);
        if (shield > 0)
            text(c, "护盾 x" + shield, W - m, topPad + dp(25), dp(13), powerColor(PW_SHIELD), 2);

        // active effect pills
        float py = topPad + dp(48);
        py = pill(c, py, frenzyT > 0, UiSpec.ICON_RAPID, "狂暴", frenzyT, powerColor(PW_RAPID));
        py = pill(c, py, rapidT > 0, UiSpec.ICON_RAPID, "速射", rapidT, powerColor(PW_RAPID));
        py = pill(c, py, laserT > 0, UiSpec.ICON_LASER, "激光", laserT, powerColor(PW_LASER));
        py = pill(c, py, homingT > 0, UiSpec.ICON_HOMING, "追踪", homingT, powerColor(PW_HOMING));
        py = pill(c, py, slowT > 0, UiSpec.ICON_SLOW, "缓速场", slowT, powerColor(PW_SHIELD));
        py = pill(c, py, shield > 0, UiSpec.ICON_SHIELD, "护盾 x" + shield, -1, powerColor(PW_SHIELD));
        py = pill(c, py, weapon >= MAX_WEAPON, UiSpec.ICON_WEAPON, "火力全开", -1, powerColor(PW_WEAPON));
        py = pill(c, py, feverActive(), UiSpec.ICON_SCORE, "连击狂热 x2", -1, GOLD);
        py = pill(c, py, freezeT > 0, UiSpec.ICON_SLOW, "冰封", freezeT, powerColor(PW_SHIELD));
        py = pill(c, py, bombStock >= Tuning.BOMB_MAX, UiSpec.ICON_BOMB, "炸弹满仓", -1, powerColor(PW_BOMB));

        if (combo >= 2) {
            float k = Math.min(1f, comboT / 0.4f);
            paint.setAlpha((int) (150 + 105 * k));
            text(c, "连击 x" + combo, W / 2f, topPad + dp(56), dp(20) + Math.min(combo, 20) * dp(0.3f),
                    GOLD, 1);
            text(c, "得分 x" + String.format(java.util.Locale.US, "%.1f", comboMult()),
                    W / 2f, topPad + dp(76), dp(12), TXT_DIM, 1);
            paint.setAlpha(255);
        }

        String mm = String.format(java.util.Locale.US, "%02d:%02d", (int) (time / 60), (int) time % 60);
        float by = H - botPad - dp(6);
        text(c, "时间 " + mm + "   击坠 " + kills, m, by, dp(13), TXT_DIM, 0);
        text(c, DIFF_NAME[diff], W - m, by, dp(13), DIFF_COL[diff], 2);

        drawSkillButtons(c);
    }

    /**
     * The two active-skill buttons, bottom corners of the play field: the energy super blast
     * on the right (the ring fills as it charges), the bomb stock on the left. Everything is
     * drawn inside the circle so nothing collides with the "时间 / 击坠" line - the DeX window
     * is only 865px tall, so the HUD has no room to spare.
     */
    private void drawSkillButtons(Canvas c) {
        float r = dp(21);
        float cy = H - botPad - r - dp(30);
        btnSuper.set(W - r * 2 - dp(10), cy - r, W - dp(10), cy + r);
        btnBomb.set(dp(10), cy - r, dp(10) + r * 2, cy + r);

        // super blast: ring gauge + label, glows when ready
        boolean ready = energy >= Tuning.ENERGY_MAX;
        float pulse = ready ? 0.6f + 0.4f * (float) Math.sin(time * 7f) : 0f;
        int col = superTheme >= 0 && superFxT > 0 ? Super.color(superTheme) : SHIP;
        float cx = btnSuper.centerX();
        paint.setColor(Color.rgb(8, 14, 26));
        paint.setAlpha(210);
        c.drawCircle(cx, cy, r, paint);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3));
        paint.setColor(ready ? Color.rgb(255, 220, 120) : Color.rgb(70, 90, 120));
        c.drawCircle(cx, cy, r, paint);
        // the charge arc
        paint.setColor(ready ? Color.rgb(255, 230, 140) : col);
        rect.set(btnSuper.left, btnSuper.top, btnSuper.right, btnSuper.bottom);
        c.drawArc(rect, -90f, 360f * Math.max(0f, Math.min(1f, energy / Tuning.ENERGY_MAX)),
                false, paint);
        paint.setStyle(Paint.Style.FILL);
        if (ready) glow(c, cx, cy, r * 2.4f, Color.rgb(255, 210, 120), 0.3f + 0.25f * pulse);
        Icons.draw(c, paint, path, rect, UiSpec.ICON_SUPER, cx, cy - dp(2),
                r * UiSpec.SKILL_ICON_RATIO, ready ? Color.rgb(255, 240, 190) : TXT_DIM);
        text(c, ready ? "就绪" : (int) (energy / Tuning.ENERGY_MAX * 100) + "%",
                cx, cy + r * 0.72f, dp(10), ready ? GOLD : TXT_DIM, 1);

        // bomb stock
        float bx = btnBomb.centerX();
        paint.setColor(Color.rgb(8, 14, 26));
        paint.setAlpha(210);
        c.drawCircle(bx, cy, r, paint);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.4f));
        paint.setColor(bombStock > 0 ? powerColor(PW_BOMB) : Color.rgb(70, 80, 100));
        c.drawCircle(bx, cy, r, paint);
        paint.setStyle(Paint.Style.FILL);
        Icons.draw(c, paint, path, rect, UiSpec.ICON_BOMB_BTN, bx, cy - dp(2),
                r * UiSpec.SKILL_ICON_RATIO, bombStock > 0 ? Color.rgb(255, 190, 170) : TXT_DIM);
        text(c, "x" + bombStock, bx, cy + r * 0.72f, dp(10),
                bombStock > 0 ? powerColor(PW_BOMB) : TXT_DIM, 1);
    }

    /**
     * One active-effect pill: icon + label + optional countdown. The icon carries the meaning, so
     * the label stays short; the pill grows to fit, so nothing is clipped on a narrow DeX window.
     */
    private float pill(Canvas c, float y, boolean on, int icon, String label, float t, int col) {
        if (!on) return y;
        float h = dp(20), is = UiSpec.PILL_ICON_DP;
        float pad = dp(6), gap = dp(4);
        float w = pad * 2 + is + gap + textW(label, dp(12)) + (t > 0 ? dp(26) : 0);
        rect.set(m(), y, m() + w, y + h);
        paint.setColor(Color.rgb(12, 20, 32));
        paint.setAlpha(190);
        c.drawRoundRect(rect, h / 2, h / 2, paint);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(col);
        c.drawRoundRect(rect, h / 2, h / 2, paint);
        paint.setStyle(Paint.Style.FILL);
        float ix = m() + pad + is / 2;
        Icons.draw(c, paint, path, rect, icon, ix, y + h / 2, is / 2, col);
        text(c, label, ix + is / 2 + gap, y + h - dp(6), dp(12), col, 0);
        if (t > 0) text(c, String.format(java.util.Locale.US, "%.1f", t), m() + w - dp(9),
                y + h - dp(6), dp(12), TXT, 2);
        return y + h + dp(5);
    }

    private float m() { return dp(14); }

    private float textW(String s, float size) {
        textPaint.setTextSize(size);
        return textPaint.measureText(s);
    }

    private void drawBanner(Canvas c) {
        if (bannerT <= 0) return;
        float k = bannerT / 2.1f;                       // 1 -> 0
        float a = k > 0.85f ? (1f - k) / 0.15f : Math.min(1f, k / 0.25f);
        float scale = 1f + (1f - k) * 0.12f;
        float cy = H * 0.34f;
        c.save();
        c.translate(W / 2f, cy);
        c.scale(scale, scale);
        paint.setColor(Color.rgb(6, 10, 20));
        paint.setAlpha((int) (170 * a));
        rect.set(-W, -dp(40), W, dp(40));
        c.drawRect(rect, paint);
        paint.setAlpha((int) (255 * a));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(GOLD);
        c.drawLine(-W * 0.5f, -dp(38), W * 0.5f, -dp(38), paint);
        c.drawLine(-W * 0.5f, dp(38), W * 0.5f, dp(38), paint);
        paint.setStyle(Paint.Style.FILL);
        text(c, banner, 0, dp(2), dp(34), GOLD, 1);
        text(c, bannerSub, 0, dp(26), dp(14), TXT, 1);
        paint.setAlpha(255);
        c.restore();
    }

    // ------------------------------------------------------------------ screens

    private void panel(Canvas c, float x, float y, float w, float h, int accent) {
        paint.setColor(Color.rgb(8, 13, 24));
        paint.setAlpha(225);
        rect.set(x, y, x + w, y + h);
        c.drawRoundRect(rect, dp(14), dp(14), paint);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.4f));
        paint.setColor(accent);
        paint.setAlpha(160);
        c.drawRoundRect(rect, dp(14), dp(14), paint);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);
    }

    /** leaderboard table, returns the bottom y */
    private String diffName(Scores.Entry e) {
        int d = Math.max(0, Math.min(DIFF_NAME.length - 1, e.diff));
        return DIFF_NAME[d];
    }

    /** @param maxBottom last y the board may use, so it never runs into the option block */
    private float drawBoard(Canvas c, float x, float y, float w, int n, int highlight,
                            float maxBottom) {
        float rowH = (dp(30) + dp(14)) * fit;
        if (maxBottom > 0)
            n = Math.max(1, Math.min(n, (int) ((maxBottom - (y + dp(26))) / rowH)));
        text(c, "排行榜 TOP " + n, x, y, dp(15), GOLD, 0);
        float row = dp(30) * fit;
        float yy = y + dp(26);
        java.util.List<Scores.Entry> list = Scores.load(getContext());
        if (list.isEmpty()) {
            text(c, "暂无记录 - 飞出第一个成绩吧", x + w / 2, yy + dp(14), dp(13), TXT_DIM, 1);
            return yy + dp(34);
        }
        for (int i = 0; i < Math.min(n, list.size()); i++) {
            Scores.Entry e = list.get(i);
            boolean hl = i == highlight;
            if (hl) {
                paint.setColor(GOLD);
                paint.setAlpha(40);
                rect.set(x - dp(6), yy - dp(16), x + w + dp(6), yy + dp(11));
                c.drawRoundRect(rect, dp(6), dp(6), paint);
                paint.setAlpha(255);
            }
            int rk = i + 1;
            int col = rk == 1 ? GOLD : rk <= 3 ? Color.rgb(200, 220, 255) : TXT_DIM;
            text(c, String.valueOf(rk), x + dp(6), yy, dp(14) * fit, col, 1);
            text(c, e.name, x + dp(22), yy, dp(14) * fit, hl ? GOLD : TXT, 0);
            text(c, String.valueOf(e.score), x + w, yy, dp(14) * fit, TXT, 2);
            text(c, "击坠 " + e.kills + " · " + diffName(e) + " · " + Scores.date(e.date),
                    x + w, yy + dp(15) * fit, dp(10) * fit, TXT_DIM, 2);
            yy += row + dp(14) * fit;
        }
        return yy;
    }

    /** 3 difficulty buttons; 简单 is the original tuning. Returns the y below the caption. */
    private float drawDiff(Canvas c, float cx, float y, float h) {
        float w = dp(66) * fit, gap = dp(8) * fit;
        float x = cx - (w * 3 + gap * 2) / 2;
        text(c, "难度", cx, y - dp(10) * fit, dp(13) * fit, TXT_DIM, 1);
        for (int i = 0; i < btnDiff.length; i++) {
            RectF r = btnDiff[i];
            r.set(x + i * (w + gap), y, x + i * (w + gap) + w, y + h);
            boolean on = i == diff;
            paint.setColor(on ? Color.rgb(34, 24, 20) : Color.rgb(24, 26, 34));
            c.drawRoundRect(r, dp(7), dp(7), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.3f));
            paint.setColor(on ? DIFF_COL[i] : Color.rgb(88, 98, 118));
            c.drawRoundRect(r, dp(7), dp(7), paint);
            paint.setStyle(Paint.Style.FILL);
            text(c, DIFF_NAME[i], r.centerX(), r.centerY() + dp(5) * fit, dp(15) * fit,
                    on ? Color.WHITE : TXT_DIM, 1);
        }
        text(c, "得分 x" + String.format(java.util.Locale.US, "%.2f", D_SCORE[diff])
                        + " · 敌机 x" + String.format(java.util.Locale.US, "%.2f", D_HP[diff]),
                cx, y + h + dp(17) * fit, dp(12) * fit, DIFF_COL[diff], 1);
        return y + h + dp(22) * fit;
    }

    /** 7 step buttons: 1..3 slower, 4 = standard, 5..7 faster. Returns the y below the caption. */
    private float drawSens(Canvas c, float cx, float y, float h) {
        float w = dp(30) * fit, gap = dp(4) * fit;
        float total = w * SENS.length + gap * (SENS.length - 1);
        float x = cx - total / 2;
        text(c, "拖动灵敏度", cx - dp(56) * fit, y - dp(10) * fit, dp(13) * fit, TXT_DIM, 2);
        float sc = UiSpec.ICON_MIN_DP * fit;
        int scol = Color.rgb(140, 152, 175);
        Icons.draw(c, paint, path, rect, UiSpec.ICON_SLOW,
                cx - total / 2 - dp(18) * fit, y + h - sc, sc, scol);
        Icons.draw(c, paint, path, rect, UiSpec.ICON_FAST,
                cx + total / 2 + dp(18) * fit, y + h - sc, sc, scol);
        for (int i = 0; i < SENS.length; i++) {
            RectF r = btnSens[i];
            r.set(x + i * (w + gap), y, x + i * (w + gap) + w, y + h);
            boolean on = i == sensIdx;
            paint.setColor(on ? Color.rgb(18, 44, 62) : Color.rgb(24, 26, 34));
            c.drawRoundRect(r, dp(7), dp(7), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.3f));
            paint.setColor(on ? SHIP : Color.rgb(88, 98, 118));
            c.drawRoundRect(r, dp(7), dp(7), paint);
            paint.setStyle(Paint.Style.FILL);
            text(c, String.valueOf(i + 1), r.centerX(), r.centerY() + dp(5) * fit, dp(14) * fit,
                    on ? Color.rgb(190, 245, 255) : TXT_DIM, 1);
        }
        text(c, "第 " + (sensIdx + 1) + " 档 · " + (sensIdx == SENS_STD ? "标准" : "自定义"),
                cx, y + h + dp(17) * fit, dp(13) * fit, sensIdx == SENS_STD ? SHIP : GOLD, 1);
        return y + h + dp(22) * fit;
    }

    private float drawToggles(Canvas c, float cx, float y, float h) {
        float w = dp(86) * fit, gap = dp(10) * fit;
        float x = cx - (w * 3 + gap * 2) / 2;
        btnMusic.set(x, y, x + w, y + h);
        btnSound.set(x + w + gap, y, x + w * 2 + gap, y + h);
        btnVibe.set(x + (w + gap) * 2, y, x + w * 3 + gap * 2, y + h);
        toggle(c, btnMusic, UiSpec.ICON_MUSIC, "音乐", musicOn);
        toggle(c, btnSound, UiSpec.ICON_SFX, "音效", soundOn);
        toggle(c, btnVibe, UiSpec.ICON_VIBRO, "震动", vibrateOn);
        return y + h;
    }

    /** 难度 / 灵敏度 / 开关：底部对齐的一整块，短屏自动整体缩小 (ui) */
    private float drawOptions(Canvas c, float cx, float bottom) {
        float rowH = dp(30) * fit, capH = dp(22) * fit, gap = dp(14) * fit;
        float block = (rowH + capH) * 2 + rowH + gap * 2;
        float top = bottom - block;
        float y = drawDiff(c, cx, top, rowH);
        y = drawSens(c, cx, y + gap, rowH);
        drawToggles(c, cx, y + gap, rowH);
        return top;
    }

    /** One on/off button: icon, label and a switch glyph, so the state reads without CJK. */
    private void toggle(Canvas c, RectF r, int icon, String label, boolean on) {
        paint.setColor(on ? Color.rgb(20, 46, 40) : Color.rgb(24, 26, 34));
        c.drawRoundRect(r, dp(8), dp(8), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(on ? Color.rgb(110, 231, 158) : Color.rgb(90, 100, 120));
        c.drawRoundRect(r, dp(8), dp(8), paint);
        paint.setStyle(Paint.Style.FILL);
        int col = on ? Color.rgb(160, 245, 195) : TXT_DIM;
        float is = dp(7);
        Icons.draw(c, paint, path, rect, icon, r.left + dp(9) + is, r.centerY(), is, col);
        Icons.switch_(c, paint, rect, r.right - dp(13), r.centerY(), dp(17), dp(9), on, col);
        text(c, label, r.centerX() - dp(4), r.centerY() + dp(5), dp(13), col, 1);
    }

    /**
     * The whole power-up alphabet in one row on the title screen: the icons are the legend, so
     * the player learns them before the first drop ever appears.
     */
    private void drawLegend(Canvas c, float cx, float y) {
        float is = UiSpec.LEGEND_ICON_DP * fit;
        float step = is * 2.3f;
        int n = UiSpec.PW_COUNT;
        float x0 = cx - step * (n - 1) / 2;
        for (int k = 0; k < n; k++)
            Icons.draw(c, paint, path, rect, UiSpec.iconOf(k), x0 + step * k, y, is, UiSpec.color(k));
    }

    private void drawTitle(Canvas c) {
        paint.setColor(Color.rgb(4, 6, 14));
        paint.setAlpha(200);
        c.drawRect(0, 0, W, H, paint);
        paint.setAlpha(255);

        float cy = topPad + dp(34);
        glow(c, W / 2f, cy, dp(150), Color.rgb(60, 140, 255), 0.35f);
        text(c, "雷霆战机", W / 2f, cy, dp(46), TXT, 1);
        text(c, "PLANE  WAR", W / 2f, cy + dp(26), dp(14), Color.rgb(120, 170, 230), 1);

        float iy = cy + dp(62);
        text(c, "手指拖动 = 飞机同步移动", W / 2f, iy, dp(16), TXT, 1);
        text(c, "不用点到飞机 · 机头恒朝上 · 自动开火", W / 2f, iy + dp(25), dp(13), TXT_DIM, 1);
        text(c, "吃道具", W / 2f, iy + dp(46), dp(13), TXT_DIM, 1);
        drawLegend(c, W / 2f, iy + dp(64));

        float hintY = H - botPad - dp(44);
        float optTop = drawOptions(c, W / 2f, hintY - dp(26));

        float pw = W - dp(48);
        drawBoard(c, dp(24), iy + dp(78), pw, 10, -1, optTop - dp(10));

        float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 320f);
        paint.setAlpha((int) (150 + 105 * pulse));
        text(c, "拖动屏幕 开始飞行", W / 2f, hintY, dp(20), GOLD, 1);
        paint.setAlpha(255);
    }

    private void drawPause(Canvas c) {
        paint.setColor(Color.rgb(4, 6, 14));
        paint.setAlpha(210);
        c.drawRect(0, 0, W, H, paint);
        paint.setAlpha(255);
        text(c, "已暂停", W / 2f, topPad + dp(56), dp(34), TXT, 1);
        text(c, "得分 " + score + "   击坠 " + kills, W / 2f, topPad + dp(90), dp(16), TXT_DIM, 1);
        float hintY = H - botPad - dp(40);
        drawOptions(c, W / 2f, hintY - dp(30));
        text(c, "拖动屏幕继续", W / 2f, hintY, dp(18), GOLD, 1);
    }

    private void drawOver(Canvas c) {
        paint.setColor(Color.rgb(6, 4, 8));
        paint.setAlpha((int) (200 * Math.min(1f, deathT)));
        c.drawRect(0, 0, W, H, paint);
        paint.setAlpha(255);
        if (deathT < 0.25f) return;

        float pw = W - dp(40), px = dp(20), top = H * 0.12f;
        panel(c, px, top, pw, dp(150) * fit, GOLD);
        text(c, "战机被击落", W / 2f, top + dp(36) * fit, dp(20) * fit, Color.rgb(255, 120, 120), 1);
        text(c, String.valueOf(score), W / 2f, top + dp(86) * fit, dp(46) * fit, TXT, 1);
        text(c, "最高 " + best + "   难度 " + DIFF_NAME[diff],
                W / 2f, top + dp(112) * fit, dp(14) * fit, TXT_DIM, 1);
        String mm = String.format(java.util.Locale.US, "%02d:%02d", (int) (time / 60), (int) time % 60);
        text(c, "击坠 " + kills + "   时间 " + mm + "   最高连击 x" + maxCombo,
                W / 2f, top + dp(138) * fit, dp(13) * fit, TXT_DIM, 1);

        java.util.List<Scores.Entry> list = Scores.load(getContext());
        int mine = -1, rank = -1;
        for (int i = 0; i < list.size(); i++)
            if (list.get(i).score == score && rank < 0) { rank = i; }
        text(c, rank >= 0 ? "恭喜上榜：第 " + (rank + 1) + " 名" : "未进入榜单，再试一次",
                W / 2f, top + dp(180) * fit, dp(16) * fit, rank >= 0 ? GOLD : TXT_DIM, 1);

        drawBoard(c, px + dp(10), top + dp(204) * fit, pw - dp(20), 10, rank,
                H - botPad - dp(56));

        if (deathT > 0.9f) {
            float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 320f);
            paint.setAlpha((int) (150 + 105 * pulse));
            text(c, "拖动屏幕 返回标题", W / 2f, H - botPad - dp(40), dp(19), GOLD, 1);
            paint.setAlpha(255);
        }
    }
}
