package dev.termux.planewar;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;

/**
 * All audio is synthesised at runtime (no asset files, no network):
 *  - music : one looping chiptune track (Am-F-C-G) written to the cache dir, played by MediaPlayer
 *  - sfx   : short procedural wav files loaded into a SoundPool
 */
public class AudioFx {

    public static final String SHOT = "shot", HIT = "hit", KILL = "kill", BIGKILL = "bigkill",
            POWER = "power", SHIELD = "shield", BOMB = "bomb", HURT = "hurt", OVER = "over",
            EVENT = "event", PICK = "pick", START = "start", WALL = "wall", SUPER = "super",
            GRAZE = "graze";

    private static final String TAG = "planewar";
    private static final int RATE = 22050;
    private static final String VER = "v5";

    private final Context ctx;
    private final HashMap<String, Integer> ids = new HashMap<String, Integer>();
    private SoundPool pool;
    private MediaPlayer music;
    private boolean ready;
    private int loadedCount;
    private boolean wantMusic = true, wantSound = true;
    private float musicVol = 0.26f;

    public AudioFx(Context c) {
        this.ctx = c;
        new Thread(new Runnable() {
            @Override public void run() { build(); }
        }, "audio-gen").start();
    }

    // ---------------------------------------------------------------- lifecycle

    public void startMusic() {
        wantMusic = true;
        if (music != null) {
            try { music.start(); } catch (Exception ignored) {}
            setMusicVolume(musicVol);
        }
    }

    public void stopMusic() {
        if (music != null) {
            try { if (music.isPlaying()) music.pause(); } catch (Exception ignored) {}
        }
    }

    public void pause() {
        if (music != null) {
            try { if (music.isPlaying()) music.pause(); } catch (Exception ignored) {}
        }
    }

    public void resume() {
        if (music != null && wantMusic) {
            try { music.start(); } catch (Exception ignored) {}
        }
    }

    public void release() {
        try { if (pool != null) pool.release(); } catch (Exception ignored) {}
        try { if (music != null) { music.release(); music = null; } } catch (Exception ignored) {}
    }

    public void setMusicOn(boolean on) {
        wantMusic = on;
        if (music == null) return;
        if (!on) {
            try { if (music.isPlaying()) music.pause(); } catch (Exception ignored) {}
        } else {
            try { music.start(); } catch (Exception ignored) {}
            setMusicVolume(musicVol);
        }
    }

    public void setSoundOn(boolean on) { wantSound = on; }

    public boolean musicOn() { return wantMusic; }
    public boolean soundOn() { return wantSound; }

    /** duck the music a little during big moments */
    public void setMusicVolume(float v) {
        musicVol = v;
        if (music != null && wantMusic) {
            try { music.setVolume(v, v); } catch (Exception ignored) {}
        }
    }

    public void play(String name) { play(name, 1f); }

    public void play(String name, float rate) {
        if (!wantSound || pool == null || !ready) return;
        Integer id = ids.get(name);
        if (id == null) return;
        try { pool.play(id, 0.9f, 0.9f, 1, 0, rate); } catch (Exception ignored) {}
    }

    // ---------------------------------------------------------------- generation

    private void build() {
        try {
            File dir = ctx.getCacheDir();
            File mus = new File(dir, "music_" + VER + ".wav");
            if (!mus.exists()) writeWav(mus, music(RATE), RATE);

            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            pool = new SoundPool.Builder().setMaxStreams(12).setAudioAttributes(aa).build();
            pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override public void onLoadComplete(SoundPool sp, int sampleId, int status) {
                    loadedCount++;
                    if (loadedCount >= ids.size()) {
                        ready = true;
                        Log.i(TAG, "audio ready: " + loadedCount + " sfx");
                    }
                }
            });
            String[] names = {SHOT, HIT, KILL, BIGKILL, POWER, SHIELD, BOMB, HURT, OVER, EVENT, PICK, START, WALL, SUPER, GRAZE};
            for (String n : names) {
                File f = new File(dir, "sfx_" + n + "_" + VER + ".wav");
                if (!f.exists()) writeWav(f, sfx(n, RATE), RATE);
                ids.put(n, pool.load(f.getAbsolutePath(), 1));
            }

