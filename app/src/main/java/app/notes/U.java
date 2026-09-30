package app.notes;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class U {
    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + .5f);
    }

    static TextView tv(Context c, String s, int sp, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static TextView btn(Context c, String s, View.OnClickListener l) {
        TextView t = tv(c, s, 16, true);
        t.setTextColor(0xFF4C8DFF);
        t.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        t.setOnClickListener(l);
        return t;
    }

    static File root(File filesDir) {
        File r = new File(filesDir, "n");
        r.mkdirs();
        return r;
    }

    static File imgDir(File filesDir) {
        File d = new File(filesDir, "i");
        d.mkdirs();
        return d;
    }

    static String read(File f, int max) {
        try (FileInputStream in = new FileInputStream(f)) {
            int n = (int) Math.min(f.length(), (long) max);
            byte[] b = new byte[n];
            int off = 0, r;
            while (off < n && (r = in.read(b, off, n - off)) > 0) off += r;
            return new String(b, 0, off, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    static String read(File f) {
        return read(f, Integer.MAX_VALUE);
    }

    static void write(File f, String s) {
        File t = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(t)) {
            o.write(s.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return;
        }
        t.renameTo(f);
    }

    static File side(File note) {
        String p = note.getPath();
        return new File(p.substring(0, p.length() - 4) + ".i");
    }

    static ArrayList<String> imgs(File note) {
        ArrayList<String> l = new ArrayList<>();
        for (String s : read(side(note)).split("\n")) if (!s.isEmpty()) l.add(s);
        return l;
    }

    static void saveImgs(File note, List<String> l) {
        File s = side(note);
        if (l.isEmpty()) s.delete();
        else write(s, String.join("\n", l));
    }

    static String title(File note) {
        for (String s : read(note, 300).split("\n")) {
            s = s.trim();
            if (!s.isEmpty()) return s;
        }
        return "Untitled";
    }

    static File[] list(File dir) {
        File[] fs = dir.listFiles(f -> f.isDirectory() || f.getName().endsWith(".txt"));
        if (fs == null) return new File[0];
        Arrays.sort(fs, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            if (a.isDirectory()) return a.getName().compareToIgnoreCase(b.getName());
            return Long.compare(b.lastModified(), a.lastModified());
        });
        return fs;
    }

    static void deleteNote(File note, File imgDir) {
        for (String n : imgs(note)) new File(imgDir, n).delete();
        side(note).delete();
        note.delete();
    }

    static void deleteDir(File d, File imgDir) {
        File[] fs = d.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isDirectory()) deleteDir(f, imgDir);
                else if (f.getName().endsWith(".txt")) deleteNote(f, imgDir);
                else f.delete();
            }
        }
        d.delete();
    }
}
