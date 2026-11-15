package dev.termux.planewar;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** Haptics that mirror game events. Every effect is a single call, safe on API 24+. */
public class Vibro {

    private final Vibrator v;
    private boolean on = true;

    public Vibro(Context c) {
        Vibrator tmp = null;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                tmp = vm == null ? null : vm.getDefaultVibrator();
            } else {
                tmp = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            }
        } catch (Throwable t) {
            tmp = null;
        }
        v = tmp;
    }

    public boolean on() { return on; }
    public void setOn(boolean on) { this.on = on; }

    public void tap(int ms) { pattern(new long[]{0, ms}, -1); }

    /** grazing a bullet / wall scrape */
    public void tick() { pattern(new long[]{0, 12}, -1); }

    /** enemy destroyed */
    public void kill() { pattern(new long[]{0, 18}, -1); }

    /** heavy enemy destroyed - deep thud */
    public void bigKill() { pattern(new long[]{0, 70, 30, 45}, -1); }

    /** power-up picked up - two light clicks */
    public void power() { pattern(new long[]{0, 18, 40, 26}, -1); }

    /** shield absorbs a hit */
    public void shield() { pattern(new long[]{0, 30, 25, 60}, -1); }

    /** player took damage */
    public void hurt() { pattern(new long[]{0, 130, 40, 60}, -1); }

    /** EMP / bomb */
    public void bomb() { pattern(new long[]{0, 220, 60, 120}, -1); }

    /** special event announced */
    public void event() { pattern(new long[]{0, 40, 60, 40}, -1); }

    /** game over - long rumble */
    public void gameOver() { pattern(new long[]{0, 160, 90, 160, 90, 320}, -1); }

    public void stop() {
        if (v != null) try { v.cancel(); } catch (Throwable ignored) {}
    }

    private void pattern(long[] seq, int repeat) {
        if (!on || v == null) return;
        try {
            if (!v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                long[] ms = new long[seq.length];
                for (int i = 0; i < seq.length; i++) ms[i] = Math.max(1, seq[i]);
                v.vibrate(VibrationEffect.createWaveform(ms, repeat));
            } else {
                v.vibrate(seq, repeat);
            }
        } catch (Throwable ignored) {}
    }
}