            AudioAttributes ma = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            music = new MediaPlayer();
            music.setAudioAttributes(ma);
            music.setDataSource(mus.getAbsolutePath());
            music.setLooping(true);
            music.setVolume(musicVol, musicVol);
            music.prepare();
            Log.i(TAG, "audio prepared");
        } catch (Throwable t) {
            Log.w(TAG, "audio init failed: " + t);
        }
    }

    // ---------------------------------------------------------------- sfx voices

    private static short[] sfx(String name, int rate) {
        float[] b;
        if (name.equals(SHOT)) {
            b = new float[(int) (rate * 0.13)];
            sweep(b, rate, 0, 0.09, 1150, 380, 0.20, Wave.SQUARE);
            noise(b, rate, 0, 0.035, 0.10, 0.90);
        } else if (name.equals(HIT)) {
            b = new float[(int) (rate * 0.10)];
            noise(b, rate, 0, 0.06, 0.22, 0.55);
            sweep(b, rate, 0, 0.05, 520, 180, 0.10, Wave.TRIANGLE);
        } else if (name.equals(KILL)) {
            b = new float[(int) (rate * 0.42)];
            noise(b, rate, 0, 0.30, 0.30, 0.30);
            sweep(b, rate, 0, 0.28, 320, 55, 0.26, Wave.SAW);
            sweep(b, rate, 0.01, 0.12, 900, 200, 0.10, Wave.SQUARE);
        } else if (name.equals(BIGKILL)) {
            b = new float[(int) (rate * 1.05)];
            noise(b, rate, 0, 0.85, 0.42, 0.22);
            sweep(b, rate, 0, 0.80, 190, 32, 0.42, Wave.SAW);
            sweep(b, rate, 0.02, 0.35, 700, 90, 0.14, Wave.SQUARE);
        } else if (name.equals(POWER)) {
            b = new float[(int) (rate * 0.42)];
            int[] n = {784, 988, 1319, 1568};
            for (int i = 0; i < n.length; i++)
                tone(b, rate, i * 0.065, 0.16, n[i], 0.20, Wave.TRIANGLE);
        } else if (name.equals(SHIELD)) {
            b = new float[(int) (rate * 0.55)];
            sweep(b, rate, 0, 0.34, 300, 900, 0.20, Wave.TRIANGLE);
            tone(b, rate, 0.05, 0.40, 1800, 0.06, Wave.SINE);
            tone(b, rate, 0.05, 0.40, 2700, 0.04, Wave.SINE);
        } else if (name.equals(BOMB)) {
            b = new float[(int) (rate * 1.5)];
            noise(b, rate, 0, 1.25, 0.50, 0.16);
            sweep(b, rate, 0, 1.20, 260, 26, 0.50, Wave.SAW);
            sweep(b, rate, 0, 0.20, 2400, 200, 0.16, Wave.SQUARE);
        } else if (name.equals(HURT)) {
            b = new float[(int) (rate * 0.55)];
            sweep(b, rate, 0, 0.40, 380, 70, 0.30, Wave.SAW);
            noise(b, rate, 0, 0.22, 0.26, 0.45);
        } else if (name.equals(OVER)) {
            b = new float[(int) (rate * 1.9)];
            int[] n = {523, 440, 349, 262};
            for (int i = 0; i < n.length; i++) {
                tone(b, rate, i * 0.26, 0.42, n[i], 0.22, Wave.SAW);
                tone(b, rate, i * 0.26, 0.42, n[i] / 2f, 0.14, Wave.TRIANGLE);
            }
            noise(b, rate, 0, 1.1, 0.16, 0.20);
        } else if (name.equals(EVENT)) {
            b = new float[(int) (rate * 0.7)];
            tone(b, rate, 0, 0.30, 523, 0.18, Wave.SAW);
            tone(b, rate, 0, 0.30, 659, 0.14, Wave.SAW);
            tone(b, rate, 0.22, 0.42, 784, 0.20, Wave.SAW);
            tone(b, rate, 0.22, 0.42, 988, 0.14, Wave.SAW);
            noise(b, rate, 0, 0.10, 0.12, 0.8);
        } else if (name.equals(PICK)) {
            b = new float[(int) (rate * 0.16)];
            sweep(b, rate, 0, 0.10, 1046, 1720, 0.18, Wave.TRIANGLE);
        } else if (name.equals(START)) {
            b = new float[(int) (rate * 0.7)];
            int[] n = {523, 659, 784, 1046};
            for (int i = 0; i < n.length; i++)
                tone(b, rate, i * 0.10, 0.26, n[i], 0.20, Wave.SQUARE);
        } else if (name.equals(WALL)) {
            b = new float[(int) (rate * 0.09)];
            noise(b, rate, 0, 0.05, 0.10, 0.35);
            sweep(b, rate, 0, 0.05, 700, 300, 0.06, Wave.SQUARE);
        } else if (name.equals(SUPER)) {
            // rising sweep, a sub drop, a bright arpeggio and a noise crack
            b = new float[(int) (rate * 1.2)];
            sweep(b, rate, 0, 0.45, 180, 1500, 0.24, Wave.SAW);
            sweep(b, rate, 0.18, 0.7, 110, 34, 0.32, Wave.SINE);
            int[] n = {659, 784, 988, 1319, 1568};
            for (int i = 0; i < n.length; i++)
                tone(b, rate, 0.26 + i * 0.06, 0.30, n[i], 0.15, Wave.TRIANGLE);
            noise(b, rate, 0.26, 0.7, 0.20, 0.55);
        } else if (name.equals(GRAZE)) {
            // a thin bright "shing" - a bullet brushing past the ship, must stay quiet
            b = new float[(int) (rate * 0.16)];
            tone(b, rate, 0, 0.07, 2100, 0.10, Wave.SINE);
            tone(b, rate, 0.01, 0.06, 3130, 0.06, Wave.SINE);
            sweep(b, rate, 0, 0.10, 2600, 1500, 0.05, Wave.TRIANGLE);
        } else {
            b = new float[(int) (rate * 0.1)];
        }
        return toShort(b);
    }

    // ---------------------------------------------------------------- music

    /** 8 bars, A minor, 138 bpm: bass + arpeggio + pentatonic lead + drums, with echo. */
    private static short[] music(int rate) {
        double bpm = 138.0, beat = 60.0 / bpm, bar = 4 * beat, bars = 8;
        int len = (int) ((bars * bar + 1.2) * rate);
        float[] b = new float[len];

        // Am F C G  (midi)
        int[][] chord = {{57, 60, 64}, {53, 57, 60}, {48, 52, 55}, {55, 59, 62}};
        int[] bassRoot = {45, 41, 36, 43};
        // pentatonic (A C D E G) lead, one entry per 8th: 0 = rest
        int[] lead = {
                69, 0, 72, 74, 76, 0, 74, 72,
                69, 0, 72, 69, 72, 74, 0, 72,
                76, 0, 74, 72, 69, 72, 0, 69,
                74, 72, 69, 67, 69, 0, 0, 0,
                76, 0, 79, 76, 74, 0, 72, 74,
                76, 0, 74, 76, 79, 81, 0, 79,
                81, 0, 79, 76, 74, 72, 74, 76,
                72, 69, 0, 67, 0, 0, 0, 0
        };

        for (int barI = 0; barI < bars; barI++) {
            double t0 = barI * bar;
            int ci = barI % 4;
            int[] ch = chord[ci];

            // bass: driving 8ths
            for (int e = 0; e < 8; e++) {
                double t = t0 + e * beat / 2;
                double amp = (e % 2 == 0) ? 0.30 : 0.17;
                tone(b, rate, t, 0.20, midi(bassRoot[ci]), amp, Wave.SAW);
                tone(b, rate, t, 0.12, midi(bassRoot[ci]) * 2, amp * 0.30, Wave.SINE);
            }
            // arpeggio: 16ths
            for (int e = 0; e < 16; e++) {
                double t = t0 + e * beat / 4;
                int n = ch[e % 3] + 12 * (1 + (e / 3) % 2);
                tone(b, rate, t, 0.10, midi(n), 0.075, Wave.SQUARE);
            }
            // lead: 8ths, with two echo taps
            for (int e = 0; e < 8; e++) {
                int n = lead[(barI * 8 + e) % lead.length];
                if (n == 0) continue;
                double t = t0 + e * beat / 2;
                double f = midi(n);
                tone(b, rate, t, beat * 0.42, f, 0.135, Wave.TRIANGLE);
                tone(b, rate, t + beat * 0.75, beat * 0.35, f, 0.055, Wave.TRIANGLE);
                tone(b, rate, t + beat * 1.5, beat * 0.30, f, 0.025, Wave.TRIANGLE);
            }
            // drums
            for (int e = 0; e < 4; e++) {
                if (e == 0 || e == 2) kick(b, rate, t0 + e * beat);
                if (e == 1 || e == 3) snare(b, rate, t0 + e * beat);
                kick(b, rate, t0 + 3.5 * beat);
            }
            for (int e = 0; e < 16; e++) {
                hat(b, rate, t0 + e * beat / 4, (e % 4 == 2) ? 0.075 : 0.035);
            }
        }
        return toShort(b);
    }

    private static double midi(int n) { return 440.0 * Math.pow(2, (n - 69) / 12.0); }

    private static void kick(float[] b, int rate, double t) {
        sweep(b, rate, t, 0.16, 130, 42, 0.34, Wave.SINE);
        noise(b, rate, t, 0.02, 0.10, 0.9);
    }

    private static void snare(float[] b, int rate, double t) {
        noise(b, rate, t, 0.13, 0.16, 0.62);
        tone(b, rate, t, 0.07, 210, 0.07, Wave.TRIANGLE);
    }

    private static void hat(float[] b, int rate, double t, double amp) {
        noise(b, rate, t, 0.035, amp, 0.97);
    }

    // ---------------------------------------------------------------- synth core

    private enum Wave { SINE, SQUARE, TRIANGLE, SAW }

    private static double osc(Wave w, double ph) {
        double x = ph - Math.floor(ph);          // 0..1
        switch (w.ordinal()) {
            case 0: return Math.sin(2 * Math.PI * x);
            case 1: return (Math.sin(2 * Math.PI * x)
                    + 0.55 * Math.sin(6 * Math.PI * x)
                    + 0.33 * Math.sin(10 * Math.PI * x)) * 0.72;
            case 2: return (4 * Math.abs(x - 0.5) - 1) * -1.0;
            default: return (2 * x - 1) * 0.9
                    + 0.22 * Math.sin(4 * Math.PI * x)
                    + 0.12 * Math.sin(6 * Math.PI * x);
        }
    }

    private static void tone(float[] b, int rate, double t, double dur, double freq, double amp, Wave w) {
        int i0 = (int) (t * rate);
        int n = (int) (dur * rate);
        if (i0 < 0) { n += i0; i0 = 0; }
        if (n <= 0 || i0 >= b.length) return;
        if (i0 + n > b.length) n = b.length - i0;
        int atk = Math.max(1, (int) (0.008 * rate));
        int rel = Math.max(1, (int) (dur * rate * 0.45));
        for (int i = 0; i < n; i++) {
            double e = 1.0;
            if (i < atk) e = (double) i / atk;
            else if (i > n - rel) e = (double) (n - i) / rel;
            e = e * e * (3 - 2 * e);            // smoothstep envelope
            b[i0 + i] += (float) (amp * e * osc(w, freq * i / (double) rate));
        }
    }

    private static void sweep(float[] b, int rate, double t, double dur, double f0, double f1,
                              double amp, Wave w) {
        int i0 = (int) (t * rate);
        int n = (int) (dur * rate);
        if (i0 < 0) { n += i0; i0 = 0; }
        if (n <= 0 || i0 >= b.length) return;
        if (i0 + n > b.length) n = b.length - i0;
        int atk = Math.max(1, (int) (0.006 * rate));
        int rel = Math.max(1, (int) (n * 0.55));
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double k = (double) i / n;
            double f = f0 * Math.pow(f1 / f0, k);   // exponential glide
            ph += f / rate;
            double e = 1.0;
            if (i < atk) e = (double) i / atk;
            else if (i > n - rel) e = (double) (n - i) / rel;
            e = e * e * (3 - 2 * e);
            b[i0 + i] += (float) (amp * e * osc(w, ph));
        }
    }

    /** one-pole low-passed noise burst (alpha near 1 = bright) */
    private static void noise(float[] b, int rate, double t, double dur, double amp, double alpha) {
        int i0 = (int) (t * rate);
        int n = (int) (dur * rate);
        if (i0 < 0) { n += i0; i0 = 0; }
        if (n <= 0 || i0 >= b.length) return;
        if (i0 + n > b.length) n = b.length - i0;
        java.util.Random r = new java.util.Random(12345L + i0);
        double lp = 0;
        int rel = Math.max(1, (int) (n * 0.7));
        for (int i = 0; i < n; i++) {
            double w = r.nextDouble() * 2 - 1;
            lp += alpha * (w - lp);
            double e = i > n - rel ? (double) (n - i) / rel : 1.0;
            b[i0 + i] += (float) (amp * lp * e);
        }
    }

    /** normalise, soft-clip, dither-free 16 bit */
    private static short[] toShort(float[] b) {
        float peak = 1e-6f;
        for (float v : b) if (Math.abs(v) > peak) peak = Math.abs(v);
        float g = 0.92f / peak;
        short[] s = new short[b.length];
        for (int i = 0; i < b.length; i++) {
            float v = (float) Math.tanh(b[i] * g * 1.25);
            s[i] = (short) (int) (Math.max(-1f, Math.min(1f, v)) * 32000f);
        }
        return s;
    }

    private static void writeWav(File f, short[] samples, int rate) throws IOException {
        int data = samples.length * 2;
        ByteBuffer bb = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN);
        bb.put("RIFF".getBytes());
        bb.putInt(36 + data);
        bb.put("WAVE".getBytes());
        bb.put("fmt ".getBytes());
        bb.putInt(16);
        bb.putShort((short) 1);
        bb.putShort((short) 1);
        bb.putInt(rate);
        bb.putInt(rate * 2);
        bb.putShort((short) 2);
        bb.putShort((short) 16);
        bb.put("data".getBytes());
        bb.putInt(data);
        for (short s : samples) bb.putShort(s);
        File tmp = new File(f.getAbsolutePath() + ".tmp");
        FileOutputStream o = new FileOutputStream(tmp);
        o.write(bb.array());
        o.close();
        if (f.exists()) f.delete();
        tmp.renameTo(f);
    }
}
