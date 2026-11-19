package dev.xduo.plane;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

public class MainActivity extends Activity {

    private GameView view;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new GameView(this);
        setContentView(view);
        applyImmersive();

        // Test hooks:  am start -n dev.xduo.plane/.MainActivity \
        //     --ez autostart true --ez autopilot true --ez god true --ei report 3
        // or a file:  run-as dev.xduo.plane sh -c 'echo autostart=1 autopilot=1 > files/testhook'
        Bundle b = getIntent() == null ? null : getIntent().getExtras();
        if (b == null || b.isEmpty()) b = readTestHookFile();
        if (b != null) {
            Log.i(GameView.TAG, "hooks: " + b);
            if (b.getBoolean("god", false)) view.setGod(true);
            if (b.getBoolean("autopilot", false)) view.setAutopilot(true);
            int df = b.getInt("diff", -1);
            if (df >= 0) view.setDiff(df);
            int rep = b.getInt("report", 0);
            if (rep > 0) view.setReport(rep);
            final int delay = b.getInt("delay", 300);
            if (b.getBoolean("autostart", false)) autoStart(delay);
            String pu = b.getString("powerup");
            if (pu != null) view.hookPower(powerKind(pu));
            String pu2 = b.getString("powerup2");
            if (pu2 != null) view.hookPower(powerKind(pu2));
            int pa = b.getInt("powerall", 0);
            if (pa > 0) view.hookPowerAll(pa);
            int spawn = b.getInt("spawn", -1);
            if (spawn >= 0) view.hookSpawn(spawn);
            int ev = b.getInt("event", -1);
            if (ev >= 0) view.hookEvent(ev);
            int sup = b.getInt("super", 0);
            if (sup > 0) view.hookSuper(sup);
            int bomb = b.getInt("bomb", 0);
            if (bomb > 0) view.hookBomb(bomb);
            int sa = b.getInt("superall", 0);
            if (sa > 0) view.hookSuperAll(sa);
            int gz = b.getInt("graze", 0);
            if (gz > 0) view.hookGraze(gz);
            int wv = b.getInt("wave", 0);
            if (wv > 0) view.hookWave(wv);
            int bs = b.getInt("boss", 0);
            if (bs > 0) view.hookBoss(bs);
            int show = b.getInt("show", 0);
            if (show > 0) view.hookShow(show);
            int die = b.getInt("autodie", 0);
            if (die > 0) view.setAutoDie(die);
            if (b.containsKey("vibrate")) view.setVibe(b.getBoolean("vibrate", true));
            if (b.containsKey("music")) view.setMusic(b.getBoolean("music", true));
            if (b.containsKey("sound")) view.setSound(b.getBoolean("sound", true));
        }
    }

    private void autoStart(int ms) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { view.startGame(); }
        }, ms);
    }

    static int powerKind(String s) {
        char c = s.length() == 0 ? 'P' : Character.toUpperCase(s.charAt(0));
        switch (c) {
            case 'S': return GameView.PW_SHIELD;
            case 'H': return GameView.PW_HEAL;
            case 'B': return GameView.PW_BOMB;
            case 'X': return GameView.PW_SCORE;
            case 'F': return GameView.PW_RAPID;
            case 'L': return GameView.PW_LASER;
            case 'T': return GameView.PW_HOMING;
            default: return GameView.PW_WEAPON;
        }
    }

    /** Test hook file so a shell (which Android 14+ may block from starting activities in
        the foreground) can still drive the game: files/testhook, key=value pairs. */
    private Bundle readTestHookFile() {
        try {
            java.io.File f = new java.io.File(getFilesDir(), "testhook");
            if (!f.exists()) return null;
            byte[] buf = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int n = in.read(buf);
            in.close();
            f.delete();
            Bundle b = new Bundle();
            for (String kv : new String(buf, 0, Math.max(0, n)).split("[ \\n\\r]+")) {
                int i = kv.indexOf('=');
                if (i <= 0) continue;
                String k = kv.substring(0, i), v = kv.substring(i + 1);
                if (v.matches("-?\\d+")) b.putInt(k, Integer.parseInt(v));
                else if (v.equals("true") || v.equals("false")) b.putBoolean(k, Boolean.parseBoolean(v));
                else b.putString(k, v);
            }
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    /** hide the status/nav bars so the play field is the whole screen */
    private void applyImmersive() {
        try {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        } catch (Throwable ignored) {}
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    @Override protected void onResume() {
        super.onResume();
        Log.i(GameView.TAG, "onResume");
        view.resume();
        view.resumeGame();
        view.requestFocus();
        applyImmersive();
    }

    @Override protected void onPause() {
        super.onPause();
        view.pauseGame();
    }

    @Override protected void onStop() {
        super.onStop();
        view.audio.stopMusic();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        view.release();
    }

    @Override public void onBackPressed() {
        if (!view.onBackPressed()) super.onBackPressed();
    }
}
