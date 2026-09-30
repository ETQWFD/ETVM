package com.et.storage;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * ET文件传输 · 单向文件导入（ET协会出品 · © ET）
 * 真机/电脑 → 虚拟机 单向；虚拟机不可反向删除真机文件。
 * os 目录：/storage/emulated/0/Download/os/（删除后自动重建）。
 */
public class MainActivity extends Activity {
    static final String OS_DIR = Environment.getExternalStorageDirectory() + "/Download/os/";
    TextView listView, vmView, clipView;
    File osDir;
    Button importBtn, toVmBtn, refreshBtn, clipBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        osDir = new File(OS_DIR);
        ensureOsDir();
        setContentView(buildUi());
        refresh();
    }

    void ensureOsDir() {
        if (osDir.exists() && osDir.isDirectory()) return;
        if (osDir.mkdirs()) return;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, ".keep");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/os/");
                getContentResolver().insert(MediaStore.Downloads.getContentUri("external"), v);
                osDir = new File(OS_DIR);
                osDir.mkdirs();
            }
        } catch (Exception ignored) {}
    }

    View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0B1020);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(24, 30, 24, 30);
        sv.addView(p);

        TextView title = new TextView(this);
        title.setText("ET文件传输");
        title.setTextSize(22);
        title.setTextColor(0xFFE8EEF7);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        p.addView(title);

        TextView sub = new TextView(this);
        sub.setText("真机 → 虚拟机 单向导入 · ET协会出品 © ET");
        sub.setTextSize(12);
        sub.setTextColor(0xFF7B8BB0);
        sub.setPadding(0, 4, 0, 18);
        p.addView(sub);

        p.addView(section("os 目录（真机共享）"));
        listView = new TextView(this);
        listView.setTextColor(0xFFB9C6DF);
        listView.setTextSize(13);
        listView.setTextIsSelectable(true);
        listView.setPadding(0, 6, 0, 6);
        p.addView(listView);

        importBtn = btn("从手机导入文件 → os 目录");
        importBtn.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            try { startActivityForResult(i, 7); } catch (Exception e) { toast("无法打开文件选择器"); }
        });
        p.addView(importBtn);

        toVmBtn = btn("复制所选文件 → 虚拟机");
        toVmBtn.setOnClickListener(v -> copyToVm());
        p.addView(toVmBtn);

        refreshBtn = btn("刷新列表");
        refreshBtn.setOnClickListener(v -> refresh());
        p.addView(refreshBtn);

        p.addView(section("虚拟机内（已导入）"));
        vmView = new TextView(this);
        vmView.setTextColor(0xFFB9C6DF);
        vmView.setTextSize(13);
        vmView.setTextIsSelectable(true);
        vmView.setPadding(0, 6, 0, 6);
        p.addView(vmView);

        p.addView(section("剪贴板 · 单向"));
        clipView = new TextView(this);
        clipView.setTextColor(0xFF9FD7A8);
        clipView.setTextSize(13);
        clipView.setPadding(0, 6, 0, 6);
        p.addView(clipView);

        clipBtn = btn("把剪贴板内容同步到虚拟机");
        clipBtn.setOnClickListener(v -> {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                String t = "";
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                    t = cm.getPrimaryClip().getItemAt(0).coerceToText(this).toString();
                }
                if (t.isEmpty()) { toast("剪贴板为空"); return; }
                File f = new File(OS_DIR, "clipboard.txt");
                try (OutputStream o = new FileOutputStream(f)) { o.write(t.getBytes("UTF-8")); }
                toast("已同步到虚拟机（os/clipboard.txt）");
                refresh();
            } catch (Exception e) { toast("同步失败：" + e.getMessage()); }
        });
        p.addView(clipBtn);

        TextView tip = new TextView(this);
        tip.setText("单向说明：只能从手机/电脑向虚拟机发送文件，虚拟机无法反向删除或修改真机文件。\nos 目录被删除后会自动重建。");
        tip.setTextSize(11);
        tip.setTextColor(0xFF7B8BB0);
        tip.setPadding(0, 16, 0, 0);
        p.addView(tip);
        return sv;
    }

    TextView section(String s) {
        TextView t = new TextView(this);
        t.setText("▍" + s);
        t.setTextSize(14);
        t.setTextColor(0xFFE8EEF7);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setPadding(0, 14, 0, 4);
        return t;
    }

    Button btn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(14);
        b.setTextColor(0xFFE8EEF7);
        b.setBackgroundColor(0xFF1E2D50);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 8;
        b.setLayoutParams(lp);
        return b;
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    void refresh() {
        ensureOsDir();
        StringBuilder sb = new StringBuilder();
        File[] fs = osDir.listFiles();
        if (fs != null && fs.length > 0) {
            for (File f : fs) if (f.isFile()) sb.append("· ").append(f.getName()).append("  ").append(f.length() / 1024).append("KB\n");
        } else sb.append("（空 · 把手机文件发到这里）\n");
        listView.setText(sb.toString());

        StringBuilder vb = new StringBuilder();
        File vdir = new File(getExternalFilesDir(null), "vm");
        File[] vfs = vdir.listFiles();
        if (vfs != null && vfs.length > 0) {
            for (File f : vfs) if (f.isFile()) vb.append("· ").append(f.getName()).append("  ").append(f.length() / 1024).append("KB\n");
        } else vb.append("（空）\n");
        vmView.setText(vb.toString());

        try {
            File c = new File(OS_DIR, "clipboard.txt");
            if (c.exists()) {
                byte[] d = new byte[(int) Math.min(c.length(), 4096)];
                try (InputStream i = new FileInputStream(c)) { int n = i.read(d); clipView.setText(new String(d, 0, n > 0 ? n : 0, "UTF-8")); }
            } else clipView.setText("（无 · 点下方按钮把剪贴板同步进来）");
        } catch (Exception e) { clipView.setText("（读取失败）"); }
    }

    void copyToVm() {
        File[] fs = osDir.listFiles();
        if (fs == null || fs.length == 0) { toast("os 目录为空"); return; }
        File vdir = new File(getExternalFilesDir(null), "vm");
        vdir.mkdirs();
        int ok = 0;
        for (File f : fs) {
            if (!f.isFile()) continue;
            try {
                try (InputStream i = new FileInputStream(f); OutputStream o = new FileOutputStream(new File(vdir, f.getName()))) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
                }
                ok++;
            } catch (Exception ignored) {}
        }
        toast(ok + " 个文件已复制进虚拟机");
        refresh();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 7 && res == RESULT_OK && data != null && data.getData() != null) {
            try {
                Uri u = data.getData();
                String name = queryName(u);
                if (name == null || name.isEmpty()) name = "file_" + System.currentTimeMillis() + ".dat";
                ensureOsDir();
                try (InputStream i = getContentResolver().openInputStream(u);
                     OutputStream o = new FileOutputStream(new File(OS_DIR, name))) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
                }
                toast("已导入 os 目录：" + name);
                refresh();
            } catch (Exception e) { toast("导入失败：" + e.getMessage()); }
        }
    }

    String queryName(Uri u) {
        String n = null;
        try (android.database.Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ix = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (ix >= 0) n = c.getString(ix);
            }
        } catch (Exception ignored) {}
        return n;
    }
}
