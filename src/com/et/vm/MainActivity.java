package com.et.vm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ET虚拟机 v3.0.0 · 原生 Java 版（完全无 WebView）
 * 内置 ET-OS 7.0 精简系统（32 位）· ROM 商店 · 连接储存 · 签名防注入 · 设备授权码
 * ET协会出品 · © ET
 */
public class MainActivity extends Activity {

    /* 签名防注入：本应用真实签名哈希（构建后回填，见 build.sh 两遍打包） */
    static final String EXPECTED_SIG = "4a7eee440f0a87274aff7ddbf690d9c8f5422826e71b0234a18727c0950d6a6b";

    /* 配色 */
    static final int BG = 0xFF070B15, CARD = 0xFF132246, CARD2 = 0xFF0D1729,
            LINE = 0xFF1E2E4F, ACCENT = 0xFF00E5FF, BLUE = 0xFF2F7BFF,
            TXT = 0xFFE8EEFB, SUB = 0xFF7482A3, OK = 0xFF2EE6A8,
            BAD = 0xFFFF6B6B, WARN = 0xFFFFC44D, GREEN = 0xFF00B894;

    private FrameLayout holder;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int scr = 0; // 0 welcome 1 home 2 wizard 3 settings 4 boot 5 vm 6 store 7 romstore 8 files 9 dev 10 about

    private SharedPreferences sp;
    private JSONArray vms = new JSONArray();
    private JSONObject curVm = null;
    private String androidId = "";
    private String sigHex = "";

    /* 向导状态 */
    private JSONObject wizard = new JSONObject();
    private JSONObject romRes = null;
    private final Map<String, Boolean> perms = new java.util.HashMap<>();
    private int wizardStep = 1;

    private JSONArray catalog = new JSONArray();
    private final Map<String, JSONObject> romDls = new java.util.HashMap<>();
    private final Map<String, Integer> dlTimers = new java.util.HashMap<>();
    private JSONArray importCache = new JSONArray();

    private String filesTab = "vm";
    private int pendingPermKind = -1;
    private static final int RC_FILE = 1001, RC_STORAGE = 1002, RC_NOTIFY = 1003,
            RC_OVERLAY_SETTINGS = 1004, RC_INSTALL_SETTINGS = 1005;
    private String pendingPickKind = "", pendingPickTarget = "";

    /* 控件引用 */
    private TextView tvTyped, vmClock, vmBadge, vmGreet, detectCard, romNextBtn, permNextBtn;
    private LinearLayout vmAppGrid, bootLinesBox, filesList, romList, storeList, homeVmList;
    private EditText vmNameEdit, vmCustomVerEdit, vmVersionEdit;
    private List<Button> bitsBtns = new ArrayList<>(), verBtns = new ArrayList<>(), modelBtns = new ArrayList<>();
    private TextView fpsVal, animHint;
    private SeekBar fpsBar;
    private CheckBox optGapps, optXposed, optRoot;
    private List<Button> permBtns = new ArrayList<>();
    private LinearLayout summaryRows;

    private boolean typedDone = false;

