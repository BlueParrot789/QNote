package app.notes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.text.DateFormat;
import java.util.*;
import java.util.function.Consumer;

public class MainActivity extends Activity {
    File root, cur;
    File[] items = new File[0];
    TextView head;
    BaseAdapter ad;

    @Override
    protected void onCreate(Bundle sv) {
        super.onCreate(sv);
        root = U.root(getFilesDir());
        cur = root;
        if (sv != null && sv.getString("cur") != null) {
            File f = new File(sv.getString("cur"));
            if (f.isDirectory()) cur = f;
        }

        head = U.tv(this, "", 22, true);
        head.setSingleLine();
        head.setEllipsize(TextUtils.TruncateAt.END);
        head.setOnClickListener(v -> up());

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(U.dp(this, 16), U.dp(this, 6), U.dp(this, 4), U.dp(this, 6));
        bar.addView(head, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(U.btn(this, "+ Folder", v -> ask("New folder", "", s -> {
            new File(cur, s).mkdirs();
            refresh();
        })));
        bar.addView(U.btn(this, "+ Note", v -> open(null)));

        ad = new BaseAdapter() {
            public int getCount() { return items.length; }
            public Object getItem(int i) { return items[i]; }
            public long getItemId(int i) { return i; }

            public View getView(int i, View v, ViewGroup p) {
                if (v == null) {
                    LinearLayout row = new LinearLayout(MainActivity.this);
                    row.setOrientation(LinearLayout.VERTICAL);
                    row.setPadding(U.dp(MainActivity.this, 16), U.dp(MainActivity.this, 12),
                            U.dp(MainActivity.this, 16), U.dp(MainActivity.this, 12));
                    TextView x = U.tv(MainActivity.this, "", 17, false);
                    x.setSingleLine();
                    x.setEllipsize(TextUtils.TruncateAt.END);
                    TextView y = U.tv(MainActivity.this, "", 13, false);
                    y.setAlpha(.55f);
                    row.addView(x);
                    row.addView(y);
                    v = row;
                }
                LinearLayout r = (LinearLayout) v;
                TextView a = (TextView) r.getChildAt(0);
                TextView c = (TextView) r.getChildAt(1);
                File f = items[i];
                if (f.isDirectory()) {
                    a.setText("\uD83D\uDCC1  " + f.getName());
                    a.setTypeface(Typeface.DEFAULT_BOLD);
                    int n = U.list(f).length;
                    c.setText(n == 1 ? "1 item" : n + " items");
                } else {
                    a.setText(U.title(f));
                    a.setTypeface(Typeface.DEFAULT);
                    c.setText(DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(f.lastModified())));
                }
                return v;
            }
        };

        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setAdapter(ad);
        lv.setOnItemClickListener((p, v, i, id) -> {
            File f = items[i];
            if (f.isDirectory()) {
                cur = f;
                refresh();
            } else open(f);
        });
        lv.setOnItemLongClickListener((p, v, i, id) -> {
            menu(items[i]);
            return true;
        });

        TextView empty = U.tv(this, "Nothing here yet", 15, false);
        empty.setAlpha(.5f);
        empty.setGravity(Gravity.CENTER);
        FrameLayout fl = new FrameLayout(this);
        fl.addView(lv);
        fl.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        lv.setEmptyView(empty);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(bar);
        col.addView(fl, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(col);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        o.putString("cur", cur.getPath());
    }

    @Override
    public void onBackPressed() {
        if (cur.equals(root)) super.onBackPressed();
        else up();
    }

    void refresh() {
        if (!cur.isDirectory()) cur = root;
        items = U.list(cur);
        head.setText(cur.equals(root) ? "Notes" : "\u2039  " + cur.getName());
        ad.notifyDataSetChanged();
    }

    void up() {
        if (!cur.equals(root)) {
            cur = cur.getParentFile();
            refresh();
        }
    }

    void open(File n) {
        Intent i = new Intent(this, EditorActivity.class).putExtra("dir", cur.getPath());
        if (n != null) i.putExtra("note", n.getPath());
        startActivity(i);
    }

    void ask(String title, String init, Consumer<String> ok) {
        EditText e = new EditText(this);
        e.setSingleLine();
        e.setText(init);
        e.setSelection(e.length());
        FrameLayout w = new FrameLayout(this);
        w.setPadding(U.dp(this, 20), U.dp(this, 8), U.dp(this, 20), 0);
        w.addView(e);
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle(title).setView(w)
                .setPositiveButton("OK", (x, y) -> {
                    String s = e.getText().toString()
                            .replaceAll("[\\\\/:*?\"<>|]", "")
                            .replaceAll("^\\.+", "").trim();
                    if (!s.isEmpty()) ok.accept(s);
                })
                .setNegativeButton("Cancel", null).create();
        dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
    }

    void menu(File f) {
        boolean dir = f.isDirectory();
        String[] o = dir ? new String[]{"Rename", "Delete"} : new String[]{"Move", "Delete"};
        new AlertDialog.Builder(this).setItems(o, (x, w) -> {
            if (w == 1) {
                new AlertDialog.Builder(this)
                        .setMessage(dir ? "Delete this folder and everything in it?" : "Delete this note?")
                        .setPositiveButton("Delete", (y, z) -> {
                            File imgs = U.imgDir(getFilesDir());
                            if (dir) U.deleteDir(f, imgs);
                            else U.deleteNote(f, imgs);
                            refresh();
                        })
                        .setNegativeButton("Cancel", null).show();
            } else if (dir) {
                ask("Rename", f.getName(), s -> {
                    File t = new File(cur, s);
                    if (!t.exists()) f.renameTo(t);
                    refresh();
                });
            } else move(f);
        }).show();
    }

    void move(File n) {
        ArrayList<File> ds = new ArrayList<>();
        ds.add(root);
        collect(root, ds);
        String[] names = new String[ds.size()];
        for (int i = 0; i < names.length; i++) {
            File d = ds.get(i);
            names[i] = d.equals(root) ? "Notes" : d.getPath().substring(root.getPath().length() + 1);
        }
        new AlertDialog.Builder(this).setTitle("Move to").setItems(names, (x, w) -> {
            File t = ds.get(w);
            if (!t.equals(cur)) {
                File side = U.side(n);
                n.renameTo(new File(t, n.getName()));
                if (side.exists()) side.renameTo(new File(t, side.getName()));
            }
            refresh();
        }).show();
    }

    void collect(File d, List<File> out) {
        File[] fs = d.listFiles(f -> f.isDirectory());
        if (fs == null) return;
        Arrays.sort(fs);
        for (File f : fs) {
            out.add(f);
            collect(f, out);
        }
    }
}
