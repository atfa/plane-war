package dev.termux.planewar;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/** Offline high-score board: top 10 runs, kept in SharedPreferences. */
public class Scores {

    public static class Entry {
        public String name;
        public int score, kills, time, diff;
        public long date;
    }

    private static final int MAX = 10;
    private static final String PREF = "planewar_board";
    private static final String KEY = "board";
    private static final String KEY_NAME = "name";

    public static String lastName(Context c) {
        return c.getSharedPreferences(PREF, 0).getString(KEY_NAME, "飞行员");
    }

    public static void saveLastName(Context c, String n) {
        c.getSharedPreferences(PREF, 0).edit().putString(KEY_NAME, n).commit();
    }

    public static List<Entry> load(Context c) {
        List<Entry> out = new ArrayList<Entry>();
        String raw = c.getSharedPreferences(PREF, 0).getString(KEY, "");
        if (raw == null || raw.isEmpty()) return out;
        for (String line : raw.split("\n")) {
            String[] p = line.split("\\|");
            if (p.length < 5) continue;
            Entry e = new Entry();
            e.name = p[0];
            try {
                e.score = Integer.parseInt(p[1]);
                e.kills = Integer.parseInt(p[2]);
                e.time = Integer.parseInt(p[3]);
                e.date = Long.parseLong(p[4]);
                if (p.length > 5) e.diff = Integer.parseInt(p[5]);
            } catch (NumberFormatException ex) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    private static void save(Context c, List<Entry> list) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.name).append('|').append(e.score).append('|')
              .append(e.kills).append('|').append(e.time).append('|').append(e.date)
              .append('|').append(e.diff);
        }
        // commit(), not apply(): the run ends right here and the harness (or the OS) may
        // kill the process before an async write reaches the disk - the board must survive.
        c.getSharedPreferences(PREF, 0).edit().putString(KEY, sb.toString()).commit();
    }

    public static int best(Context c) {
        List<Entry> l = load(c);
        int b = 0;
        for (Entry e : l) if (e.score > b) b = e.score;
        return b;
    }

    /** @return index in the board if the run made the top 10, -1 otherwise */
    public static int submit(Context c, String name, int score, int kills, int timeSec, int diff) {
        if (score <= 0) return -1;
        List<Entry> l = load(c);
        Entry e = new Entry();
        e.name = clean(name);
        e.score = score;
        e.kills = kills;
        e.time = timeSec;
        e.diff = diff;
        e.date = System.currentTimeMillis();
        l.add(e);
        Collections.sort(l, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) { return b.score - a.score; }
        });
        while (l.size() > MAX) l.remove(l.size() - 1);
        save(c, l);
        return l.indexOf(e);
    }

    public static void rename(Context c, int index, String name) {
        if (index < 0) return;
        List<Entry> l = load(c);
        if (index >= l.size()) return;
        l.get(index).name = clean(name);
        save(c, l);
        saveLastName(c, l.get(index).name);
    }

    public static String date(long ms) {
        return new SimpleDateFormat("MM-dd HH:mm").format(new Date(ms));
    }

    private static String clean(String n) {
        if (n == null) return "飞行员";
        n = n.replace("|", " ").replace("\n", " ").trim();
        if (n.isEmpty()) n = "飞行员";
        return n.length() > 8 ? n.substring(0, 8) : n;
    }
}