    /* ================= 生命周期 ================= */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        sp = getSharedPreferences("et_vm_store", MODE_PRIVATE);
        androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null) androidId = "etvm";
        sigHex = computeSigHash();

        /* 签名防注入：签名不匹配 = 被重打包/注入，拒绝运行 */
        if (!EXPECTED_SIG.equals("PENDING") && !EXPECTED_SIG.equalsIgnoreCase(sigHex)) {
            setContentView(blockView("应用签名校验失败\n检测到应用已被重打包或注入不合规代码\n为保护您的虚拟机数据，已拒绝运行。"));
            return;
        }

        holder = new FrameLayout(this);
        holder.setBackgroundColor(BG);
        setContentView(holder);

        loadVms();
        seedBuiltIn();
        extractBundled();
        show(0);
        typeWelcome();
        clockTick();
    }

    private View blockView(String msg) {
        TextView t = new TextView(this);
        t.setText(msg);
        t.setTextColor(BAD);
        t.setTextSize(16);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(BG);
        return t;
    }

    /* ================= 工具 ================= */
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private void vib(int ms) { Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE); if (v != null && v.hasVibrator()) v.vibrate(ms); }

    private TextView text(String s, float sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (style == 1) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
    private TextView subText(String s) { return text(s, 12, SUB, 0); }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }
    private GradientDrawable roundStroke(int color, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setStroke(dp(stroke), ACCENT);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private Button btn(String s, int bg, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(bg == BLUE || bg == ACCENT ? 0xFF03121C : TXT);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(bg, 12));
        b.setOnClickListener(l);
        return b;
    }
    private Button btnPrimary(String s, View.OnClickListener l) { return btn(s, BLUE, l); }
    private Button btnGhost(String s, View.OnClickListener l) {
        Button b = btn(s, 0x1100E5FF, l);
        b.setBackground(roundStroke(0x1100E5FF, 1, 12));
        b.setTextColor(ACCENT);
        return b;
    }

    private LinearLayout rowWrap() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(CARD, 16));
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        return c;
    }
    private TextView sectionTitle(String s) {
        return text(s, 13, SUB, 1);
    }
    private LinearLayout vpad(LinearLayout p, int top) { p.setPadding(dp(16), dp(top), dp(16), dp(96)); return p; }

    private void addDetectRow(LinearLayout parent, String k, String v, int vColor) {
        LinearLayout row = rowWrap();
        row.setPadding(0, dp(8), 0, dp(8));
        TextView kt = text(k, 13, SUB, 0);
        TextView vt = text(v, 13, vColor, 1);
        row.addView(kt);
        row.addView(vt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        parent.addView(row);
    }

    /* ================= 数据 ================= */
    private void loadVms() {
        try { vms = new JSONArray(sp.getString("vms", "[]")); } catch (Exception e) { vms = new JSONArray(); }
    }
    private void saveVms() { sp.edit().putString("vms", vms.toString()).apply(); }

    private void seedBuiltIn() {
        if (vms.length() > 0) return;
        try {
            JSONObject vm = new JSONObject();
            vm.put("id", 1);
            vm.put("name", "ET-OS 7.0 (内置)");
            vm.put("bits", "32");
            vm.put("ver", "Android 7.0");
            vm.put("model", "内置精简机型");
            JSONObject rom = new JSONObject();
            rom.put("name", "内置 ET-OS 7.0 精简系统");
            rom.put("size", 4218001L);
            rom.put("bits", "32");
            rom.put("bundled", true);
            vm.put("rom", rom);
            vm.put("version", "ET-OS 7.0 (2026.09)");
            vm.put("fps", 60);
            vm.put("anim", "ET 经典");
            vm.put("animPath", "");
            vm.put("gapps", false);
            vm.put("xposed", false);
            vm.put("root", false);
            vm.put("firstBoot", false); // 内置系统已安装好：直接进桌面
            JSONArray inst = new JSONArray();
            JSONObject st = new JSONObject();
            st.put("label", "连接储存");
            st.put("pkg", "com.et.storage");
            st.put("abi", "内置 · 真机共享");
            st.put("color", "tile-orange");
            inst.put(st);
            vm.put("installed", inst);
            vm.put("created", System.currentTimeMillis());
            vms.put(vm);
            saveVms();
        } catch (Exception ignored) {}
    }

    private void extractBundled() {
        File dir = new File(getExternalFilesDir(null), "roms");
        File zip = new File(dir, "etos-7.0-x86.zip");
        if (zip.exists()) return;
        try {
            if (!dir.exists()) dir.mkdirs();
            InputStream in = getAssets().open("etos/etos-7.0-x86.zip");
            OutputStream out = new FileOutputStream(zip);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close(); out.close();
            /* 解出壁纸 */
            try (ZipFile zf = new ZipFile(zip)) {
                ZipEntry we = zf.getEntry("wallpaper.png");
                if (we != null) {
                    InputStream wi = zf.getInputStream(we);
                    OutputStream wo = new FileOutputStream(new File(dir, "wallpaper.png"));
                    byte[] wb = new byte[65536];
                    int w;
                    while ((w = wi.read(wb)) > 0) wo.write(wb, 0, w);
                    wi.close(); wo.close();
                }
            }
        } catch (Exception ignored) {}
    }

    private String bundledZipPath() {
        return new File(getExternalFilesDir(null), "roms/etos-7.0-x86.zip").getAbsolutePath();
    }

    /* 签名哈希（防注入） */
    private String computeSigHash() {
        try {
            PackageInfo pi;
            if (Build.VERSION.SDK_INT >= 28) {
                pi = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                Signature[] ss = pi.signingInfo.getApkContentsSigners();
                if (ss.length > 0) return sha256Hex(ss[0].toByteArray());
            } else {
                pi = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
                if (pi.signatures.length > 0) return sha256Hex(pi.signatures[0].toByteArray());
            }
        } catch (Exception ignored) {}
        return "NONE";
    }
    private String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) { return "NONE"; }
    }

    /* ================= 屏幕切换 ================= */
    private void show(int s) {
        scr = s;
        holder.removeAllViews();
        switch (s) {
            case 0: holder.addView(buildWelcome()); break;
            case 1: holder.addView(buildHome()); break;
            case 2: holder.addView(buildWizard()); break;
            case 3: holder.addView(buildSettings()); break;
            case 4: holder.addView(buildBoot()); break;
            case 5: holder.addView(buildVm()); break;
            case 6: holder.addView(buildStore()); break;
            case 7: holder.addView(buildRomStore()); break;
            case 8: holder.addView(buildFiles()); break;
            case 9: holder.addView(buildDev()); break;
            case 10: holder.addView(buildAbout()); break;
        }
    }

    /* ================= 欢迎页 ================= */
    private View buildWelcome() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER);
        p.setBackgroundColor(0xFF0A1120);

        TextView chip = new TextView(this);
        chip.setText("ET");
        chip.setTextSize(34);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setTextColor(Color.WHITE);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(roundStroke(0xFF0E1830, 2, 22));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(112), dp(112));
        cl.gravity = Gravity.CENTER;
        cl.bottomMargin = dp(18);
        p.addView(chip, cl);

        TextView t1 = text("ET虚拟机", 26, TXT, 1);
        t1.setGravity(Gravity.CENTER);
        t1.setLetterSpacing(0.08f);
        p.addView(t1);
        TextView t2 = subText("ET协会出品 · 原生系统引擎");
        t2.setGravity(Gravity.CENTER);
        t2.setPadding(0, dp(4), 0, dp(18));
        p.addView(t2);

        tvTyped = text("", 13, ACCENT, 0);
        tvTyped.setGravity(Gravity.CENTER);
        tvTyped.setPadding(dp(20), 0, dp(20), dp(12));
        tvTyped.setMinHeight(dp(44));
        p.addView(tvTyped);

        Button b = btnPrimary("进入首页", v -> show(1));
        b.setTextSize(15);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(220), dp(50));
        bl.gravity = Gravity.CENTER;
        p.addView(b, bl);

        TextView f = text("© ET · v3.0.0 · 内置 ET-OS 7.0", 11, 0xFF3E4C68, 0);
        f.setGravity(Gravity.CENTER);
        f.setPadding(0, dp(24), 0, 0);
        p.addView(f);
        return p;
    }

    private void typeWelcome() {
        final String msg = "欢迎进入 ET 虚拟机 · 原生引擎\n内置 ET-OS 7.0 (32位) 已就绪\nROM 商店 · 连接储存 · 防注入";
        final int[] i = {0};
        typedDone = false;
        h.post(new Runnable() {
            @Override public void run() {
                if (tvTyped == null) return;
                if (i[0] <= msg.length()) {
                    tvTyped.setText(msg.substring(0, i[0]));
                    i[0]++;
                    h.postDelayed(this, 40);
                } else {
                    tvTyped.setText(msg);
                    typedDone = true;
                }
            }
        });
    }

    /* ================= 首页 ================= */
    private View buildHome() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        vpad(p, 18);
        sv.addView(p);

        LinearLayout top = rowWrap();
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.addView(text("欢迎回来", 20, TXT, 1));
        tt.addView(subText("ET 虚拟机管理台 · 原生 Java 引擎"));
        top.addView(tt);
        TextView badge = text("32位内置", 11, 0xFF7FB3FF, 1);
        badge.setBackground(round(0xFF0E1830, 99));
        badge.setPadding(dp(12), dp(6), dp(12), dp(6));
        top.addView(badge, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams) badge.getLayoutParams()).gravity = Gravity.CENTER_VERTICAL;
        p.addView(top);

        String[][] menus = {
                {"01", "我的机器", "管理已创建的虚拟机", "home"},
                {"02", "切换机 · 创建虚拟机", "上传 ROM · 配置系统环境", "wizard"},
                {"03", "应用中心", "导入真机软件到虚拟机", "store"},
                {"04", "ROM 商店", "下载官方精简系统镜像（≤400MB）", "romstore"},
                {"05", "关于 & 授权", "设备信息 · 授权码 · 防注入", "about"}};
        for (String[] m : menus) {
            LinearLayout item = card();
            item.setPadding(dp(14), dp(13), dp(14), dp(13));
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            TextView num = text(m[0], 12, ACCENT, 1);
            num.setBackground(round(0x1200E5FF, 9));
            num.setPadding(dp(8), dp(4), dp(8), dp(4));
            LinearLayout tt2 = new LinearLayout(this);
            tt2.setOrientation(LinearLayout.VERTICAL);
            tt2.setPadding(dp(12), 0, 0, 0);
            tt2.addView(text(m[1], 14.5f, TXT, 1));
            tt2.addView(subText(m[2]));
            item.addView(num);
            item.addView(tt2);
            item.addView(text("›", 22, 0xFF41507A, 0), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            ((TextView) item.getChildAt(item.getChildCount() - 1)).setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            item.setOnClickListener(v -> {
                switch (m[3]) {
                    case "wizard": openWizard(); break;
                    case "romstore": show(7); break;
                    case "store": if (curVm != null) show(6); else toast("请先启动一台虚拟机"); break;
                    case "about": show(10); break;
                    case "home": show(1); break;
                }
            });
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            il.bottomMargin = dp(9);
            p.addView(item, il);
        }

        p.addView(sectionTitle("我的机器"));
        homeVmList = new LinearLayout(this);
        homeVmList.setOrientation(LinearLayout.VERTICAL);
        p.addView(homeVmList);
        renderHome();
        return p;
    }

    private void renderHome() {
        if (homeVmList == null) return;
        homeVmList.removeAllViews();
        if (vms.length() == 0) {
            homeVmList.addView(text("还没有虚拟机，点击创建", 13, 0xFF46546E, 0));
            return;
        }
        for (int i = 0; i < vms.length(); i++) {
            try {
                final JSONObject vm = vms.getJSONObject(i);
                final int fi = i;
                LinearLayout c = card();
                c.setPadding(dp(14), dp(12), dp(14), dp(12));
                LinearLayout head = rowWrap();
                TextView av = text(vm.optString("name", "?").substring(0, 1), 17, 0xFF8FF4FF, 1);
                av.setBackground(roundStroke(0x1A2F7BFF, 1, 14));
                av.setGravity(Gravity.CENTER);
                head.addView(av, new LinearLayout.LayoutParams(dp(46), dp(46)));
                LinearLayout tt = new LinearLayout(this);
                tt.setOrientation(LinearLayout.VERTICAL);
                tt.setPadding(dp(11), 0, 0, 0);
                tt.addView(text(vm.optString("name", ""), 15.5f, TXT, 1));
                tt.addView(subText(vm.optString("ver", "") + " · " + vm.optString("bits", "") + " 位"));
                head.addView(tt);
                boolean ready = !vm.optBoolean("firstBoot", true);
                TextView st = text(ready ? "已就绪" : "待首启", 11, ready ? OK : 0xFF8A97AD, 1);
                st.setBackground(round(ready ? 0x142EE6A8 : 0x148A97AD, 99));
                st.setPadding(dp(10), dp(4), dp(10), dp(4));
                head.addView(st, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                ((LinearLayout.LayoutParams) st.getLayoutParams()).gravity = Gravity.CENTER_VERTICAL;
                c.addView(head);
                LinearLayout acts = rowWrap();
                acts.setPadding(0, dp(12), 0, 0);
                Button start = btnPrimary("启动", v -> { curVm = vm; startBoot(); });
                Button set = btn("设置", 0xFF182442, v -> { curVm = vm; show(3); });
                Button del = btn("删除", 0xFF182442, v -> removeVm(fi));
                acts.addView(start, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                acts.addView(set, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                acts.addView(del, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                c.addView(acts);
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cl.bottomMargin = dp(10);
                homeVmList.addView(c, cl);
            } catch (Exception ignored) {}
        }
    }

    private void removeVm(int idx) {
        try {
            if (vms.getJSONObject(idx).optBoolean("bundled", false) && vms.getJSONObject(idx).optInt("id") == 1) {
                toast("内置 ET-OS 7.0 系统不可删除");
                return;
            }
            vms.remove(idx);
            saveVms();
            renderHome();
            toast("已删除虚拟机");
        } catch (Exception ignored) {}
    }

    /* ================= 向导 ================= */
    private void openWizard() {
        wizardStep = 1;
        wizard = new JSONObject();
        romRes = null;
        perms.put("storage", false); perms.put("overlay", false); perms.put("install", false); perms.put("notify", false);
        show(2);
    }

    private View buildWizard() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        vpad(p, 18);
        sv.addView(p);

        LinearLayout top = rowWrap();
        Button back = btn("‹", 0xFF1E2D50, v -> { if (wizardStep > 1) wizardBack(); else show(1); });
        back.setTextSize(22);
        top.addView(back);
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(dp(12), 0, 0, 0);
        tt.addView(text("创建虚拟机", 19, TXT, 1));
        final TextView stepLbl = text("第 1 步 / 4 · 系统环境", 12, SUB, 0);
        tt.addView(stepLbl);
        top.addView(tt);
        p.addView(top);

        /* 步骤指示 */
        LinearLayout steps = rowWrap();
        steps.setPadding(0, dp(16), 0, dp(10));
        TextView[] sts = new TextView[4];
        for (int i = 0; i < 4; i++) {
            sts[i] = text(String.valueOf(i + 1), 13, 0xFF5E6E90, 1);
            sts[i].setGravity(Gravity.CENTER);
            sts[i].setBackground(round(0xFF101A32, 99));
            steps.addView(sts[i], new LinearLayout.LayoutParams(dp(32), dp(32)));
            if (i < 3) {
                View line = new View(this);
                line.setBackgroundColor(0x331E2E4F);
                steps.addView(line, new LinearLayout.LayoutParams(0, dp(2), 1));
            }
        }
        p.addView(steps);
        for (int i = 0; i < 4; i++) {
            if (i < wizardStep) { sts[i].setBackground(round(BLUE, 99)); sts[i].setTextColor(0xFF03121C); }
        }

        /* ===== 第 1 步 ===== */
        p.addView(text("虚拟机名称", 13, SUB, 1));
        vmNameEdit = new EditText(this);
        vmNameEdit.setText("我的安卓机");
        vmNameEdit.setTextColor(TXT);
        vmNameEdit.setHintTextColor(0xFF5E6E90);
        vmNameEdit.setBackground(round(0xFF0E162A, 13));
        vmNameEdit.setPadding(dp(14), dp(12), dp(14), dp(12));
        p.addView(vmNameEdit);

        p.addView(text("系统位数", 13, SUB, 1));
        bitsBtns = seg(p, new String[]{"32 位", "64 位"}, "32".equals(wizard.optString("bits", "64")) ? 0 : 1, idx -> {});
        p.addView(subText("64 位虚拟机可运行 64/32 位 ROM；32 位虚拟机仅运行 32 位 ROM。"));

        p.addView(text("Android 系统版本", 13, SUB, 1));
        verBtns = seg(p, new String[]{"Android 7.1", "Android 9.0", "Android 13.0", "Android 14.0", "自定义"}, 2, idx -> {
            vmCustomVerEdit.setVisibility(idx == 4 ? View.VISIBLE : View.GONE);
        });
        vmCustomVerEdit = new EditText(this);
        vmCustomVerEdit.setHint("自定义版本号，如 ET-OS 14.0");
        vmCustomVerEdit.setTextColor(TXT);
        vmCustomVerEdit.setHintTextColor(0xFF5E6E90);
        vmCustomVerEdit.setBackground(round(0xFF0E162A, 13));
        vmCustomVerEdit.setPadding(dp(14), dp(12), dp(14), dp(12));
        vmCustomVerEdit.setVisibility(View.GONE);
        p.addView(vmCustomVerEdit);

        p.addView(text("机型模板", 13, SUB, 1));
        modelBtns = seg(p, new String[]{"通用机型", "游戏机型", "办公机型"}, 0, idx -> {});

        Button next = btnPrimary("下一步", v -> wizardNext());
        p.addView(next, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        ((LinearLayout.LayoutParams) next.getLayoutParams()).topMargin = dp(18);

        /* ===== 第 2 步 ===== */
        Button up = btnGhost("⤒ 上传自己的 ROM 镜像\n支持 .zip / .img / .iso，仅支持 Android", v -> pickFile("rom", ""));
        up.setPadding(dp(12), dp(26), dp(12), dp(26));
        up.setGravity(Gravity.CENTER);
        up.setTextSize(13);
        up.setOnClickListener(v -> pickFile("rom", ""));
        p.addView(up);

        detectCard = text("", 12, TXT, 0);
        detectCard.setVisibility(View.GONE);
        p.addView(detectCard);

        Button goStore = btnGhost("没有 ROM？去 ROM 商店下载 ›", v -> show(7));
        p.addView(goStore);

        LinearLayout br = rowWrap();
        br.setPadding(0, dp(16), 0, 0);
        Button prev = btn("上一步", 0xFF182442, v -> wizardBack());
        romNextBtn = btnPrimary("下一步", v -> wizardNext());
        romNextBtn.setEnabled(false);
        br.addView(prev, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        br.addView(romNextBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        p.addView(br);

        /* ===== 第 3 步 ===== */
        p.addView(text("授权所需权限", 13, SUB, 1));
        Button all = btnGhost("一键全部授权", v -> grantAll());
        p.addView(all);
        permBtns = new ArrayList<>();
        String[][] pk = {{"storage", "存储 & 麦克风", "读写 ROM / 应用数据，录制声音"},
                {"overlay", "悬浮窗", "虚拟机运行时悬浮控制球"},
                {"install", "安装未知应用", "允许向虚拟机安装应用"},
                {"notify", "通知", "虚拟机运行状态通知"}};
        for (String[] kv : pk) {
            LinearLayout item = card();
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout t2 = new LinearLayout(this);
            t2.setOrientation(LinearLayout.VERTICAL);
            t2.addView(text(kv[1], 14, TXT, 1));
            t2.addView(subText(kv[2]));
            item.addView(t2);
            Button gb = btn("授权", 0xFF182442, v -> requestPerm(kv[0], false));
            item.addView(gb, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            ((Button) item.getChildAt(item.getChildCount() - 1)).setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            permBtns.add(gb);
            p.addView(item);
        }
        LinearLayout br2 = rowWrap();
        br2.setPadding(0, dp(16), 0, 0);
        Button prev2 = btn("上一步", 0xFF182442, v -> wizardBack());
        permNextBtn = btnPrimary("下一步", v -> wizardNext());
        permNextBtn.setEnabled(false);
        br2.addView(prev2, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        br2.addView(permNextBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        p.addView(br2);

        /* ===== 第 4 步 ===== */
        p.addView(text("创建确认", 13, SUB, 1));
        summaryRows = card();
        p.addView(summaryRows);
        Button create = btnPrimary("正式创建", v -> createVm());
        p.addView(create, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        ((LinearLayout.LayoutParams) create.getLayoutParams()).topMargin = dp(18);
        return sv;
    }

    private List<Button> seg(LinearLayout parent, String[] labels, int sel, java.util.function.IntConsumer onSel) {
        LinearLayout row = rowWrap();
        List<Button> list = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            Button b = btn(labels[i], i == sel ? 0x1F2F7BFF : 0xFF101A32, null);
            final Button fb = b;
            b.setOnClickListener(v -> {
                for (Button x : list) x.setBackground(round(0xFF101A32, 11));
                fb.setBackground(round(0x1F2F7BFF, 11));
                fb.setTextColor(ACCENT);
                onSel.accept(idx);
            });
            if (i == sel) { b.setTextColor(ACCENT); }
            list.add(b);
            row.addView(b, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            if (i < labels.length - 1) row.addView(new View(this), new LinearLayout.LayoutParams(dp(6), dp(1)));
        }
        parent.addView(row);
        return list;
    }

    private void renderWizard() {
        /* 向导步骤变化时由 show(2) 整体重建，无需单独刷新 */
    }

    private void wizardNext() {
        try {
            if (wizardStep == 1) {
                wizard.put("name", vmNameEdit.getText().toString().trim().isEmpty() ? "我的安卓机" : vmNameEdit.getText().toString().trim());
                int bi = selIndex(bitsBtns);
                wizard.put("bits", bi == 0 ? "32" : "64");
                int vi = selIndex(verBtns);
                String ver = vi == 4 ? (vmCustomVerEdit.getText().toString().trim().isEmpty() ? "自定义版本" : vmCustomVerEdit.getText().toString().trim())
                        : verBtns.get(vi).getText().toString();
                wizard.put("ver", ver);
                wizard.put("model", modelBtns.get(selIndex(modelBtns)).getText().toString());
                wizardStep = 2;
            } else if (wizardStep == 2) {
                if (romRes == null || !romRes.optBoolean("ok", false)) { toast("请先上传并检测通过 ROM"); return; }
                wizardStep = 3;
            } else if (wizardStep == 3) {
                if (!(perms.get("storage") == Boolean.TRUE && perms.get("overlay") == Boolean.TRUE && perms.get("install") == Boolean.TRUE)) {
                    toast("请先授权存储、悬浮窗与安装权限");
                    return;
                }
                wizardStep = 4;
            } else return;
        } catch (Exception ignored) {}
        show(2);
    }

    private int selIndex(List<Button> list) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).getCurrentTextColor() == ACCENT) return i;
        return 0;
    }

    private void wizardBack() {
        if (wizardStep <= 1) { show(1); return; }
        wizardStep--;
        show(2);
    }

    private void renderDetect() {
        if (romRes == null || detectCard == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("━━ 实时检测结果 ━━\n");
        sb.append("文件名称：").append(romRes.optString("name", "")).append("\n");
        sb.append("文件大小：").append(fmtSize(romRes.optLong("size", 0))).append("\n");
        sb.append("系统类型：").append(romRes.optString("type", "")).append("\n");
        sb.append("系统位数：").append(romRes.optString("bitsLabel", "")).append("\n");
        if (romRes.optBoolean("ok", false)) {
            String bits = romRes.optString("bits", "unknown");
            String wbits = wizard.optString("bits", "64");
            if (bits.equals("unknown") || bits.equals("both") || bits.equals(wbits)) {
                sb.append("位数匹配：✓ 与当前 ").append(wbits).append(" 位虚拟机匹配\n");
            } else {
                sb.append("位数匹配：✗ 不匹配（ROM 为 ").append(bits).append(" 位，虚拟机为 ").append(wbits).append(" 位）\n");
            }
        } else {
            sb.append("位数匹配：—\n");
        }
        sb.append(romRes.optString("note", ""));
        detectCard.setText(sb.toString());
        detectCard.setTextColor(romRes.optBoolean("ok", false) ? OK : BAD);
        detectCard.setBackground(round(CARD2, 14));
        detectCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        detectCard.setVisibility(View.VISIBLE);
        boolean ok = romRes.optBoolean("ok", false);
        if (ok) {
            String bits = romRes.optString("bits", "unknown");
            String wbits = wizard.optString("bits", "64");
            if (!(bits.equals("unknown") || bits.equals("both") || bits.equals(wbits))) ok = false;
        }
        if (romNextBtn != null) romNextBtn.setEnabled(ok);
    }

    private String fmtSize(long n) {
        if (n > 1073741824L) return String.format("%.2f GB", n / 1073741824.0);
        if (n > 1048576L) return String.format("%.1f MB", n / 1048576.0);
        return String.format("%.0f KB", n / 1024.0);
    }

    private void analyze(String path, String name, long size) {
        toast("正在实时检测镜像…");
        new Thread(() -> {
            RomDetect.Result r = RomDetect.detect(path, name, size);
            h.post(() -> {
                try {
                    romRes = new JSONObject();
                    romRes.put("ok", r.ok).put("type", r.type).put("bits", r.bits)
                            .put("bitsLabel", r.bitsLabel).put("note", r.note)
                            .put("size", r.size).put("name", name).put("path", path);
                    renderDetect();
                } catch (Exception ignored) {}
            });
        }).start();
    }

    /* 权限 */
    private void requestPerm(String kind, boolean fromAbout) {
        pendingPermKind = kind.equals("storage") ? RC_STORAGE : kind.equals("notify") ? RC_NOTIFY : -1;
        switch (kind) {
            case "storage":
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(new String[]{android.Manifest.permission.READ_MEDIA_IMAGES,
                            android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_AUDIO,
                            android.Manifest.permission.RECORD_AUDIO}, RC_STORAGE);
                } else {
                    requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.RECORD_AUDIO}, RC_STORAGE);
                }
                break;
            case "notify":
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, RC_NOTIFY);
                } else { perms.put("notify", true); refreshPerms(); }
                break;
            case "overlay":
                if (Settings.canDrawOverlays(this)) { perms.put("overlay", true); refreshPerms(); }
                else {
                    try {
                        startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())), RC_OVERLAY_SETTINGS);
                    } catch (ActivityNotFoundException e) {
                        startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), RC_OVERLAY_SETTINGS);
                    }
                }
                break;
            case "install":
                try {
                    startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + getPackageName())), RC_INSTALL_SETTINGS);
                } catch (ActivityNotFoundException e) { perms.put("install", false); refreshPerms(); }
                break;
        }
    }

    private boolean hasPerm(String kind) {
        switch (kind) {
            case "storage":
                if (Build.VERSION.SDK_INT >= 33) {
                    return checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                            || checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                }
                return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            case "notify":
                if (Build.VERSION.SDK_INT >= 33) return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
                return true;
            case "overlay": return Settings.canDrawOverlays(this);
            case "install": return Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
        }
        return false;
    }

    private void grantAll() {
        String[] ks = {"storage", "overlay", "install", "notify"};
        for (int i = 0; i < ks.length; i++) {
            final String k = ks[i];
            h.postDelayed(() -> { if (!hasPerm(k)) requestPerm(k, false); else { perms.put(k, true); refreshPerms(); } }, i * 900L);
        }
    }

    private void refreshPerms() {
        if (permBtns == null) return;
        for (int i = 0; i < permBtns.size(); i++) {
            Button b = permBtns.get(i);
            String[] keys = {"storage", "overlay", "install", "notify"};
            boolean ok = perms.get(keys[i]) == Boolean.TRUE || hasPerm(keys[i]);
            if (ok) { perms.put(keys[i], true); b.setText("已授权"); b.setEnabled(false); b.setTextColor(OK); }
        }
        if (permNextBtn != null) {
            permNextBtn.setEnabled(perms.get("storage") == Boolean.TRUE && perms.get("overlay") == Boolean.TRUE && perms.get("install") == Boolean.TRUE);
        }
    }

    private void createVm() {
        try {
            if (romRes == null) { toast("缺少 ROM"); return; }
            JSONObject vm = new JSONObject();
            vm.put("id", System.currentTimeMillis());
            vm.put("name", wizard.optString("name", "我的安卓机"));
            vm.put("bits", wizard.optString("bits", "64"));
            vm.put("ver", wizard.optString("ver", "Android 13.0"));
            vm.put("model", wizard.optString("model", "通用机型"));
            JSONObject rom = new JSONObject();
            rom.put("name", romRes.optString("name", ""));
            rom.put("size", romRes.optLong("size", 0));
            rom.put("bits", romRes.optString("bits", "unknown"));
            rom.put("path", romRes.optString("path", ""));
            vm.put("rom", rom);
            vm.put("version", "ET-OS 14.0 (2026.09)");
            vm.put("fps", 60);
            vm.put("anim", "ET 经典");
            vm.put("animPath", "");
            vm.put("gapps", false);
            vm.put("xposed", false);
            vm.put("root", false);
            vm.put("firstBoot", true);
            vm.put("installed", new JSONArray());
            vm.put("created", System.currentTimeMillis());
            vms.put(vm);
            saveVms();
            toast("虚拟机「" + vm.optString("name") + "」创建成功");
            show(1);
        } catch (Exception ignored) {}
    }

    /* ================= 设置 ================= */
    private View buildSettings() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        vpad(p, 18);
        sv.addView(p);

        LinearLayout top = rowWrap();
        Button back = btn("‹", 0xFF1E2D50, v -> show(1));
        back.setTextSize(22);
        top.addView(back);
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(dp(12), 0, 0, 0);
        tt.addView(text(curVm == null ? "虚拟机设置" : curVm.optString("name", "") + " · 设置", 19, TXT, 1));
        tt.addView(subText("刷新率 · 开机动画 · 增强工具"));
        top.addView(tt);
        p.addView(top);

        p.addView(text("刷新频率：60 ~ 120 Hz（默认 60）", 13, SUB, 1));
        fpsVal = text("60 Hz", 13, ACCENT, 1);
        p.addView(fpsVal);
        fpsBar = new SeekBar(this);
        fpsBar.setMax(60);
        fpsBar.setProgress(curVm != null ? (curVm.optInt("fps", 60) - 60) : 0);
        fpsBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int pr, boolean from) { fpsVal.setText((60 + pr) + " Hz"); }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        p.addView(fpsBar);
        if (curVm != null) fpsVal.setText(curVm.optInt("fps", 60) + " Hz");

        p.addView(text("自定义开机动画", 13, SUB, 1));
        animBtns = seg(p, new String[]{"ET 经典", "极光粒子", "极简线条", "自定义图片"},
                curVm != null && curVm.optString("animPath", "").length() > 0 ? 3
                        : Math.max(0, indexOf(new String[]{"ET 经典", "极光粒子", "极简线条"}, curVm == null ? "ET 经典" : curVm.optString("anim", "ET 经典"))), idx -> {
                    if (idx == 3) pickFile("bootanim", "");
                });
        animHint = subText(curVm != null && curVm.optString("animPath", "").length() > 0 ? "已选择：自定义图片" : "已选择：" + (curVm == null ? "ET 经典" : curVm.optString("anim", "ET 经典")));
        p.addView(animHint);

        p.addView(text("增强工具", 13, SUB, 1));
        optGapps = new CheckBox(this);
        optGapps.setText("安装谷歌三件套（Google 服务框架 / Play 商店）");
        optGapps.setTextColor(TXT);
        optGapps.setChecked(curVm != null && curVm.optBoolean("gapps", false));
        p.addView(optGapps);
        optXposed = new CheckBox(this);
        optXposed.setText("Xposed 框架（模块化系统增强）");
        optXposed.setTextColor(TXT);
        optXposed.setChecked(curVm != null && curVm.optBoolean("xposed", false));
        p.addView(optXposed);
        optRoot = new CheckBox(this);
        optRoot.setText("Root 工具（内置超级用户权限管理）");
        optRoot.setTextColor(TXT);
        optRoot.setChecked(curVm != null && curVm.optBoolean("root", false));
        p.addView(optRoot);

        p.addView(text("虚拟机版本号", 13, SUB, 1));
        vmVersionEdit = new EditText(this);
        vmVersionEdit.setText(curVm == null ? "" : curVm.optString("version", ""));
        vmVersionEdit.setTextColor(TXT);
        vmVersionEdit.setHintTextColor(0xFF5E6E90);
        vmVersionEdit.setBackground(round(0xFF0E162A, 13));
        vmVersionEdit.setPadding(dp(14), dp(12), dp(14), dp(12));
        p.addView(vmVersionEdit);

        Button save = btnPrimary("保存设置", v -> saveSettings());
        p.addView(save, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        ((LinearLayout.LayoutParams) save.getLayoutParams()).topMargin = dp(18);
        return sv;
    }

    private List<Button> animBtns = new ArrayList<>();
    private int indexOf(String[] arr, String v) { for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i; return 0; }

    private void saveSettings() {
        if (curVm == null) return;
        try {
            curVm.put("fps", 60 + fpsBar.getProgress());
            curVm.put("gapps", optGapps.isChecked());
            curVm.put("xposed", optXposed.isChecked());
            curVm.put("root", optRoot.isChecked());
            String ver = vmVersionEdit.getText().toString().trim();
            if (!ver.isEmpty()) curVm.put("version", ver);
            int ai = selIndex(animBtns);
            if (ai < 3) curVm.put("anim", new String[]{"ET 经典", "极光粒子", "极简线条"}[ai]);
            saveVms();
            toast("设置已保存");
            show(1);
        } catch (Exception ignored) {}
    }

    /* ================= 开机 ================= */
    private View buildBoot() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER);
        p.setBackgroundColor(0xFF04070F);

        if (curVm != null && curVm.optString("animPath", "").length() > 0) {
            try {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(BitmapFactory.decodeFile(curVm.optString("animPath")));
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(280), dp(180));
                il.bottomMargin = dp(20);
                p.addView(iv, il);
            } catch (Exception ignored) {}
        } else {
            TextView logo = new TextView(this);
            logo.setText(curVm != null && "极光粒子".equals(curVm.optString("anim", "")) ? "◆" : "ET");
            logo.setTextSize(36);
            logo.setTypeface(Typeface.DEFAULT_BOLD);
            logo.setTextColor(0xFF03121C);
            logo.setGravity(Gravity.CENTER);
            logo.setBackground(round(BLUE, 24));
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(dp(100), dp(100));
            ll.bottomMargin = dp(24);
            p.addView(logo, ll);
        }

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setProgress(0);
        bar.setMax(100);
        bar.setPadding(dp(20), 0, dp(20), 0);
        p.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)));

        bootLinesBox = new LinearLayout(this);
        bootLinesBox.setOrientation(LinearLayout.VERTICAL);
        bootLinesBox.setPadding(dp(26), dp(18), dp(26), 0);
        p.addView(bootLinesBox);
        return p;
    }

    private void startBoot() {
        show(4);
        final boolean first = curVm.optBoolean("firstBoot", true);
        final List<String> lines = new ArrayList<>();
        if (first) {
            String romName = curVm.optJSONObject("rom") == null ? "自定义" : curVm.optJSONObject("rom").optString("name", "自定义");
            lines.add("正在加载 ET 虚拟机内核…");
            lines.add("正在初始化系统镜像…");
            lines.add("首次启动：正在安装 ROM「" + romName + "」…");
            lines.add("正在校验系统位数（" + curVm.optString("bits", "64") + " 位）…");
            if (curVm.optBoolean("gapps", false)) lines.add("正在安装 Google 服务框架 (GApps)…");
            if (curVm.optBoolean("xposed", false)) lines.add("正在注入 Xposed 框架…");
            if (curVm.optBoolean("root", false)) lines.add("正在启用 Root 工具…");
            lines.add("正在应用 " + curVm.optInt("fps", 60) + "Hz 刷新率…");
            lines.add("正在自动安装「连接储存」共享组件…");
            lines.add("正在优化应用缓存…");
            lines.add("首次启动完成，进入系统");
        } else {
            lines.add("正在快速启动…");
            lines.add("正在加载内置系统镜像缓存…");
            lines.add("正在校验真机共享文件夹…");
            lines.add("正在应用 " + curVm.optInt("fps", 60) + "Hz 刷新率…");
            lines.add("开机完成，进入系统");
        }
        final long stepMs = first ? 620 : 460;
        final int[] i = {0};
        h.post(new Runnable() {
            @Override public void run() {
                if (scr != 4) return;
                if (i[0] < lines.size()) {
                    if (bootLinesBox != null) {
                        bootLinesBox.removeAllViews();
                        for (int j = 0; j <= i[0]; j++) {
                            TextView t = text((j < i[0] ? "✓ " : "▸ ") + lines.get(j), 12,
                                    j < i[0] ? OK : ACCENT, 0);
                            t.setTypeface(Typeface.MONOSPACE);
                            t.setPadding(0, dp(3), 0, dp(3));
                            bootLinesBox.addView(t);
                        }
                    }
                    i[0]++;
                    h.postDelayed(this, stepMs);
                } else {
                    if (first) {
                        try {
                            JSONArray inst = curVm.optJSONArray("installed");
                            if (inst == null) inst = new JSONArray();
                            boolean has = false;
                            for (int j = 0; j < inst.length(); j++) if ("com.et.storage".equals(inst.getJSONObject(j).optString("pkg", ""))) has = true;
                            if (!has) {
                                JSONObject st = new JSONObject();
                                st.put("label", "连接储存"); st.put("pkg", "com.et.storage");
                                st.put("abi", "内置 · 真机共享"); st.put("color", "tile-orange");
                                inst.put(st);
                                curVm.put("installed", inst);
                            }
                            curVm.put("firstBoot", false);
                            saveVms();
                        } catch (Exception ignored) {}
                    }
                    h.postDelayed(() -> { renderVm(); show(5); }, 420);
                }
            }
        });
    }

    /* ================= 虚拟机桌面 ================= */
    private View buildVm() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackgroundColor(0xFF060A14);

        /* 状态栏 */
        LinearLayout status = rowWrap();
        status.setPadding(dp(16), dp(12), dp(16), dp(12));
        status.setBackgroundColor(0xFF0D1528);
        vmClock = text("--:--", 12, 0xFF9FB2D6, 0);
        status.addView(vmClock);
        vmBadge = text(curVm == null ? "" : curVm.optString("bits", "64") + " 位 · " + curVm.optInt("fps", 60) + "Hz", 11, ACCENT, 1);
        vmBadge.setBackground(round(0x1200E5FF, 99));
        vmBadge.setPadding(dp(11), dp(3), dp(11), dp(3));
        status.addView(vmBadge, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams) vmBadge.getLayoutParams()).gravity = Gravity.CENTER_VERTICAL;
        p.addView(status);

        /* 壁纸区 */
        FrameLayout wall = new FrameLayout(this);
        Bitmap bmp = null;
        try { bmp = BitmapFactory.decodeFile(new File(getExternalFilesDir(null), "roms/wallpaper.png").getAbsolutePath()); } catch (Exception ignored) {}
        if (bmp != null) {
            int w = getResources().getDisplayMetrics().widthPixels;
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, w, (int) (w * 1280f / 720f), true);
            ImageView wi = new ImageView(this);
            wi.setImageBitmap(scaled);
            wi.setScaleType(ImageView.ScaleType.CENTER_CROP);
            wall.addView(wi, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(170)));
        } else {
            wall.setBackgroundColor(0xFF0D1B3A);
        }
        vmGreet = text(curVm == null ? "" : curVm.optString("name", "") + " · 一切皆可运行", 19, Color.WHITE, 1);
        vmGreet.setShadowLayer(dp(6), 0, dp(2), 0xAA000000);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        gl.leftMargin = dp(16); gl.bottomMargin = dp(34);
        gl.gravity = Gravity.BOTTOM | Gravity.LEFT;
        wall.addView(vmGreet, gl);
        TextView share = text("⇄ 真机共享文件夹已连接 · 点此打开", 11, 0xFF8FF4FF, 0);
        share.setBackground(round(0x1A00E5FF, 99));
        share.setPadding(dp(12), dp(4), dp(12), dp(4));
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        sl.leftMargin = dp(16); sl.bottomMargin = dp(10);
        sl.gravity = Gravity.BOTTOM | Gravity.LEFT;
        share.setOnClickListener(v -> show(8));
        wall.addView(share, sl);
        p.addView(wall, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170)));

        /* 应用网格 */
        ScrollView sv = new ScrollView(this);
        vmAppGrid = new LinearLayout(this);
        vmAppGrid.setOrientation(LinearLayout.VERTICAL);
        vmAppGrid.setPadding(dp(12), dp(18), dp(12), dp(10));
        sv.addView(vmAppGrid);
        p.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        /* Dock */
        LinearLayout dock = rowWrap();
        dock.setPadding(dp(6), dp(10), dp(6), dp(12));
        dock.setBackgroundColor(0xFF0D1528);
        String[][] dks = {{"商", "应用中心", "store"}, {"夹", "连接储存", "files"}, {"球", "悬浮控制", "float"}, {"开", "开发者", "dev"}, {"电", "电源", "power"}};
        for (String[] d : dks) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            TextView t = tile(d[0], d[1].equals("应用中心") ? 0xFF00B8D4 : d[1].equals("连接储存") ? 0xFFFF9F43
                    : d[1].equals("悬浮控制") ? 0xFF7C4DFF : d[1].equals("开发者") ? 0xFF00B894 : 0xFFE84118);
            item.addView(t);
            TextView l = text(d[1], 11, 0xFF9FB2D6, 0);
            item.addView(l);
            item.setOnClickListener(v -> onDock(d[2]));
            dock.addView(item, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        p.addView(dock);
        return p;
    }

    private TextView tile(String ch, int color) {
        TextView t = new TextView(this);
        t.setText(ch);
        t.setTextSize(20);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(color, 16));
        return t;
    }

    private void renderVm() {
        if (vmAppGrid == null || curVm == null) return;
        vmAppGrid.removeAllViews();
        String[][] sys = {{"系", "系统设置", "set"}, {"浏", "ET 浏览器", "browser"}, {"连", "连接储存", "files"}, {"相", "相机", "camera"}, {"册", "相册", "gallery"}};
        for (int i = 0; i < sys.length; i += 3) {
            LinearLayout row = rowWrap();
            for (int j = i; j < Math.min(sys.length, i + 3); j++) {
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER);
                TextView t = tile(sys[j][0], new int[]{0xFF2F7BFF, 0xFF00B8D4, 0xFFFF9F43, 0xFFE84118, 0xFF7C4DFF}[j]);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(dp(54), dp(54));
                item.addView(t, tl);
                TextView l = text(sys[j][1], 11, 0xFFCDD8EC, 0);
                l.setGravity(Gravity.CENTER);
                item.addView(l);
                final String key = sys[j][2];
                item.setOnClickListener(v -> onSysApp(key));
                row.addView(item, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            }
            vmAppGrid.addView(row);
        }
        try {
            JSONArray inst = curVm.optJSONArray("installed");
            if (inst != null && inst.length() > 0) {
                for (int i = 0; i < inst.length(); i += 3) {
                    LinearLayout row = rowWrap();
                    for (int j = i; j < Math.min(inst.length(), i + 3); j++) {
                        JSONObject a = inst.getJSONObject(j);
                        LinearLayout item = new LinearLayout(this);
                        item.setOrientation(LinearLayout.VERTICAL);
                        item.setGravity(Gravity.CENTER);
                        TextView t = tile(a.optString("label", "?").substring(0, 1), 0xFF00B8D4);
                        item.addView(t, new LinearLayout.LayoutParams(dp(54), dp(54)));
                        TextView l = text(a.optString("label", ""), 11, 0xFFCDD8EC, 0);
                        l.setGravity(Gravity.CENTER);
                        item.addView(l);
                        final String pkg = a.optString("pkg", "");
                        item.setOnClickListener(v -> { if ("com.et.storage".equals(pkg)) show(8); else toast("正在启动 " + a.optString("label", "")); });
                        row.addView(item, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                    }
                    vmAppGrid.addView(row);
                }
            }
        } catch (Exception ignored) {}
    }

    private void onSysApp(String key) {
        if (key.equals("files")) { show(8); return; }
        String[] tips = {"set", "打开系统设置 · " + (curVm == null ? "" : curVm.optString("ver", "")), "browser", "ET 浏览器 · 快速浏览", "camera", "相机 · 调用虚拟机摄像头", "gallery", "相册 · 查看媒体文件"};
        for (int i = 0; i < tips.length; i += 2) if (tips[i].equals(key)) { toast(tips[i + 1]); vib(20); return; }
    }

    private void onDock(String key) {
        vib(18);
        switch (key) {
            case "store": show(6); break;
            case "files": show(8); break;
            case "float":
                if (hasPerm("overlay")) { startFloat(); toast("悬浮控制球已开启，可拖动使用"); }
                else { toast("请先授权悬浮窗权限"); requestPerm("overlay", false); }
                break;
            case "dev": show(9); break;
            case "power":
                toast("正在关机…");
                h.postDelayed(() -> { stopFloat(); show(1); }, 800);
                break;
        }
    }

    /* 悬浮窗面板按键（home/back/menu/volup/voldown） */
    public void dispatchFloatKey(String key) {
        switch (key) {
            case "home": show(1); break;
            case "back": show(1); break;
            case "menu": toast("悬浮控制菜单：拖动悬浮球可调整位置"); break;
            case "volup": toast("音量 +（虚拟机内由应用中心控制）"); break;
            case "voldown": toast("音量 -（虚拟机内由应用中心控制）"); break;
        }
    }

    private void startFloat() {
        if (!Settings.canDrawOverlays(this)) return;
        try { startService(new Intent(this, FloatingControlService.class)); } catch (Exception ignored) {}
    }
    private void stopFloat() {
        try { stopService(new Intent(this, FloatingControlService.class)); } catch (Exception ignored) {}
    }

    /* ================= 应用中心 ================= */
    private View buildStore() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        vpad(p, 18);
        sv.addView(p);

        LinearLayout top = rowWrap();
        Button back = btn("‹", 0xFF1E2D50, v -> show(5));
        back.setTextSize(22);
        top.addView(back);
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(dp(12), 0, 0, 0);
        tt.addView(text("应用中心", 19, TXT, 1));
        tt.addView(subText("当前虚拟机：" + (curVm == null ? "-" : curVm.optString("name", "")) + " · " + (curVm == null ? "" : curVm.optString("bits", "") + " 位")));
        top.addView(tt);
        p.addView(top);

        p.addView(sectionTitle("系统自带应用"));
        LinearLayout builtin = rowWrap();
        String[][] bapps = {{"浏", "ET 浏览器"}, {"夹", "连接储存"}, {"设", "系统设置"}, {"相", "相机"}, {"册", "相册"}};
        int[] bcolors = {0xFF2F7BFF, 0xFFFF9F43, 0xFF00B894, 0xFFE84118, 0xFF7C4DFF};
        for (int bi = 0; bi < bapps.length; bi++) {
            String[] b = bapps[bi];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.addView(tile(b[0], bcolors[bi]));
            TextView l = text(b[1], 11, 0xFFCDD8EC, 0);
            l.setGravity(Gravity.CENTER);
            item.addView(l);
            builtin.addView(item, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        LinearLayout builtinCard = card();
        builtinCard.setOrientation(LinearLayout.HORIZONTAL);
        builtinCard.addView(builtin);
        p.addView(builtinCard);

        p.addView(sectionTitle("已导入应用"));
        storeList = new LinearLayout(this);
        storeList.setOrientation(LinearLayout.VERTICAL);
        p.addView(storeList);
        renderStore();

        Button imp = btnPrimary("从真机导出应用", v -> importFromDevice());
        p.addView(imp, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        ((LinearLayout.LayoutParams) imp.getLayoutParams()).topMargin = dp(18);
        p.addView(subText("导出真机软件后会自动检测位数兼容性，仅支持当前虚拟机位数的应用才会被安装。"));
        return sv;
    }

    private void renderStore() {
        if (storeList == null) return;
        storeList.removeAllViews();
        JSONArray inst = curVm == null ? new JSONArray() : curVm.optJSONArray("installed");
        if (inst == null) inst = new JSONArray();
        if (inst.length() == 0) {
            storeList.addView(text("还没有导入应用，点击下方按钮从真机导出", 13, 0xFF46546E, 0));
            return;
        }
        try {
            for (int i = 0; i < inst.length(); i++) {
                JSONObject a = inst.getJSONObject(i);
                LinearLayout item = card();
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setGravity(Gravity.CENTER_VERTICAL);
                TextView t = tile(a.optString("label", "?").substring(0, 1), 0xFF00B8D4);
                item.addView(t, new LinearLayout.LayoutParams(dp(40), dp(40)));
                LinearLayout tt = new LinearLayout(this);
                tt.setOrientation(LinearLayout.VERTICAL);
                tt.setPadding(dp(11), 0, 0, 0);
                tt.addView(text(a.optString("label", ""), 14, TXT, 1));
                tt.addView(subText(a.optString("pkg", "") + " · " + a.optString("abi", "兼容")));
                item.addView(tt);
                TextView st = text("已安装", 11, OK, 1);
                item.addView(st, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                ((TextView) item.getChildAt(item.getChildCount() - 1)).setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                storeList.addView(item);
            }
        } catch (Exception ignored) {}
    }

    private void importFromDevice() {
        importCache = new JSONArray();
        try {
            Intent main = new Intent(Intent.ACTION_MAIN);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            PackageManager pm = getPackageManager();
            List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : list) {
                try {
                    String pkg = ri.activityInfo.packageName;
                    if (!seen.add(pkg)) continue;
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    JSONObject o = new JSONObject();
                    o.put("label", String.valueOf(ai.loadLabel(pm)));
                    o.put("pkg", pkg);
                    o.put("apk", ai.sourceDir);
                    o.put("system", (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                    o.put("size", new File(ai.sourceDir).length());
                    importCache.put(o);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) { importCache = new JSONArray(); }
        if (importCache.length() == 0) { toast("未找到可导出的应用"); return; }
        showAppPicker();
    }

    private void showAppPicker() {
        LinearLayout listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.addView(listBox);
        for (int i = 0; i < importCache.length(); i++) {
            try {
                JSONObject a = importCache.getJSONObject(i);
                LinearLayout row = rowWrap();
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                row.setBackground(round(0xFF141E3C, 12));
                TextView t = tile(a.optString("label", "?").substring(0, 1), 0xFF00B8D4);
                row.addView(t, new LinearLayout.LayoutParams(dp(38), dp(38)));
                LinearLayout tt = new LinearLayout(this);
                tt.setOrientation(LinearLayout.VERTICAL);
                tt.setPadding(dp(10), 0, 0, 0);
                tt.addView(text(a.optString("label", ""), 14, TXT, 1));
                tt.addView(subText(a.optString("pkg", "") + " · " + fmtSize(a.optLong("size", 0))));
                row.addView(tt);
                final String pkg = a.optString("pkg", "");
                row.setOnClickListener(v -> pickApp(pkg));
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rl.bottomMargin = dp(8);
                listBox.addView(row, rl);
            } catch (Exception ignored) {}
        }
        new AlertDialog.Builder(this)
                .setTitle("选择真机应用")
                .setView(sv)
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickApp(String pkg) {
        if ("com.et.vm".equals(pkg)) { toast("不能导入 ET 虚拟机自身"); return; }
        try {
            for (int i = 0; i < importCache.length(); i++) {
                JSONObject a = importCache.getJSONObject(i);
                if (pkg.equals(a.optString("pkg", ""))) {
                    JSONArray inst = curVm == null ? new JSONArray() : curVm.optJSONArray("installed");
                    if (inst == null) inst = new JSONArray();
                    for (int j = 0; j < inst.length(); j++) {
                        if (pkg.equals(inst.getJSONObject(j).optString("pkg", ""))) { toast("该应用已在虚拟机中"); return; }
                    }
                    String apk = a.optString("apk", "");
                    final String label = a.optString("label", "");
                    final String vmId = String.valueOf(curVm.optLong("id", 0));
                    toast("正在检测兼容性…");
                    new Thread(() -> {
                        try {
                            File src = new File(apk);
                            File dir = new File(getExternalFilesDir(null), "vms/" + vmId + "/apps");
                            if (!dir.exists()) dir.mkdirs();
                            File dst = new File(dir, src.getName());
                            copyFile(src, dst);
                            RomDetect.ApkCompat c = RomDetect.checkApk(dst.getAbsolutePath(), curVm.optString("bits", "64"));
                            h.post(() -> showCompat(label, pkg, c));
                        } catch (Exception e) {
                            h.post(() -> toast("读取应用包失败"));
                        }
                    }).start();
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    private void showCompat(String label, String pkg, RomDetect.ApkCompat c) {
        StringBuilder sb = new StringBuilder();
        sb.append("应用名称：").append(label).append("\n包名：").append(pkg)
                .append("\n原生库位数：").append(c.detail)
                .append("\n与虚拟机（").append(curVm.optString("bits", "64")).append(" 位）：")
                .append(c.ok ? "✓ 兼容" : "✗ 不兼容")
                .append("\n").append(c.reason);
        TextView body = text(sb.toString(), 13, c.ok ? OK : BAD, 0);
        body.setPadding(dp(4), dp(4), dp(4), dp(4));
        new AlertDialog.Builder(this)
                .setTitle("兼容性检测结果")
                .setMessage(body.getText())
                .setPositiveButton(c.ok ? "安装到虚拟机" : "关闭", (d, w) -> {
                    if (c.ok) {
                        try {
                            JSONArray inst = curVm.optJSONArray("installed");
                            if (inst == null) inst = new JSONArray();
                            JSONObject a = new JSONObject();
                            a.put("label", label); a.put("pkg", pkg); a.put("abi", c.reason); a.put("color", "tile-cyan");
                            inst.put(a);
                            curVm.put("installed", inst);
                            saveVms();
                            toast("「" + label + "」已安装到虚拟机");
                            renderStore(); renderVm();
                        } catch (Exception ignored) {}
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    /* ================= ROM 商店 ================= */
    private View buildRomStore() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        vpad(p, 18);
        sv.addView(p);

        LinearLayout top = rowWrap();
        Button back = btn("‹", 0xFF1E2D50, v -> { if (wizardStep > 1 && scr == 7) wizardBack(); else show(1); });
        back.setTextSize(22);
        top.addView(back);
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(dp(12), 0, 0, 0);
        tt.addView(text("ROM 商店", 19, TXT, 1));
        tt.addView(subText("官方精简系统镜像 · 单包 ≤400MB"));
        top.addView(tt);
        Button ref = btn("刷新列表", 0xFF182442, v -> { toast("正在刷新列表…"); show(7); });
        ref.setTextSize(11);
        top.addView(ref);
        p.addView(top);

        romList = new LinearLayout(this);
        romList.setOrientation(LinearLayout.VERTICAL);
        p.addView(romList);
        p.addView(subText("下载完成后可直接\"使用此 ROM\"创建虚拟机；已下载的镜像会保留在本地。"));
        renderRomStore();
        return sv;
    }

    private void renderRomStore() {
        if (romList == null) return;
        romList.removeAllViews();
        loadCatalog();
        try {
            JSONObject dls = new JSONObject(sp.getString("romDls", "{}"));
            for (int i = 0; i < catalog.length(); i++) {
                final JSONObject r = catalog.getJSONObject(i);
                final String id = r.optString("id", "");
                boolean dl = dls.has(id);
                LinearLayout c = card();
                LinearLayout head = rowWrap();
                TextView t = tile(r.optString("name", "?").substring(0, 1), BLUE);
                head.addView(t, new LinearLayout.LayoutParams(dp(44), dp(44)));
                LinearLayout tt = new LinearLayout(this);
                tt.setOrientation(LinearLayout.VERTICAL);
                tt.setPadding(dp(11), 0, 0, 0);
                tt.addView(text(r.optString("name", ""), 15, TXT, 1));
                tt.addView(subText(r.optString("desc", "")));
                head.addView(tt);
                c.addView(head);
                LinearLayout tags = rowWrap();
                tags.setPadding(0, dp(10), 0, 0);
                tags.addView(tag(r.optString("ver", ""), 0xFF7FB3FF));
                tags.addView(tag(r.optString("bits", "") + " 位", ACCENT));
                tags.addView(tag(r.optString("sizeMb", "") + " MB", OK));
                tags.addView(tag(dl ? "已下载" : "未下载", dl ? OK : 0xFF8A97AD));
                c.addView(tags);
                LinearLayout acts = rowWrap();
                acts.setPadding(0, dp(12), 0, 0);
                if (dl) {
                    Button use = btnPrimary("使用此 ROM", v -> useRom(id));
                    acts.addView(use, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                }
                Button dbtn = btn(dl ? "重新下载" : "下载 ROM", 0xFF182442, v -> downloadRom(id));
                acts.addView(dbtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                c.addView(acts);
                romList.addView(c);
            }
        } catch (Exception ignored) {}
    }

    private TextView tag(String s, int color) {
        TextView t = text(s, 10.5f, color, 1);
        t.setBackground(round(0x11FFFFFF, 99));
        t.setPadding(dp(10), dp(3), dp(10), dp(3));
        t.setLetterSpacing(0.03f);
        return t;
    }

    private void loadCatalog() {
        try {
            InputStream in = getAssets().open("rom-catalog.json");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            catalog = new JSONObject(out.toString("UTF-8")).optJSONArray("roms");
            if (catalog == null) catalog = new JSONArray();
        } catch (Exception e) { catalog = new JSONArray(); }
    }

    private void downloadRom(final String id) {
        try {
            JSONObject r = null;
            for (int i = 0; i < catalog.length(); i++) if (id.equals(catalog.getJSONObject(i).optString("id", ""))) r = catalog.getJSONObject(i);
            if (r == null) return;
            final JSONObject fr = r;
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(fr.optString("url", "")));
            req.setDestinationInExternalFilesDir(this, "uploads/rom", fr.optString("file", "rom.iso"));
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setTitle("ET虚拟机 · ROM 下载");
            req.setDescription(fr.optString("name", ""));
            final long dlId = dm.enqueue(req);
            toast("已开始下载「" + fr.optString("name", "") + "」");
            h.postDelayed(new Runnable() {
                @Override public void run() {
                    JSONObject info = getDownloadInfo(String.valueOf(dlId));
                    if (info.optBoolean("done", false)) {
                        try {
                            JSONObject dls = new JSONObject(sp.getString("romDls", "{}"));
                            JSONObject e = new JSONObject();
                            e.put("file", fr.optString("file", ""));
                            e.put("path", info.optString("path", ""));
                            dls.put(id, e);
                            sp.edit().putString("romDls", dls.toString()).apply();
                            toast("「" + fr.optString("name", "") + "」下载完成");
                            show(7);
                        } catch (Exception ignored) {}
                    } else if (info.optBoolean("failed", false)) {
                        toast("下载失败，请重试");
                        show(7);
                    } else {
                        h.postDelayed(this, 1200);
                    }
                }
            }, 1200);
        } catch (Exception e) {
            toast("下载启动失败，请检查网络");
        }
    }

    private JSONObject getDownloadInfo(String idStr) {
        JSONObject o = new JSONObject();
        try {
            long id = Long.parseLong(idStr);
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Query q = new DownloadManager.Query();
            q.setFilterById(id);
            Cursor c = dm.query(q);
            if (c != null && c.moveToFirst()) {
                int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                long bytes = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long total = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                String uri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                c.close();
                o.put("bytes", bytes);
                o.put("total", total);
                o.put("path", uri == null ? "" : uri.replace("file://", ""));
                o.put("done", status == DownloadManager.STATUS_SUCCESSFUL);
                o.put("failed", status == DownloadManager.STATUS_FAILED);
            }
        } catch (Exception ignored) {}
        return o;
    }

    private void useRom(String id) {
        try {
            JSONObject dls = new JSONObject(sp.getString("romDls", "{}"));
            JSONObject e = dls.optJSONObject(id);
            JSONObject r = null;
            for (int i = 0; i < catalog.length(); i++) if (id.equals(catalog.getJSONObject(i).optString("id", ""))) r = catalog.getJSONObject(i);
            if (e == null || r == null || e.optString("path", "").isEmpty()) { toast("ROM 尚未下载完成"); return; }
            openWizard();
            wizard.put("bits", r.optString("bits", "32"));
            wizardStep = 2;
            show(2);
            analyze(e.optString("path", ""), e.optString("file", "rom.iso"), (r.optInt("sizeMb", 0)) * 1048576L);
        } catch (Exception ignored) {}
    }

    /* ================= 连接储存 ================= */
    private View buildFiles() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        vpad(p, 18);
        sv.addView(p);

        LinearLayout top = rowWrap();
        Button back = btn("‹", 0xFF1E2D50, v -> show(5));
        back.setTextSize(22);
        top.addView(back);
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(dp(12), 0, 0, 0);
        tt.addView(text("连接储存", 19, TXT, 1));
        tt.addView(subText("单向连接 · 真机 → 虚拟机"));
        top.addView(tt);
        Button send = btn("从真机发送", 0xFF182442, v -> pickFile("sendfile", String.valueOf(curVm.optLong("id", 0))));
        send.setTextSize(11);
        top.addView(send);
        p.addView(top);

        /* Tabs */
        LinearLayout tabs = rowWrap();
        Button tv = btn("虚拟机存储", 0x1F2F7BFF, v -> { filesTab = "vm"; renderFiles(); refreshTabs(tabs); });
        Button ts = btn("真机共享", 0xFF101A32, v -> { filesTab = "shared"; renderFiles(); refreshTabs(tabs); });
        tv.setTextColor(ACCENT);
        tabs.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        tabs.addView(ts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        p.addView(tabs);

        TextView hint = subText("真机共享目录：" + sharedDir().getAbsolutePath() + "（把文件放进去，虚拟机即可查看/复制）");
        p.addView(hint);

        filesList = new LinearLayout(this);
        filesList.setOrientation(LinearLayout.VERTICAL);
        p.addView(filesList);
        renderFiles();
        return sv;
    }

    private void refreshTabs(LinearLayout tabs) {
        if (tabs == null || tabs.getChildCount() < 2) return;
        Button a = (Button) tabs.getChildAt(0);
        Button b = (Button) tabs.getChildAt(1);
        boolean vm = "vm".equals(filesTab);
        a.setBackground(round(vm ? 0x1F2F7BFF : 0xFF101A32, 11));
        a.setTextColor(vm ? ACCENT : TXT);
        b.setBackground(round(vm ? 0xFF101A32 : 0x1F2F7BFF, 11));
        b.setTextColor(vm ? TXT : ACCENT);
    }

    private File vmFilesDir(String vmId) {
        File d = new File(getExternalFilesDir(null), "vms/" + vmId + "/files");
        if (!d.exists()) d.mkdirs();
        return d;
    }
    private File sharedDir() {
        File d = new File(getExternalFilesDir(null), "shared");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private void renderFiles() {
        if (filesList == null) return;
        filesList.removeAllViews();
        File dir = "vm".equals(filesTab) ? vmFilesDir(String.valueOf(curVm.optLong("id", 0))) : sharedDir();
        File[] fs = dir.listFiles();
        if (fs == null || fs.length == 0) {
            filesList.addView(text("vm".equals(filesTab)
                    ? "虚拟机存储为空，可从「真机共享」复制文件"
                    : "真机共享目录为空，点击\"从真机发送\"或把文件放入共享目录", 13, 0xFF46546E, 0));
            return;
        }
        java.util.Arrays.sort(fs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (final File f : fs) {
            LinearLayout item = card();
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            boolean img = f.getName().toLowerCase().matches(".*\\.(png|jpe?g|gif|webp)$");
            TextView t = tile(img ? "图" : "文", img ? 0xFF7C4DFF : BLUE);
            item.addView(t, new LinearLayout.LayoutParams(dp(40), dp(40)));
            LinearLayout tt = new LinearLayout(this);
            tt.setOrientation(LinearLayout.VERTICAL);
            tt.setPadding(dp(11), 0, 0, 0);
            tt.addView(text(f.getName(), 13.5f, TXT, 1));
            tt.addView(subText(fmtSize(f.length()) + (f.isDirectory() ? " · 目录" : "")));
            item.addView(tt);
            LinearLayout acts = new LinearLayout(this);
            acts.setOrientation(LinearLayout.HORIZONTAL);
            Button view = btn("查看", 0xFF182442, v -> viewFile(f));
            acts.addView(view);
            if ("vm".equals(filesTab)) {
                Button del = btn("删除", 0xFF182442, v -> deleteVmFile(f));
                acts.addView(del);
            } else {
                Button cp = btnPrimary("复制到虚拟机", v -> copyToVm(f));
                acts.addView(cp);
            }
            item.addView(acts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            ((LinearLayout) item.getChildAt(item.getChildCount() - 1)).setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            filesList.addView(item);
        }
    }

    private void viewFile(File f) {
        if (f.isDirectory()) { toast("暂不支持子目录"); return; }
        String name = f.getName();
        if (name.toLowerCase().matches(".*\\.(png|jpe?g|gif|webp)$")) {
            Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (bmp != null) {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(bmp);
                iv.setPadding(dp(8), dp(8), dp(8), dp(8));
                new AlertDialog.Builder(this).setTitle("查看 · " + name).setView(iv).setPositiveButton("关闭", null).show();
                return;
            }
            toast("图片无法预览");
            return;
        }
        String content = readText(f, 512);
        if ("BINARY".equals(content)) {
            new AlertDialog.Builder(this).setTitle("查看 · " + name)
                    .setMessage("二进制文件，暂不支持文本预览（可复制到虚拟机使用）")
                    .setPositiveButton("关闭", null).show();
            return;
        }
        TextView body = text(content.isEmpty() ? "(空文件)" : content, 12, 0xFFBFE8FF, 0);
        body.setTypeface(Typeface.MONOSPACE);
        body.setPadding(dp(8), dp(8), dp(8), dp(8));
        new AlertDialog.Builder(this).setTitle("查看 · " + name).setView(body).setPositiveButton("关闭", null).show();
    }

    private String readText(File f, int limitKb) {
        try {
            if (!f.exists() || f.length() == 0) return "";
            int limit = Math.max(8, limitKb) * 1024;
            byte[] buf = new byte[(int) Math.min(f.length(), limit)];
            InputStream in = new FileInputStream(f);
            int n = in.read(buf);
            in.close();
            for (int i = 0; i < Math.min(n, 2048); i++) if (buf[i] == 0) return "BINARY";
            return new String(buf, 0, Math.max(n, 0), "UTF-8");
        } catch (Exception e) { return ""; }
    }

    private void copyToVm(File src) {
        try {
            File dst = new File(vmFilesDir(String.valueOf(curVm.optLong("id", 0))), src.getName());
            if (dst.exists()) { toast("已存在同名文件"); return; }
            copyFile(src, dst);
            toast("已复制到虚拟机存储");
            renderFiles();
        } catch (Exception e) { toast("复制失败"); }
    }

    private void deleteVmFile(File f) {
        new AlertDialog.Builder(this)
                .setTitle("删除文件")
                .setMessage("确定删除虚拟机中的「" + f.getName() + "」吗？")
                .setPositiveButton("删除", (d, w) -> { if (f.delete()) toast("已删除"); renderFiles(); })
                .setNegativeButton("取消", null)
                .show();
    }

    /* ================= 开发者 ================= */
    private View buildDev() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        vpad(p, 18);
        sv.addView(p);
        LinearLayout top = rowWrap();
        Button back = btn("‹", 0xFF1E2D50, v -> show(5));
        back.setTextSize(22);
        top.addView(back);
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(dp(12), 0, 0, 0);
        tt.addView(text("开发者选项", 19, TXT, 1));
        tt.addView(subText("虚拟机系统调试"));
        top.addView(tt);
        p.addView(top);
        CheckBox[] cbs = new CheckBox[4];
        String[][] items = {{"USB 调试", "虚拟机 ADB 调试通道"}, {"指针位置", "显示触摸坐标"}, {"显示刷新率", "FPS 悬浮显示"}, {"悬浮球演示", "测试悬浮窗控制"}};
        for (int i = 0; i < 4; i++) {
            cbs[i] = new CheckBox(this);
            cbs[i].setText(items[i][0] + "（" + items[i][1] + "）");
            cbs[i].setTextColor(TXT);
            p.addView(cbs[i]);
        }
        p.addView(text("动画缩放", 13, SUB, 1));
        seg(p, new String[]{"0.5x", "1x", "1.5x"}, 0, idx -> toast("动画缩放：" + (idx == 0 ? "0.5x" : idx == 1 ? "1x" : "1.5x")));
        Button back2 = btnPrimary("返回虚拟机", v -> show(5));
        p.addView(back2, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        ((LinearLayout.LayoutParams) back2.getLayoutParams()).topMargin = dp(18);
        return sv;
    }

    /* ================= 关于 & 授权 ================= */
    private View buildAbout() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        vpad(p, 18);
        sv.addView(p);
        LinearLayout top = rowWrap();
        Button back = btn("‹", 0xFF1E2D50, v -> show(1));
        back.setTextSize(22);
        top.addView(back);
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(dp(12), 0, 0, 0);
        tt.addView(text("关于 & 授权", 19, TXT, 1));
        tt.addView(subText("设备信息 · 授权码 · 防注入"));
        top.addView(tt);
        p.addView(top);

        LinearLayout c = card();
        addDetectRow(c, "设备型号", Build.BRAND + " " + Build.MODEL, TXT);
        addDetectRow(c, "Android 版本", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")", TXT);
        addDetectRow(c, "CPU 架构", androidBuildAbi(), TXT);
        addDetectRow(c, "应用版本", "ET虚拟机 v3.0.0", TXT);
        addDetectRow(c, "签名指纹", sigHex.isEmpty() ? "N/A" : sigHex.substring(0, 16) + "…", OK);
        addDetectRow(c, "防注入状态", EXPECTED_SIG.equals("PENDING") || EXPECTED_SIG.equalsIgnoreCase(sigHex) ? "✓ 签名有效" : "✗ 签名异常", EXPECTED_SIG.equals("PENDING") || EXPECTED_SIG.equalsIgnoreCase(sigHex) ? OK : BAD);
        String lk = License.licenseKey(sigHex, androidId);
        addDetectRow(c, "设备授权码", lk, ACCENT);
        p.addView(c);

        p.addView(sectionTitle("权限状态"));
        LinearLayout pc = card();
        String[][] pk = {{"存储 & 麦克风", "storage"}, {"悬浮窗", "overlay"}, {"安装未知应用", "install"}, {"通知", "notify"}};
        for (String[] kv : pk) {
            boolean ok = hasPerm(kv[1]);
            addDetectRow(pc, kv[0], ok ? "✓ 已授权" : "未授权", ok ? OK : SUB);
        }
        p.addView(pc);

        p.addView(sectionTitle("说明"));
        LinearLayout note = card();
        note.addView(text("本应用为 ET虚拟机 v3.0.0 原生 Java 版（无 WebView），内置 ET-OS 7.0 精简系统（32 位，≤100MB）即装即用；支持 ROM 商店、连接储存（真机→虚拟机单向共享）、应用兼容检测、120Hz、自定义开机动画，以及签名防注入与设备授权码。© ET 协会。", 13, 0xFF9FB2D6, 0));
        p.addView(note);
        return sv;
    }

    private String androidBuildAbi() {
        String[] abis = Build.SUPPORTED_ABIS;
        return abis == null || abis.length == 0 ? "unknown" : abis[0];
    }

    /* ================= 文件选择 ================= */
    private void pickFile(String kind, String target) {
        pendingPickKind = kind;
        pendingPickTarget = target == null ? "" : target;
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("*/*");
        it.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/octet-stream", "image/*", "application/vnd.android.package-archive", "*/*"});
        try {
            startActivityForResult(Intent.createChooser(it, "选择文件"), RC_FILE);
        } catch (Exception e) {
            toast("无法打开文件选择器");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_FILE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                handlePickedFile(data.getData());
            } else {
                toast("已取消选择");
            }
        } else if (requestCode == RC_OVERLAY_SETTINGS) {
            boolean ok = Settings.canDrawOverlays(this);
            perms.put("overlay", ok);
            refreshPerms();
        } else if (requestCode == RC_INSTALL_SETTINGS) {
            boolean ok = Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
            perms.put("install", ok);
            refreshPerms();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean all = grantResults.length > 0;
        for (int g : grantResults) if (g != PackageManager.PERMISSION_GRANTED) { all = false; break; }
        if (requestCode == RC_STORAGE) perms.put("storage", all);
        else if (requestCode == RC_NOTIFY) perms.put("notify", all);
        refreshPerms();
    }

    private void handlePickedFile(Uri uri) {
        final String kind = pendingPickKind;
        final String target = pendingPickTarget;
        new Thread(() -> {
            try {
                ContentResolver cr = getContentResolver();
                String name = queryName(uri);
                if (name == null || name.isEmpty()) name = "upload_" + System.currentTimeMillis();
                File dir;
                if ("sendfile".equals(kind) && !target.isEmpty()) {
                    dir = vmFilesDir(target);
                } else {
                    dir = new File(getExternalFilesDir(null), "uploads/" + kind);
                    if (!dir.exists()) dir.mkdirs();
                }
                File dst = new File(dir, name);
                try (InputStream in = cr.openInputStream(uri); OutputStream out = new FileOutputStream(dst)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                final String path = dst.getAbsolutePath();
                final String fname = name;
                final long size = dst.length();
                h.post(() -> fileReady(kind, path, fname, size, target));
            } catch (Exception e) {
                h.post(() -> toast("读取文件失败"));
            }
        }).start();
    }

    private void fileReady(String kind, String path, String name, long size, String target) {
        if ("rom".equals(kind)) {
            analyze(path, name, size);
        } else if ("bootanim".equals(kind) && curVm != null) {
            try {
                curVm.put("animPath", path);
                curVm.put("anim", "自定义");
                saveVms();
                if (animHint != null) animHint.setText("已选择：自定义图片（" + name + "）");
                toast("自定义开机动画已导入");
            } catch (Exception ignored) {}
        } else if ("sendfile".equals(kind)) {
            toast("已从真机发送「" + name + "」到虚拟机");
            if (scr == 8) show(8);
        }
    }

    private String queryName(Uri uri) {
        try {
            String[] proj = {"_display_name"};
            Cursor c = getContentResolver().query(uri, proj, null, null, null);
            if (c != null) {
                try { if (c.moveToFirst()) return c.getString(0); } finally { c.close(); }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /* ================= 时钟 ================= */
    private void clockTick() {
        h.post(new Runnable() {
            @Override public void run() {
                if (vmClock != null) {
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    vmClock.setText(String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)));
                }
                h.postDelayed(this, 1000);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (scr == 5) { toast("正在关机…"); stopFloat(); show(1); return; }
        if (scr == 2 && wizardStep > 1) { wizardBack(); return; }
        if (scr != 1 && scr != 0 && scr != 4) { show(1); return; }
        super.onBackPressed();
    }
}
