package app.notes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class EditorActivity extends Activity {
    File dir, note, imgDir;
    EditText et;
    LinearLayout box;
    ArrayList<String> imgs = new ArrayList<>();
    String orig = "";
    boolean imgChanged, gone;
    int sw;

    @Override
    protected void onCreate(Bundle sv) {
        super.onCreate(sv);
        sw = getResources().getDisplayMetrics().widthPixels;
        dir = new File(getIntent().getStringExtra("dir"));
        String np = sv != null && sv.getString("note") != null ? sv.getString("note") : getIntent().getStringExtra("note");
        if (np != null) note = new File(np);
        imgDir = U.imgDir(getFilesDir());

        int adj = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        getWindow().setSoftInputMode(note == null
                ? adj | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                : adj | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(U.dp(this, 4), U.dp(this, 2), U.dp(this, 4), U.dp(this, 2));
        TextView back = U.btn(this, "\u2039", v -> finish());
        back.setTextSize(30);
        TextView del = U.btn(this, "Delete", v -> del());
        del.setTextColor(0xFFE5534B);
        bar.addView(back);
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        bar.addView(U.btn(this, "Image", v -> pick()));
        bar.addView(del);

        et = new EditText(this);
        et.setBackground(null);
        et.setHint("Write something\u2026");
        et.setTextSize(17);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setLineSpacing(0, 1.15f);
        et.setPadding(U.dp(this, 16), U.dp(this, 8), U.dp(this, 16), U.dp(this, 16));
        et.setMinHeight(U.dp(this, 300));

        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.addView(et, new LinearLayout.LayoutParams(-1, -2));
        inner.addView(box, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(inner);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(bar);
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(col);

        if (note != null && note.exists()) {
            orig = U.read(note);
            et.setText(orig);
            imgs = U.imgs(note);
            for (String n : imgs) addImg(n);
        } else {
            note = null;
        }
    }

    @Override
    protected void onPause() {
        save();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        if (note != null) o.putString("note", note.getPath());
    }

    void save() {
        if (gone) return;
        String t = et.getText().toString();
        if (t.trim().isEmpty() && imgs.isEmpty()) {
            if (note != null) U.deleteNote(note, imgDir);
            note = null;
            orig = "";
            return;
        }
        if (note != null && t.equals(orig) && !imgChanged) return;
        if (note == null) note = new File(dir, System.currentTimeMillis() + ".txt");
        U.write(note, t);
        U.saveImgs(note, imgs);
        orig = t;
        imgChanged = false;
    }

    void del() {
        if (et.getText().toString().trim().isEmpty() && imgs.isEmpty()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this).setMessage("Delete this note?")
                .setPositiveButton("Delete", (x, y) -> {
                    gone = true;
                    for (String n : imgs) new File(imgDir, n).delete();
                    if (note != null) {
                        U.side(note).delete();
                        note.delete();
                    }
                    finish();
                })
                .setNegativeButton("Cancel", null).show();
    }

    void pick() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, 1);
    }

    @Override
    protected void onActivityResult(int rq, int rs, Intent data) {
        super.onActivityResult(rq, rs, data);
        if (rq == 1 && rs == RESULT_OK && data != null && data.getData() != null) {
            Uri u = data.getData();
            new Thread(() -> {
                String n = importImg(u);
                if (n != null) runOnUiThread(() -> {
                    imgs.add(n);
                    imgChanged = true;
                    addImg(n);
                    save();
                });
            }).start();
        }
    }

    String importImg(Uri u) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(u)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= 1600) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            Bitmap b;
            try (InputStream in = getContentResolver().openInputStream(u)) {
                b = BitmapFactory.decodeStream(in, null, o);
            }
            if (b == null) return null;
            int rot = 0;
            try (InputStream in = getContentResolver().openInputStream(u)) {
                int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                rot = ori == 6 ? 90 : ori == 3 ? 180 : ori == 8 ? 270 : 0;
            } catch (Exception ignored) {
            }
            float sc = Math.min(1f, 1600f / Math.max(b.getWidth(), b.getHeight()));
            Matrix m = new Matrix();
            m.postScale(sc, sc);
            m.postRotate(rot);
            Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            String name = System.currentTimeMillis() + ".jpg";
            try (FileOutputStream out = new FileOutputStream(new File(imgDir, name))) {
                r.compress(Bitmap.CompressFormat.JPEG, 80, out);
            }
            if (r != b) b.recycle();
            r.recycle();
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    void addImg(String n) {
        String p = new File(imgDir, n).getPath();
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(p, o);
        o.inJustDecodeBounds = false;
        o.inSampleSize = Math.max(1, o.outWidth / sw);
        ImageView v = new ImageView(this);
        v.setAdjustViewBounds(true);
        v.setImageBitmap(BitmapFactory.decodeFile(p, o));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(U.dp(this, 16), U.dp(this, 8), U.dp(this, 16), U.dp(this, 8));
        v.setOnLongClickListener(x -> {
            new AlertDialog.Builder(this).setMessage("Remove this image?")
                    .setPositiveButton("Remove", (a, b) -> {
                        imgs.remove(n);
                        new File(imgDir, n).delete();
                        box.removeView(v);
                        imgChanged = true;
                        save();
                    })
                    .setNegativeButton("Cancel", null).show();
            return true;
        });
        box.addView(v, lp);
    }
}
