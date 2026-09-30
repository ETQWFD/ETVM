package com.et.vm;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * ET虚拟机 · Windows 桌面版（纯 Java Swing 原生实现，无 WebView）
 * 与 Android 版同款：内置 ET-OS 7.0(32位,不可删除) · ROM商店 · 连接储存 · 三语言 · 多主题 · 检查更新 · 防注入
 * ET协会出品 · © ET
 */
public class ETVMPC {

    /* ===== 防注入：本 jar 的 SHA-256（构建后回填，两遍编译） ===== */
    static String selfHash = "";
    static boolean selfOk = true;
    static final String APP_VER = "3.3.0";
    static final String REPO = "ETQWFD/ETVM";

    /* 配色（随主题切换） */
    static int BG = 0xFF070B15, CARD = 0xFF132246, CARD2 = 0xFF0D1729,
            LINE = 0xFF1E2E4F, ACCENT = 0xFF00E5FF, BLUE = 0xFF2F7BFF,
            TXT = 0xFFE8EEFB, SUB = 0xFF7482A3, OK = 0xFF2EE6A8,
            BAD = 0xFFFF6B6B, WARN = 0xFFFFC44D, GREEN = 0xFF00B894;
    static final int[][] THEMES = {
            {0xFF070B15, 0xFF132246, 0xFF0D1729, 0xFF1E2E4F, 0xFF00E5FF, 0xFF2F7BFF, 0xFFE8EEFB, 0xFF7482A3, 0xFF2EE6A8, 0xFFFF6B6B, 0xFFFFC44D, 0xFF00B894},
            {0xFF030A1A, 0xFF0E2A5C, 0xFF081B3A, 0xFF1D3E7E, 0xFF7FD8FF, 0xFF3E8BFF, 0xFFEAF2FF, 0xFF7E96C0, 0xFF37E6B8, 0xFFFF6B7A, 0xFFFFCE54, 0xFF2EE6A8},
            {0xFF04120D, 0xFF0E3A2A, 0xFF07261B, 0xFF1B5340, 0xFF6CF7C4, 0xFF2FBF8F, 0xFFEAFBF3, 0xFF6FA892, 0xFF37E6B8, 0xFFFF6B7A, 0xFFFFCE54, 0xFF2EE6A8},
            {0xFF0C0818, 0xFF2A1A55, 0xFF181036, 0xFF3B2874, 0xFFC89BFF, 0xFF7C4DFF, 0xFFF2ECFF, 0xFF9381B8, 0xFF7CE6B8, 0xFFFF6B9A, 0xFFFFCE54, 0xFFB44DFF},
            {0xFF150A06, 0xFF3A2412, 0xFF221407, 0xFF55331A, 0xFFFFC98F, 0xFFFF8F43, 0xFFFFF3E6, 0xFFB08968, 0xFF37E6B8, 0xFFFF6B6B, 0xFFFFCE54, 0xFFFFA53F},
    };

    /* ===== 多语言 ===== */
    static String lang = "zh";
    static final String[][] STR = {
            {"welcome", "欢迎进入 ET 虚拟机 · Windows 原生引擎\n内置 ET-OS 7.0 (32位) 已就绪", "Welcome to ET VM · Windows Native Engine\nET-OS 7.0 (32-bit) bundled & ready", "ET仮想マシンへようこそ · Windowsネイティブエンジン\nET-OS 7.0（32bit）内蔵・準備完了"},
            {"enterHome", "进入首页", "Enter Home", "ホームへ"},
            {"welcomeTitle", "欢迎回来", "Welcome Back", "おかえりなさい"},
            {"homeSub", "ET 虚拟机管理台 · 原生 Java 引擎", "ET VM Console · Native Java Engine", "ET仮想マシン管理台 · ネイティブJavaエンジン"},
            {"m1", "我的机器", "My Machines", "マイマシン"},
            {"m1d", "管理已创建的虚拟机", "Manage created VMs", "作成済みVMの管理"},
            {"m2", "切换机 · 创建虚拟机", "Switch · Create VM", "切替 · VM作成"},
            {"m2d", "内置 ET-OS 7.0 即装即用 · 也可上传 ROM", "Built-in ET-OS 7.0 ready, or upload ROM", "内蔵ET-OS 7.0 即時利用可 · ROMアップロードも可"},
            {"m3", "应用中心", "App Center", "アプリセンター"},
            {"m3d", "导入 APK 到虚拟机（自动检测位数）", "Import APKs (ABI auto-checked)", "APKをインポート（ABI自動判定）"},
            {"m4", "ROM 商店", "ROM Store", "ROMストア"},
            {"m4d", "下载官方精简系统镜像（≤400MB）", "Download official lite images (≤400MB)", "公式ライトイメージ（≤400MB）をダウンロード"},
            {"m5", "关于 & 授权", "About & License", "情報 & ライセンス"},
            {"m5d", "设备信息 · 授权码 · 防注入", "Device info · License · Anti-injection", "端末情報 · ライセンス · 改ざん防止"},
            {"noVm", "还没有虚拟机，点击创建", "No VM yet, tap to create", "VMがありません。作成をタップ"},
            {"start", "启动", "Start", "起動"},
            {"settings", "设置", "Settings", "設定"},
            {"delete", "删除", "Delete", "削除"},
            {"ready", "已就绪", "Ready", "準備完了"},
            {"firstBoot", "待首启", "First boot", "初回起動待ち"},
            {"builtin", "内置", "Built-in", "内蔵"},
            {"createVm", "创建虚拟机", "Create VM", "VM作成"},
            {"lang", "语言", "Language", "言語"},
            {"theme", "主题", "Theme", "テーマ"},
            {"checkUpdate", "检查更新", "Check Updates", "更新を確認"},
            {"updateTitle", "发现新版本", "New Version Found", "新しいバージョンが見つかりました"},
            {"updateAsk", "发现新版本 %s\n是否立即下载？", "New version %s available.\nDownload now?", "新しいバージョン %s があります。\n今すぐダウンロードしますか？"},
            {"updateYes", "下载", "Download", "ダウンロード"},
            {"updateNo", "暂不", "Later", "後で"},
            {"updateLatest", "已是最新版本", "You are up to date", "最新バージョンです"},
            {"updateErr", "检查更新失败，请检查网络", "Update check failed, check network", "更新確認に失敗しました。ネットワークを確認してください"},
            {"wName", "虚拟机名称", "VM Name", "VM名"},
            {"wBits", "系统位数", "System Bits", "システムビット"},
            {"wRom", "使用内置 ET-OS 7.0（32位 · 已安装好 · 不可删除）", "Use bundled ET-OS 7.0 (32-bit, installed, locked)", "内蔵ET-OS 7.0（32bit・インストール済・ロック）を使用"},
            {"wUpload", "上传自己的 ROM 镜像\n支持 .zip / .img / .iso，仅支持安卓", "Upload your ROM image\n.zip / .img / .iso — Android only", "ROMイメージをアップロード\n.zip / .img / .iso — Androidのみ"},
            {"wPerm", "环境", "Environment", "環境"},
            {"wPermPc", "桌面版无需申请系统权限，直接可用", "Desktop version needs no system permissions", "デスクトップ版は権限不要でそのまま利用できます"},
            {"wConf", "高级设置", "Advanced", "詳細設定"},
            {"fps", "刷新频率", "Refresh Rate", "リフレッシュレート"},
            {"fpsHint", "最高 120 帧，默认 60 帧", "Up to 120 fps, default 60", "最大120fps、デフォルト60"},
            {"bootAnim", "开机动画", "Boot Animation", "起動アニメ"},
            {"gapps", "安装 Google 三件套", "Install Google Apps", "Googleアプリをインストール"},
            {"xposed", "安装 Xposed 框架", "Install Xposed", "Xposedをインストール"},
            {"root", "安装 Root 工具", "Install Root tools", "Rootツールをインストール"},
            {"verNum", "虚拟机版本号", "VM Version Number", "VMバージョン番号"},
            {"create", "正式创建", "Create", "作成"},
            {"bootInstall", "正在启动系统…", "Booting system…", "システムを起動中…"},
            {"bootDone", "启动完成，进入系统", "Booted. Entering system…", "起動完了。システムへ"},
            {"shareBanner", "连接储存已连接：真机单向发送文件 → 虚拟机内查看", "Shared storage connected: device → VM one-way files", "共有ストレージ接続：端末 → VM 一方通行ファイル"},
            {"stApps", "系统自带应用", "Built-in Apps", "内蔵アプリ"},
            {"stImport", "导入 APK 文件", "Import APK File", "APKファイルをインポート"},
            {"stImportHint", "导入后自动检测位数兼容性，仅支持当前虚拟机位数的应用才会被安装。", "ABI checked automatically; only compatible apps install.", "ABI互換性を自動判定し、互換アプリのみインストールされます。"},
            {"filesTitle", "连接储存", "Shared Storage", "共有ストレージ"},
            {"filesV", "虚拟机存储", "VM Storage", "VMストレージ"},
            {"filesS", "真机共享", "Device Shared", "端末共有"},
            {"romTitle", "ROM 商店", "ROM Store", "ROMストア"},
            {"refresh", "刷新列表", "Refresh", "更新"},
            {"dl", "下载", "Download", "ダウンロード"},
            {"useRom", "使用此 ROM", "Use this ROM", "このROMを使用"},
            {"aboutTitle", "关于 & 授权", "About & License", "情報 & ライセンス"},
            {"sigOk", "✓ 校验通过", "✓ Verified", "✓ 検証OK"},
            {"sigBad", "✗ 校验异常", "✗ Invalid", "✗ 不正"},
            {"openFolder", "打开文件夹", "Open Folder", "フォルダを開く"},
            {"themeDark", "深空黑", "Deep Space", "ディープスペース"},
            {"themeOcean", "深海蓝", "Ocean Blue", "オーシャンブルー"},
            {"themeForest", "翡翠绿", "Emerald", "エメラルド"},
            {"themeViolet", "暗夜紫", "Violet Night", "バイオレットナイト"},
            {"themeSunset", "落日橙", "Sunset", "サンセット"},
            {"zh", "中文", "Chinese", "中国語"},
            {"en", "English", "English", "英語"},
            {"ja", "日本語", "Japanese", "日本語"},
            {"wizardStep", "第 %d 步 / 共 4 步", "Step %d of 4", "ステップ %d / 4"},
            {"back", "返回", "Back", "戻る"},
            {"next", "下一步", "Next", "次へ"},
            {"dlDone", "下载完成，保存至：", "Downloaded to: ", "ダウンロード完了： "},
            {"noPerms", "桌面版无需系统权限", "No permissions needed on desktop", "デスクトップ版は権限不要"},
            {"installing", "正在安装系统…", "Installing system…", "システムをインストール中…"},
            {"installed", "已安装 · 连接储存可用", "Installed · Shared Storage ready", "インストール済み · 共有ストレージ利用可"},
    };
    static String S(String k) {
        for (String[] row : STR) if (row[0].equals(k)) {
            int idx = "en".equals(lang) ? 2 : ("ja".equals(lang) ? 3 : 1);
            return idx < row.length && !row[idx].isEmpty() ? row[idx] : row[1];
        }
        return k;
    }
    static String SF(String k, Object... a) { return String.format(S(k), a); }

    /* ===== 数据 ===== */
    static File dataDir = new File(System.getProperty("user.home"), "ETVM-data");
    static File sharedDir = new File(dataDir, "shared");
    static File vmDir = new File(dataDir, "vm");
    static File romsDir = new File(dataDir, "roms");
    static File cfgFile = new File(dataDir, "config.properties");
    static File vmsFile = new File(dataDir, "vms.json");
    static JSONArray vms = new JSONArray();
    static JSONObject curVm = null;
    static Properties cfg = new Properties();
    static String deviceId = "";

    static JFrame win;
    static CardLayout cards = new CardLayout();
    static JPanel root;
    static final Map<String, JComponent> screens = new LinkedHashMap<>();

    public static void main(String[] a) throws Exception {
        /* 防注入校验：读取同目录 hash.txt 与自身哈希比对（无自引用，构建稳定） */
        try {
            File loc = new File(ETVMPC.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (loc.isFile() && loc.exists()) {
                selfHash = sha256Hex(Files.readAllBytes(loc.toPath()));
                File ht = new File(loc.getParentFile(), "hash.txt");
                if (ht.exists()) {
                    String expected = new String(Files.readAllBytes(ht.toPath()), StandardCharsets.UTF_8).trim();
                    selfOk = expected.equalsIgnoreCase(selfHash);
                    if (!selfOk) {
                        JOptionPane.showMessageDialog(null, "校验异常：ET虚拟机 已被修改或注入\n请从官网重新下载（官方 SHA-256 见官网）", "ET虚拟机", JOptionPane.ERROR_MESSAGE);
                        System.exit(1);
                        return;
                    }
                } else {
                    JOptionPane.showMessageDialog(null, "提示：未找到 hash.txt 完整性清单，跳过校验", "ET虚拟机", JOptionPane.WARNING_MESSAGE);
                }
            }
        } catch (Exception ignored) {}
        ensureDirs();
        loadAll();
        deviceId = java.net.InetAddress.getLocalHost().getHostName() + "-" + cfg.getProperty("seed", UUID.randomUUID().toString());
        cfg.setProperty("seed", deviceId.split("-")[1]);
        saveCfg();

        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        win = new JFrame("ET虚拟机 " + APP_VER + " · ET协会");
        win.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        win.setSize(1000, 700);
        win.setMinimumSize(new Dimension(880, 620));
        win.setLocationRelativeTo(null);
        win.setIconImage(genLogo(64));
        root = new JPanel(cards);
        root.setBackground(c(BG));
        win.setContentPane(root);

        screens.put("home", buildHome());
        screens.put("wizard", buildWizard());
        screens.put("settings", buildSettings());
        screens.put("boot", buildBoot());
        screens.put("desktop", buildDesktop());
        screens.put("apps", buildApps());
        screens.put("store", buildStore());
        screens.put("files", buildFiles());
        screens.put("about", buildAbout());
        for (Map.Entry<String, JComponent> e : screens.entrySet()) root.add(e.getKey(), e.getValue());
        /* 无欢迎动画，直接进入首页 */
        show("home");
        win.setVisible(true);
        new javax.swing.Timer(60000, e -> checkUpdate(false)).start();
    }

    /* ===== 工具 ===== */
    static Color c(int rgb) { return new Color(rgb); }
    static String sha256Hex(byte[] b) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest(b)) sb.append(String.format("%02x", x));
        return sb.toString();
    }
    static byte[] readSelf() throws Exception {
        File f = new File(ETVMPC.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (f.isFile()) return Files.readAllBytes(f.toPath());
        return new byte[0];
    }
    static void ensureDirs() { for (File d : new File[]{dataDir, sharedDir, vmDir, romsDir}) if (!d.exists()) d.mkdirs(); }
    static void loadAll() {
        try { if (cfgFile.exists()) cfg.load(new FileInputStream(cfgFile)); } catch (Exception ignored) {}
        lang = cfg.getProperty("lang", "zh");
        applyTheme(Integer.parseInt(cfg.getProperty("theme", "0")));
        try { vms = new JSONArray(Files.readString(vmsFile.toPath())); } catch (Exception e) { vms = new JSONArray(); }
        seedBuiltIn();
    }
    static void saveCfg() { try { cfg.store(new FileOutputStream(cfgFile), "ETVM-PC"); } catch (Exception ignored) {} }
    static void saveVms() { try { Files.writeString(vmsFile.toPath(), vms.toString()); } catch (Exception ignored) {} }
    static void applyTheme(int id) {
        int[] t = THEMES[Math.max(0, Math.min(id, THEMES.length - 1))];
        BG = t[0]; CARD = t[1]; CARD2 = t[2]; LINE = t[3]; ACCENT = t[4]; BLUE = t[5];
        TXT = t[6]; SUB = t[7]; OK = t[8]; BAD = t[9]; WARN = t[10]; GREEN = t[11];
        cfg.setProperty("theme", String.valueOf(id));
    }
    static void seedBuiltIn() {
        boolean has = false;
        for (int i = 0; i < vms.length(); i++) { try { if (vms.getJSONObject(i).optBoolean("builtin", false)) has = true; } catch (Exception ignored) {} }
        if (has) return;
        try {
            JSONObject vm = new JSONObject();
            vm.put("id", 1).put("name", "ET-OS 7.0 (内置)").put("bits", "32").put("ver", "Android 7.0")
               .put("model", "内置精简机型").put("builtin", true)
               .put("version", "ET-OS 7.0 (2026.09)").put("fps", 60).put("anim", "ET 经典")
               .put("gapps", false).put("xposed", false).put("root", false).put("firstBoot", false)
               .put("created", System.currentTimeMillis());
            JSONObject rom = new JSONObject();
            rom.put("name", "内置 ET-OS 7.0 精简系统").put("size", 4218001L).put("bits", "32").put("bundled", true);
            vm.put("rom", rom);
            JSONArray inst = new JSONArray();
            inst.put(new JSONObject().put("label", "连接储存").put("pkg", "com.et.storage").put("abi", "内置 · 共享"));
            vm.put("installed", inst);
            JSONArray next = new JSONArray();
            next.put(vm);
            for (int i = 0; i < vms.length(); i++) next.put(vms.getJSONObject(i));
            vms = next;
            saveVms();
        } catch (Exception ignored) {}
    }
    static void show(String key) {
        cards.show(root, key);
        Runnable r = refreshers.get(key);
        if (r != null) r.run();
    }
    static final Map<String, Runnable> refreshers = new HashMap<>();

    /* 通用组件 */
    static JLabel lab(String s, float size, int color, boolean bold) {
        JLabel l = new JLabel(s);
        l.setFont(new Font("Microsoft YaHei", bold ? Font.BOLD : Font.PLAIN, (int) size));
        l.setForeground(c(color));
        return l;
    }
    static JButton btn(String s, int bg) {
        JButton b = new JButton(s);
        b.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        b.setForeground(c(bg == BLUE || bg == ACCENT ? 0xFF03121C : TXT));
        b.setBackground(c(bg));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }
    static JPanel panel(int bg) { JPanel p = new JPanel(); p.setBackground(c(bg)); return p; }
    static JPanel card() {
        JPanel p = panel(CARD);
        p.setBorder(BorderFactory.createLineBorder(c(LINE)));
        return p;
    }
    static JPanel row() { JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4)); p.setOpaque(false); return p; }
    static JPanel vbox() { JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setOpaque(false); return p; }
    static JScrollPane scroller(JComponent c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(null);
        s.getViewport().setBackground(c(BG));
        s.getVerticalScrollBar().setUnitIncrement(18);
        return s;
    }
    static BufferedImage genLogo(int px) {
        BufferedImage im = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(c(BLUE));
        g.fillRoundRect(0, 0, px, px, px / 4, px / 4);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, px / 2));
        FontMetrics fm = g.getFontMetrics();
        String s = "ET";
        g.drawString(s, (px - fm.stringWidth(s)) / 2, (px + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
        return im;
    }
    static BufferedImage genWallpaper(int w, int h) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        for (int y = 0; y < h; y++) {
            float t = y / (float) h;
            g.setColor(new Color(
                    (int) (blend(0x07, 0x1f, t)), (int) (blend(0x0B, 0x4F, t)), (int) (blend(0x15, 0xB0, t))));
            g.drawLine(0, y, w, y);
        }
        g.dispose();
        return im;
    }
    static int blend(int a, int b, float t) { return (int) (a + (b - a) * t); }

    /* ===== 欢迎 ===== */
    static JComponent buildWelcome() {
        JPanel p = panel(BG);
        p.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 0, 6, 0);
        g.gridy = 0; p.add(new JLabel(new ImageIcon(genLogo(96))), g);
        g.gridy = 1; JLabel t = lab("ET虚拟机", 30, TXT, true); p.add(t, g);
        g.gridy = 2; p.add(lab(S("welcome"), 13, SUB, false), g);
        g.gridy = 3; JButton b = btn(S("enterHome"), BLUE); b.setPreferredSize(new Dimension(240, 46)); b.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
        b.addActionListener(e -> show("home"));
        p.add(b, g);
        g.gridy = 4; p.add(lab("© ET · v" + APP_VER + " · ET-OS 7.0 内置", 11, new Color(0xFF3E4C68).getRGB(), false), g);
        return p;
    }

    /* ===== 首页 ===== */
    static JComponent buildHome() {
        JPanel p = panel(BG);
        p.setLayout(new BorderLayout(0, 10));
        p.setBorder(new EmptyBorder(18, 22, 18, 22));
        JPanel head = row();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(lab(S("welcomeTitle"), 22, TXT, true));
        head.add(lab(S("homeSub"), 12, SUB, false));
        p.add(head, BorderLayout.NORTH);

        JPanel mid = vbox();
        String[][] menus = {
                {"01", S("m1"), S("m1d"), "home"},
                {"02", S("m2"), S("m2d"), "wizard"},
                {"03", S("m3"), S("m3d"), "apps"},
                {"04", S("m4"), S("m4d"), "store"},
                {"05", S("m5"), S("m5d"), "about"}};
        for (String[] m : menus) {
            JPanel item = card();
            item.setLayout(new BorderLayout(10, 0));
            item.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(c(LINE)), new EmptyBorder(10, 12, 10, 12)));
            JLabel num = lab(m[0], 11, ACCENT, true);
            JPanel tt = vbox();
            tt.add(lab(m[1], 15, TXT, true));
            tt.add(lab(m[2], 12, SUB, false));
            item.add(num, BorderLayout.WEST);
            item.add(tt, BorderLayout.CENTER);
            item.add(lab("›", 20, new Color(0xFF41507A).getRGB(), false), BorderLayout.EAST);
            item.setCursor(new Cursor(Cursor.HAND_CURSOR));
            item.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { show(m[3]); } });
            mid.add(Box.createVerticalStrut(8));
            mid.add(item);
        }
        JPanel list = vbox();
        mid.add(Box.createVerticalStrut(6));
        mid.add(lab("▍" + S("m1"), 15, TXT, true));
        mid.add(list);
        refreshers.put("home", () -> {
            list.removeAll();
            if (vms.length() == 0) { list.add(lab(S("noVm"), 13, SUB, false)); return; }
            for (int i = 0; i < vms.length(); i++) {
                try {
                    JSONObject vm = vms.getJSONObject(i);
                    boolean bi = vm.optBoolean("builtin", false) || vm.optBoolean("bundled", false);
                    JPanel c = card();
                    c.setLayout(new BorderLayout(10, 0));
                    c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(c(LINE)), new EmptyBorder(10, 12, 10, 12)));
                    JPanel tt2 = vbox();
                    tt2.add(lab(vm.optString("name", ""), 15, TXT, true));
                    tt2.add(lab(vm.optString("ver", "") + " · " + vm.optString("bits", "") + " 位" + (bi ? " · " + S("builtin") : ""), 11, SUB, false));
                    c.add(tt2, BorderLayout.CENTER);
                    JPanel acts = row();
                    JButton st = btn(S("start"), BLUE);
                    st.addActionListener(e -> { curVm = vm; startBoot(); });
                    JButton se = btn(S("settings"), 0xFF182442);
                    se.addActionListener(e -> { curVm = vm; show("settings"); });
                    acts.add(st); acts.add(se);
                    if (!bi) {
                        JButton del = btn(S("delete"), 0xFF182442);
                        final int fi = i;
                        del.addActionListener(e -> { vms.remove(fi); saveVms(); refreshers.get("home").run(); });
                        acts.add(del);
                    }
                    c.add(acts, BorderLayout.EAST);
                    list.add(Box.createVerticalStrut(6));
                    list.add(c);
                } catch (Exception ignored) {}
            }
        });
        p.add(scroller(mid), BorderLayout.CENTER);
        return p;
    }

    /* ===== 创建向导 ===== */
    static int wStep = 1;
    static JSONObject wizard = new JSONObject();
    static JComponent buildWizard() {
        JPanel p = panel(BG);
        p.setLayout(new BorderLayout());
        p.setBorder(new EmptyBorder(18, 22, 18, 22));
        JPanel top = vbox();
        JPanel hh = row();
        JButton back = btn("‹", 0xFF1E2D50); back.setFont(new Font("Segoe UI", Font.BOLD, 20));
        back.addActionListener(e -> { if (wStep > 1) { wStep--; show("wizard"); } else show("home"); });
        hh.add(back);
        hh.add(lab(S("createVm"), 20, TXT, true));
        top.add(hh);
        JLabel stepL = lab(SF("wizardStep", wStep), 12, ACCENT, false);
        top.add(stepL);
        p.add(top, BorderLayout.NORTH);

        JPanel body = vbox();
        refreshers.put("wizard", () -> {
            stepL.setText(SF("wizardStep", wStep));
            body.removeAll();
            if (wStep == 1) {
                body.add(lab(S("wName"), 13, SUB, true));
                JTextField name = new JTextField(wizard.optString("name", "我的安卓机"));
                style(name); body.add(name);
                wNameField = name;
                body.add(Box.createVerticalStrut(10));
                body.add(lab(S("wBits"), 13, SUB, true));
                body.add(seg(new String[]{"32 位", "64 位"}, "32".equals(wizard.optString("bits", "32")) ? 0 : 1, i -> wizard.put("bits", i == 0 ? "32" : "64")));
                body.add(Box.createVerticalStrut(10));
                body.add(lab(S("wRom"), 12, OK, false));
            } else if (wStep == 2) {
                body.add(lab(S("wUpload"), 12, SUB, false));
                JButton up = btn(S("wUpload").split("\n")[0], 0xFF182442);
                up.addActionListener(e -> pickRom());
                body.add(up);
                JPanel det = card();
                det.setLayout(new BoxLayout(det, BoxLayout.Y_AXIS));
                JLabel dL = lab("未选择 ROM", 12, SUB, false);
                det.add(dL);
                body.add(det);
                JLabel rres = lab("", 12, OK, false);
                body.add(rres);
                // 上传结果回调
                romDetectLbl = dL; romDetectRes = rres;
            } else if (wStep == 3) {
                body.add(lab(S("wPerm"), 13, SUB, true));
                body.add(lab(S("wPermPc"), 12, OK, false));
            } else {
                body.add(lab(S("wConf"), 13, SUB, true));
                body.add(lab(S("fps") + ": " + S("fpsHint"), 12, SUB, false));
                JSlider fps = new JSlider(60, 120, wizard.optInt("fps", 60));
                fps.setMajorTickSpacing(20); fps.setMinorTickSpacing(5); fps.setPaintTicks(true); fps.setPaintLabels(true);
                fps.setOpaque(false);
                body.add(fps);
                JCheckBox g = new JCheckBox(S("gapps")); g.setOpaque(false); g.setForeground(c(TXT)); g.setSelected(wizard.optBoolean("gapps", false)); body.add(g);
                JCheckBox x = new JCheckBox(S("xposed")); x.setOpaque(false); x.setForeground(c(TXT)); x.setSelected(wizard.optBoolean("xposed", false)); body.add(x);
                JCheckBox r = new JCheckBox(S("root")); r.setOpaque(false); r.setForeground(c(TXT)); r.setSelected(wizard.optBoolean("root", false)); body.add(r);
                body.add(Box.createVerticalStrut(6));
                body.add(lab(S("verNum"), 12, SUB, false));
                JTextField ver = new JTextField(wizard.optString("version", "ET-OS 7.0 (2026.09)"));
                style(ver); body.add(ver);
                JPanel save = vbox();
                body.add(save);
                nextAction = () -> {
                    try {
                        JSONObject vm = new JSONObject();
                        vm.put("id", System.currentTimeMillis());
                        vm.put("name", wNameField == null ? "我的安卓机" : wNameField.getText());
                        vm.put("bits", wizard.optString("bits", "32"));
                        boolean builtin = wizard.optBoolean("useBuiltin", false);
                        JSONObject rom = new JSONObject();
                        if (builtin) {
                            rom.put("name", wizard.optString("builtinName", "内置系统"));
                            rom.put("bits", wizard.optString("bits", "32"));
                            rom.put("bundled", true);
                            rom.put("size", 0);
                            vm.put("bundled", true);
                            vm.put("ver", wizard.optString("builtinVer", "Android 4.4"));
                        } else {
                            rom.put("name", wizard.optString("romName", "自定义 ROM"));
                            rom.put("bits", wizard.optString("bits", "32"));
                            rom.put("path", wizard.optString("romPath", ""));
                            rom.put("size", 0);
                            vm.put("ver", "自定义 ROM");
                        }
                        vm.put("rom", rom);
                        vm.put("model", "ET 精简机型");
                        vm.put("version", ver.getText());
                        vm.put("fps", fps.getValue());
                        vm.put("gapps", g.isSelected());
                        vm.put("xposed", x.isSelected());
                        vm.put("root", r.isSelected());
                        vm.put("firstBoot", !builtin);
                        vm.put("created", System.currentTimeMillis());
                        JSONArray inst = new JSONArray();
                        inst.put(new JSONObject().put("label", "连接储存").put("pkg", "com.et.storage").put("abi", "内置 · 共享"));
                        vm.put("installed", inst);
                        vms.put(vm);
                        saveVms();
                        curVm = vm;
                        JOptionPane.showMessageDialog(win, S("createVm") + " ✓");
                        wStep = 1;
                        wizard = new JSONObject();
                        show("home");
                    } catch (Exception ex) { ex.printStackTrace(); }
                };
            }
            JPanel nav = row();
            JButton next = btn(S("next"), BLUE);
            next.addActionListener(e -> {
                if (wStep < 4) { wStep++; show("wizard"); }
                else if (nextAction != null) nextAction.run();
            });
            nav.add(next);
            body.add(Box.createVerticalStrut(10));
            body.add(nav);
        });
        p.add(scroller(body), BorderLayout.CENTER);
        return p;
    }
    static JLabel romDetectLbl, romDetectRes;
    static JTextField wNameField;
    static Runnable nextAction;
    static void style(JTextField f) {
        f.setBackground(c(0xFF0E162A));
        f.setForeground(c(TXT));
        f.setCaretColor(c(TXT));
        f.setBorder(BorderFactory.createLineBorder(c(LINE)));
    }
    static JPanel seg(String[] labels, int sel, java.util.function.IntConsumer onSel) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setOpaque(false);
        java.util.List<JButton> list = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            JButton b = btn(labels[i], i == sel ? 0x1F2F7BFF : 0xFF101A32);
            b.addActionListener(e -> {
                for (JButton x : list) { x.setBackground(c(0xFF101A32)); }
                b.setBackground(c(0x1F2F7BFF));
                b.setForeground(c(ACCENT));
                onSel.accept(idx);
            });
            if (i == sel) b.setForeground(c(ACCENT));
            list.add(b);
            row.add(b);
        }
        return row;
    }
    static void pickRom() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("ROM (*.zip;*.img;*.iso)", "zip", "img", "iso"));
        if (fc.showOpenDialog(win) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            RomDetect.Result r = RomDetect.detect(f.getAbsolutePath(), f.getName(), f.length());
            if (r.ok && r.type.contains("Android")) {
                wizard.put("bits", r.bits);
                wizard.put("romPath", f.getAbsolutePath());
                wizard.put("useBuiltin", false);
                romDetectLbl.setText(f.getName() + " · " + f.length() / 1048576 + "MB");
                romDetectRes.setText(r.bitsLabel + " · " + (r.bitsLabel.contains("32") || r.bitsLabel.contains("64") ? "✓ 匹配" : "✓"));
            } else {
                romDetectLbl.setText(f.getName() + " · 非安卓镜像");
                romDetectRes.setText("✗ 仅支持安卓系统镜像");
                JOptionPane.showMessageDialog(win, "✗ 仅支持安卓系统镜像（不支持 Windows）", "ET虚拟机", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    /* ===== 设置 ===== */
    static JComponent buildSettings() {
        JPanel p = panel(BG);
        p.setLayout(new BorderLayout());
        p.setBorder(new EmptyBorder(18, 22, 18, 22));
        JPanel top = vbox();
        JPanel hh = row();
        JButton back = btn("‹", 0xFF1E2D50); back.setFont(new Font("Segoe UI", Font.BOLD, 20));
        back.addActionListener(e -> show("home"));
        hh.add(back); hh.add(lab(S("settings"), 20, TXT, true));
        top.add(hh);
        p.add(top, BorderLayout.NORTH);

        JPanel body = vbox();
        refreshers.put("settings", () -> {
            body.removeAll();
            body.add(lab("▍" + S("lang"), 15, TXT, true));
            body.add(seg(new String[]{S("zh"), S("en"), S("ja")}, "en".equals(lang) ? 1 : ("ja".equals(lang) ? 2 : 0), i -> {
                lang = new String[]{"zh", "en", "ja"}[i];
                cfg.setProperty("lang", lang);
                saveCfg();
                rebuildAll();
            }));
            body.add(Box.createVerticalStrut(12));
            body.add(lab("▍" + S("theme"), 15, TXT, true));
            body.add(seg(new String[]{S("themeDark"), S("themeOcean"), S("themeForest"), S("themeViolet"), S("themeSunset")}, Integer.parseInt(cfg.getProperty("theme", "0")), i -> {
                applyTheme(i);
                saveCfg();
                rebuildAll();
            }));
            body.add(Box.createVerticalStrut(12));
            body.add(lab("▍" + S("checkUpdate"), 15, TXT, true));
            JButton upd = btn(S("checkUpdate"), BLUE);
            upd.addActionListener(e -> checkUpdate(true));
            body.add(upd);
            body.add(lab("自动检测 GitHub 最新 Release 版本，发现新版本后可直接下载", 11, SUB, false));

            if (curVm != null) {
                body.add(Box.createVerticalStrut(14));
                body.add(lab("▍" + curVm.optString("name", "") + " · " + S("settings"), 15, TXT, true));
                body.add(lab(S("fps") + ": " + S("fpsHint"), 12, SUB, false));
                JSlider fps = new JSlider(60, 120, curVm.optInt("fps", 60));
                fps.setOpaque(false);
                body.add(fps);
                JCheckBox g = new JCheckBox(S("gapps")); g.setOpaque(false); g.setForeground(c(TXT)); g.setSelected(curVm.optBoolean("gapps", false)); body.add(g);
                JCheckBox x = new JCheckBox(S("xposed")); x.setOpaque(false); x.setForeground(c(TXT)); x.setSelected(curVm.optBoolean("xposed", false)); body.add(x);
                JCheckBox r = new JCheckBox(S("root")); r.setOpaque(false); r.setForeground(c(TXT)); r.setSelected(curVm.optBoolean("root", false)); body.add(r);
                body.add(lab(S("verNum"), 12, SUB, false));
                JTextField ver = new JTextField(curVm.optString("version", ""));
                style(ver);
                body.add(ver);
                JButton save = btn(S("create"), BLUE);
                save.addActionListener(e -> {
                    try {
                        curVm.put("fps", fps.getValue());
                        curVm.put("gapps", g.isSelected());
                        curVm.put("xposed", x.isSelected());
                        curVm.put("root", r.isSelected());
                        if (!ver.getText().trim().isEmpty()) curVm.put("version", ver.getText().trim());
                        saveVms();
                        JOptionPane.showMessageDialog(win, S("settings") + " ✓");
                    } catch (Exception ex) {}
                });
                body.add(save);
            }
        });
        p.add(scroller(body), BorderLayout.CENTER);
        return p;
    }

    static void rebuildAll() {
        /* 语言/主题切换：重建全部界面 */
        for (Map.Entry<String, JComponent> e : screens.entrySet()) {
            JComponent old = e.getValue();
            String key = e.getKey();
            root.remove(old);
            JComponent nw = switch (key) {
                case "welcome" -> buildWelcome();
                case "home" -> buildHome();
                case "wizard" -> buildWizard();
                case "settings" -> buildSettings();
                case "boot" -> buildBoot();
                case "desktop" -> buildDesktop();
                case "apps" -> buildApps();
                case "store" -> buildStore();
                case "files" -> buildFiles();
                case "about" -> buildAbout();
                default -> old;
            };
            screens.put(key, nw);
            root.add(key, nw);
        }
        win.revalidate();
        win.repaint();
        show("settings");
    }

    /* ===== 开机 ===== */
    static JComponent buildBoot() {
        JPanel p = panel(0xFF04070F);
        p.setLayout(new BorderLayout());
        JLabel logo = new JLabel(new ImageIcon(genLogo(100)));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        mid.add(logo);
        mid.add(Box.createVerticalStrut(14));
        JLabel msg = lab(S("bootInstall"), 13, ACCENT, false);
        msg.setAlignmentX(Component.CENTER_ALIGNMENT);
        mid.add(msg);
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setPreferredSize(new Dimension(360, 8));
        bar.setStringPainted(false);
        bar.setOpaque(false);
        bar.setAlignmentX(Component.CENTER_ALIGNMENT);
        mid.add(Box.createVerticalStrut(10));
        mid.add(bar);
        p.add(mid, BorderLayout.CENTER);
        p.putClientProperty("bar", bar);
        p.putClientProperty("msg", msg);
        return p;
    }
    static void startBoot() {
        /* 真实 ROM（ISO）：QEMU 真虚拟化启动，不再模拟 */
        JSONObject rom = curVm.optJSONObject("rom");
        String rp = rom == null ? "" : rom.optString("path", "");
        if (!rp.isEmpty() && new File(rp).exists() && rp.toLowerCase().endsWith(".iso")) {
            launchQemu();
            return;
        }
        show("boot");
        JProgressBar bar = (JProgressBar) screens.get("boot").getClientProperty("bar");
        JLabel msg = (JLabel) screens.get("boot").getClientProperty("msg");
        bar.setValue(0);
        new javax.swing.Timer(40, null) {{
            int[] i = {0};
            addActionListener(e -> {
                i[0] += 3;
                bar.setValue(Math.min(100, i[0]));
                msg.setText(S("bootInstall") + " " + i[0] + "%");
                if (i[0] >= 100) {
                    ((javax.swing.Timer) e.getSource()).stop();
                    curVm.put("lastBoot", System.currentTimeMillis());
                    saveVms();
                    show("desktop");
                }
            });
        }}.start();
    }

    /* ===== QEMU 真虚拟化引擎（光速虚拟机级） ===== */
    static File appDir() {
        File d = new File(System.getProperty("user.dir"));
        if (!new File(d, "qemu/qemu-system-x86_64.exe").exists()) {
            try {
                File j = new File(ETVMPC.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                d = j.getParentFile();
            } catch (Exception ignored) {}
        }
        return d;
    }
    static Process qemuProc;
    static void launchQemu() {
        File qDir = new File(appDir(), "qemu");
        File qemu = new File(qDir, "qemu-system-x86_64.exe");
        File img = new File(qDir, "qemu-img.exe");
        if (!qemu.exists()) {
            JOptionPane.showMessageDialog(win, "缺少虚拟化引擎 qemu-system-x86_64.exe，请重新下载完整包。", "ET虚拟机", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JSONObject rom = curVm.optJSONObject("rom");
        File iso = new File(rom.optString("path", ""));
        if (!iso.exists()) { JOptionPane.showMessageDialog(win, "ROM 镜像不存在：" + iso.getAbsolutePath()); return; }
        File disk = new File(dataDir, "vm-" + curVm.optLong("id", 0) + ".img");
        try {
            if (!disk.exists()) {
                ProcessBuilder pb = new ProcessBuilder(img.getAbsolutePath(), "create", "-f", "qcow2", disk.getAbsolutePath(), "16G");
                Process p = pb.redirectErrorStream(true).start();
                p.waitFor();
            }
            boolean bootC = curVm.optBoolean("qemuBootC", false);
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(qemu.getAbsolutePath());
            cmd.add("-name"); cmd.add("ET虚拟机 - " + curVm.optString("name", "Android"));
            cmd.add("-m"); cmd.add("2048");
            cmd.add("-smp"); cmd.add("2");
            cmd.add("-cpu"); cmd.add("qemu64");
            cmd.add("-vga"); cmd.add("std");
            cmd.add("-usb"); cmd.add("-device"); cmd.add("usb-tablet");
            cmd.add("-boot"); cmd.add("order=" + (bootC ? "c" : "d") + ",menu=on");
            cmd.add("-cdrom"); cmd.add(iso.getAbsolutePath());
            cmd.add("-hda"); cmd.add(disk.getAbsolutePath());
            cmd.add("-netdev"); cmd.add("user,id=n1,hostfwd=tcp::15555-:5555");
            cmd.add("-device"); cmd.add("e1000,netdev=n1");
            cmd.add("-rtc"); cmd.add("base=localtime");
            ProcessBuilder pb2 = new ProcessBuilder(cmd);
            pb2.directory(appDir());
            qemuProc = pb2.start();
            curVm.put("lastBoot", System.currentTimeMillis());
            saveVms();
            new Thread(() -> {
                try {
                    if (!bootC) {
                        JOptionPane.showMessageDialog(win, "已启动 QEMU 虚拟化引擎（首次启动）：\n请在弹出的窗口中按 android-x86 安装向导完成系统安装。\n安装完成并进入系统后，ET文件传输 会自动安装。");
                    }
                    /* adb 自动安装 ET文件传输 */
                    File adb = new File(qDir, "adb.exe");
                    if (adb.exists()) {
                        int tries = 0;
                        while (tries++ < 90) {
                            Thread.sleep(2000);
                            if (runAdb(adb, "connect", "127.0.0.1:15555")) {
                                if (!runAdb(adb, "-s", "127.0.0.1:15555", "wait-for-device")) continue;
                                Thread.sleep(3000);
                                File apk = new File(new File(appDir(), "storage"), "com.et.storage.apk");
                                if (apk.exists()) runAdb(adb, "-s", "127.0.0.1:15555", "install", "-r", apk.getAbsolutePath());
                                if (!curVm.optBoolean("qemuBootC", false)) {
                                    curVm.put("qemuBootC", true);
                                    saveVms();
                                }
                                /* 单向文件同步：电脑 → 虚拟机 /sdcard/Download/os */
                                watchShared(adb);
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }).start();
            /* 电脑剪贴板 → sharedDir/clipboard.txt（watch 会推给虚拟机） */
            startClipWatch();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(win, "虚拟化启动失败：" + ex.getMessage(), "ET虚拟机", JOptionPane.ERROR_MESSAGE);
        }
    }
    static boolean runAdb(File adb, String... args) {
        try {
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(adb.getAbsolutePath()); cmd.addAll(java.util.Arrays.asList(args));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(), "UTF-8");
            return o.contains("connected") || o.contains("device") || o.contains("Success") || o.contains("adb");
        } catch (Exception e) { return false; }
    }
    static void watchShared(File adb) {
        new Thread(() -> {
            java.util.Map<String, Long> seen = new java.util.HashMap<>();
            try {
                runAdb(adb, "-s", "127.0.0.1:15555", "shell", "mkdir", "-p", "/sdcard/Download/os");
            } catch (Exception ignored) {}
            while (true) {
                try {
                    File[] fs = sharedDir.listFiles();
                    if (fs != null) for (File f : fs) {
                        if (!f.isFile()) continue;
                        Long prev = seen.get(f.getName());
                        long cur = f.length() + f.lastModified();
                        if (prev == null || prev != cur) {
                            seen.put(f.getName(), cur);
                            try {
                                runAdb(adb, "-s", "127.0.0.1:15555", "push", f.getAbsolutePath(), "/sdcard/Download/os/" + f.getName());
                            } catch (Exception ignored) {}
                        }
                    }
                    Thread.sleep(2500);
                } catch (Exception ignored) { try { Thread.sleep(5000); } catch (Exception ignored2) {} }
            }
        }).start();
    }
    static java.util.Timer clipTimer;
    static String lastClip = "";
    static void startClipWatch() {
        if (clipTimer != null) return;
        clipTimer = new java.util.Timer(true);
        clipTimer.schedule(new java.util.TimerTask() {
            public void run() {
                try {
                    java.awt.datatransfer.Clipboard cb = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
                    String t = (String) cb.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
                    if (t != null && !t.isEmpty() && !t.equals(lastClip)) {
                        lastClip = t;
                        try (java.io.FileWriter fw = new java.io.FileWriter(new File(sharedDir, "clipboard.txt"))) {
                            fw.write(t);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }, 1500, 1500);
    }

    /* ===== 桌面 ===== */
    static JComponent buildDesktop() {
        JPanel p = new JPanel(new BorderLayout());
        JLabel wall = new JLabel(new ImageIcon(genWallpaper(1280, 720)));
        p.setBorder(BorderFactory.createLineBorder(c(LINE)));
        p.setBackground(c(CARD));
        JPanel banner = panel(0xAA0E1830);
        banner.setBorder(new EmptyBorder(8, 12, 8, 12));
        banner.add(lab(S("shareBanner"), 12, ACCENT, false));
        p.add(banner, BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(0, 5, 10, 10));
        grid.setOpaque(false);
        grid.setBorder(new EmptyBorder(18, 18, 18, 18));
        refreshers.put("desktop", () -> {
            grid.removeAll();
            String[][] apps = {
                    {"浏", "ET 浏览器", "apps"}, {"夹", "连接储存", "files"}, {"设", "系统设置", "settings"},
                    {"相", "相机", "apps"}, {"册", "相册", "apps"}};
            for (String[] ap : apps) grid.add(tile(ap[0], ap[1], () -> {
                if ("files".equals(ap[2])) show("files");
                else if ("settings".equals(ap[2])) show("settings");
                else JOptionPane.showMessageDialog(win, ap[1] + " (内置应用)", "ET虚拟机", JOptionPane.INFORMATION_MESSAGE);
            }));
            if (curVm != null) {
                JSONArray inst = curVm.optJSONArray("installed");
                if (inst != null) for (int i = 0; i < inst.length(); i++) {
                    JSONObject a = inst.optJSONObject(i);
                    if (a != null) {
                        String lbl = a.optString("label", "应用");
                        grid.add(tile(lbl.substring(0, Math.min(1, lbl.length())), lbl, () ->
                                JOptionPane.showMessageDialog(win, lbl + "\n" + (a.optString("abi", "").isEmpty() ? "" : "ABI: " + a.optString("abi")), "ET虚拟机", JOptionPane.INFORMATION_MESSAGE)));
                    }
                }
            }
        });
        p.add(wall, BorderLayout.CENTER);
        wall.setLayout(new BorderLayout());
        wall.add(banner, BorderLayout.NORTH);
        wall.add(grid, BorderLayout.CENTER);
        JPanel dock = panel(0xCC0B1220);
        dock.setLayout(new FlowLayout(FlowLayout.CENTER, 12, 8));
        String[][] dk = {{"⌂ 主页", "home"}, {"☰ 应用", "apps"}, {"▤ 储存", "files"}, {"⏻ 关机", "power"}};
        for (String[] d : dk) {
            JButton b = btn(d[0], 0xFF101A32);
            b.addActionListener(e -> { if ("power".equals(d[1])) show("home"); else show(d[1]); });
            dock.add(b);
        }
        p.add(dock, BorderLayout.SOUTH);
        return p;
    }
    static JComponent tile(String ch, String name, Runnable onClick) {
        JPanel t = card();
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        JLabel ic = lab(ch, 24, Color.WHITE.getRGB(), true);
        ic.setOpaque(true);
        ic.setBackground(c(BLUE));
        ic.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        ic.setAlignmentX(Component.CENTER_ALIGNMENT);
        t.add(ic);
        t.add(Box.createVerticalStrut(6));
        JLabel l = lab(name, 12, TXT, false);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        t.add(l);
        t.setCursor(new Cursor(Cursor.HAND_CURSOR));
        t.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { onClick.run(); } });
        return t;
    }

    /* ===== 应用中心 ===== */
    static JComponent buildApps() {
        JPanel p = panel(BG);
        p.setLayout(new BorderLayout());
        p.setBorder(new EmptyBorder(18, 22, 18, 22));
        JPanel top = vbox();
        JPanel hh = row();
        JButton back = btn("‹", 0xFF1E2D50); back.setFont(new Font("Segoe UI", Font.BOLD, 20));
        back.addActionListener(e -> show("desktop"));
        hh.add(back);
        hh.add(lab(S("m3"), 20, TXT, true));
        top.add(hh);
        p.add(top, BorderLayout.NORTH);
        JPanel body = vbox();
        refreshers.put("apps", () -> {
            body.removeAll();
            body.add(lab("▍" + S("stApps"), 14, TXT, true));
            JPanel grid = new JPanel(new GridLayout(0, 5, 10, 10));
            grid.setOpaque(false);
            for (String[] ap : new String[][]{{"浏", "ET 浏览器"}, {"夹", "连接储存"}, {"设", "系统设置"}, {"相", "相机"}, {"册", "相册"}})
                grid.add(tile(ap[0], ap[1], () -> {}));
            body.add(grid);
            body.add(Box.createVerticalStrut(10));
            body.add(lab("▍" + S("m3d"), 14, TXT, true));
            JButton imp = btn(S("stImport"), BLUE);
            imp.addActionListener(e -> importApk());
            body.add(imp);
            body.add(lab(S("stImportHint"), 11, SUB, false));
            JPanel list = vbox();
            body.add(list);
            if (curVm != null) {
                JSONArray inst = curVm.optJSONArray("installed");
                if (inst != null) for (int i = 0; i < inst.length(); i++) {
                    JSONObject a = inst.optJSONObject(i);
                    if (a != null) {
                        JPanel c = card();
                        c.setLayout(new BorderLayout());
                        c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(c(LINE)), new EmptyBorder(8, 12, 8, 12)));
                        c.add(lab(a.optString("label", "") + "  ·  " + a.optString("abi", ""), 13, TXT, false), BorderLayout.CENTER);
                        list.add(Box.createVerticalStrut(4));
                        list.add(c);
                    }
                }
            }
        });
        p.add(scroller(body), BorderLayout.CENTER);
        return p;
    }
    static void importApk() {
        if (curVm == null) { JOptionPane.showMessageDialog(win, "请先启动一台虚拟机"); return; }
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("APK (*.apk)", "apk"));
        if (fc.showOpenDialog(win) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            RomDetect.ApkCompat c2 = RomDetect.checkApk(f.getAbsolutePath(), curVm.optString("bits", "32"));
            if (c2.ok) {
                try {
                    JSONArray inst = curVm.optJSONArray("installed");
                    if (inst == null) inst = new JSONArray();
                    inst.put(new JSONObject().put("label", f.getName()).put("pkg", f.getName()).put("abi", c2.detail));
                    curVm.put("installed", inst);
                    saveVms();
                    refreshers.get("apps").run();
                    JOptionPane.showMessageDialog(win, f.getName() + " ✓ " + c2.detail);
                } catch (Exception ignored) {}
            } else {
                JOptionPane.showMessageDialog(win, c2.reason, "ET虚拟机", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    /* ===== ROM 商店 ===== */
    static JSONArray catalog = new JSONArray();
    static final Map<String, Object[]> storeProg = new HashMap<>(); // id -> {JProgressBar, JLabel}
    static void initCatalog() {
        try {
            if (catalog.length() > 0) return;
            catalog = new JSONArray();
            catalog.put(new JSONObject().put("id", "builtin-44").put("name", "内置 Android 4.4 KitKat").put("ver", "Android 4.4").put("bits", "32").put("sizeMb", 5).put("bundled", true).put("file", "etos-4.4-x86.zip").put("desc", "随包内置 · 已安装好 · 创建虚拟机直接选用，无需下载。32 位。"));
            catalog.put(new JSONObject().put("id", "builtin-70").put("name", "内置 ET-OS 7.0").put("ver", "Android 7.0").put("bits", "32").put("sizeMb", 6).put("bundled", true).put("file", "etos-7.0-x86.zip").put("desc", "随包内置 · 已安装好 · 创建虚拟机直接选用，无需下载。32 位。"));
            catalog.put(new JSONObject().put("id", "x86-44-r1").put("name", "Android 4.4 KitKat (x86)").put("ver", "Android 4.4").put("bits", "32").put("sizeMb", 343).put("url", "https://sourceforge.net/projects/android-x86/files/Release%204.4/android-x86-4.4-r1.iso/download").put("file", "android-x86-4.4-r1.iso").put("desc", "android-x86 官方 4.4 精简镜像，启动轻快。"));
            catalog.put(new JSONObject().put("id", "x86-51-rc1").put("name", "Android 5.1 Lollipop (x86)").put("ver", "Android 5.1").put("bits", "32").put("sizeMb", 358).put("url", "https://sourceforge.net/projects/android-x86/files/Release%205.1/android-x86-5.1-rc1.iso/download").put("file", "android-x86-5.1-rc1.iso").put("desc", "android-x86 官方 5.1 RC 镜像，Material 风格。"));
            catalog.put(new JSONObject().put("id", "x86-60-r3").put("name", "Android 6.0 Marshmallow (x86)").put("ver", "Android 6.0").put("bits", "both").put("sizeMb", 469).put("url", "https://sourceforge.net/projects/android-x86/files/Release%206.0/android-x86-6.0-r3.iso/download").put("file", "android-x86-6.0-r3.iso").put("desc", "android-x86 官方 6.0 镜像，兼容 32/64 位。"));
            catalog.put(new JSONObject().put("id", "x86-71-r2").put("name", "Android 7.1 Nougat (x86_64)").put("ver", "Android 7.1").put("bits", "64").put("sizeMb", 706).put("url", "https://sourceforge.net/projects/android-x86/files/Release%207.1/android-x86-7.1-r2.iso/download").put("file", "android-x86-7.1-r2.iso").put("desc", "android-x86 官方 7.1 镜像，64 位。"));
            catalog.put(new JSONObject().put("id", "x86-81-r2").put("name", "Android 8.1 Oreo (x86_64)").put("ver", "Android 8.1").put("bits", "64").put("sizeMb", 764).put("url", "https://sourceforge.net/projects/android-x86/files/Release%208.1/android-x86_64-8.1-r2.iso/download").put("file", "android-x86_64-8.1-r2.iso").put("desc", "android-x86 官方 8.1 镜像，64 位。"));
            catalog.put(new JSONObject().put("id", "x86-90-r2").put("name", "Android 9.0 Pie (x86_64)").put("ver", "Android 9.0").put("bits", "64").put("sizeMb", 858).put("url", "https://sourceforge.net/projects/android-x86/files/Release%209.0/android-x86_64-9.0-r2.iso/download").put("file", "android-x86_64-9.0-r2.iso").put("desc", "android-x86 官方 9.0 镜像，64 位。"));
            for (int v = 10; v <= 16; v++) {
                catalog.put(new JSONObject().put("id", "a" + v + "-x64").put("name", "Android " + v + " (x86_64)").put("ver", "Android " + v).put("bits", "64").put("sizeMb", 1100 + (v - 10) * 100).put("coming", true).put("file", "android" + v + ".iso").put("desc", "社区 x86_64 发行版大镜像，整理上架中。"));
            }
        } catch (Exception ignored) {}
    }
    static JComponent buildStore() {
        JPanel p = panel(BG);
        p.setLayout(new BorderLayout());
        p.setBorder(new EmptyBorder(18, 22, 18, 22));
        JPanel top = vbox();
        JPanel hh = row();
        JButton back = btn("‹", 0xFF1E2D50); back.setFont(new Font("Segoe UI", Font.BOLD, 20));
        back.addActionListener(e -> show("home"));
        hh.add(back); hh.add(lab(S("romTitle"), 20, TXT, true));
        top.add(hh);
        top.add(lab("Android 4.4 → 16 全系 · 32/64 位 · 2 款随包内置系统", 11, ACCENT, false));
        p.add(top, BorderLayout.NORTH);
        JPanel list = vbox();
        JButton refresh = btn(S("refresh"), 0xFF182442);
        refresh.addActionListener(e -> show("store"));
        top.add(refresh);
        refreshers.put("store", () -> {
            list.removeAll();
            initCatalog();
            int dlN = 0;
            for (int i = 0; i < catalog.length(); i++) {
                try {
                    final JSONObject r = catalog.getJSONObject(i);
                    final String id = r.optString("id", "");
                    final boolean bundled = r.optBoolean("bundled", false);
                    final boolean coming = r.optBoolean("coming", false);
                    File df = new File(romsDir, r.optString("file", "x.iso"));
                    boolean dl = !bundled && !coming && df.exists();
                    if (dl) dlN++;
                    JPanel c = card();
                    c.setLayout(new BorderLayout());
                    c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(c(LINE)), new EmptyBorder(10, 14, 10, 14)));
                    JPanel tt = vbox();
                    tt.add(lab(r.optString("name", ""), 15, TXT, true));
                    tt.add(lab(r.optString("desc", ""), 12, SUB, false));
                    String status = bundled ? "已内置 · 无需下载" : coming ? "镜像整理中" : dl ? "已下载 ✓" : "未下载";
                    int sc = bundled ? OK : coming ? SUB : dl ? OK : 0xFF8A97AD;
                    tt.add(lab(r.optString("ver", "") + " · " + r.optString("bits", "") + " 位 · " + r.optInt("sizeMb", 0) + " MB · " + status, 11, sc, false));
                    c.add(tt, BorderLayout.CENTER);
                    /* 下载中：进度条 */
                    Object[] prog = storeProg.get(id);
                    if (prog != null && !((Boolean) prog[2])) {
                        JPanel pr = vbox();
                        JProgressBar bar = (JProgressBar) prog[0];
                        bar.setPreferredSize(new Dimension(180, 16));
                        bar.setOpaque(false);
                        pr.add(bar);
                        pr.add((JLabel) prog[1]);
                        c.add(pr, BorderLayout.SOUTH);
                    }
                    JPanel acts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
                    acts.setOpaque(false);
                    if (bundled) {
                        JButton use = btn("使用此内置系统", BLUE);
                        use.addActionListener(e -> useBundled(r));
                        acts.add(use);
                    } else if (coming) {
                        JButton w = btn("镜像整理中", 0xFF182442);
                        w.addActionListener(e -> JOptionPane.showMessageDialog(win, "Android 10+ 大镜像整理中，先用 4~9 官方版或内置系统"));
                        acts.add(w);
                    } else if (dl) {
                        JButton use = btn("使用此 ROM", BLUE);
                        use.addActionListener(e -> useDownloaded(r));
                        acts.add(use);
                        JButton re = btn("重新下载", 0xFF182442);
                        re.addActionListener(e -> downloadRom(r));
                        acts.add(re);
                    } else {
                        JButton dlb = btn("下载 ROM", BLUE);
                        dlb.addActionListener(e -> downloadRom(r));
                        acts.add(dlb);
                    }
                    c.add(acts, BorderLayout.EAST);
                    list.add(Box.createVerticalStrut(6));
                    list.add(c);
                } catch (Exception ignored) {}
            }
            list.add(Box.createVerticalStrut(10));
            list.add(lab("已下载 " + dlN + " 款官方镜像 · 内置系统随包可用", 11, ACCENT, false));
        });
        p.add(scroller(list), BorderLayout.CENTER);
        return p;
    }
    static void useBundled(JSONObject r) {
        wizard = new JSONObject();
        wizard.put("name", r.optString("name", "我的安卓机"));
        wizard.put("bits", r.optString("bits", "32"));
        wizard.put("useBuiltin", true);
        wizard.put("builtinVer", r.optString("ver", "Android 4.4"));
        wizard.put("builtinName", r.optString("name", "内置系统"));
        wStep = 4;
        show("wizard");
    }
    static void useDownloaded(JSONObject r) {
        wizard = new JSONObject();
        wizard.put("name", r.optString("name", "我的安卓机"));
        wizard.put("bits", r.optString("bits", "32"));
        wizard.put("useBuiltin", false);
        wizard.put("romPath", new File(romsDir, r.optString("file", "x.iso")).getAbsolutePath());
        wizard.put("romName", r.optString("name", "自定义 ROM"));
        wStep = 4;
        show("wizard");
    }
    static void downloadRom(JSONObject r) {
        final String id = r.optString("id", "");
        final File dst = new File(romsDir, r.optString("file", "x.iso"));
        final JProgressBar bar = new JProgressBar(0, 100);
        final JLabel bl = lab("0%", 11, ACCENT, false);
        storeProg.put(id, new Object[]{bar, bl, Boolean.FALSE});
        refreshers.get("store").run();
        new SwingWorker<Void, Integer>() {
            protected Void doInBackground() {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(r.optString("url", "")).openConnection();
                    c.setInstanceFollowRedirects(true);
                    c.setConnectTimeout(12000);
                    c.setReadTimeout(30000);
                    int code = c.getResponseCode();
                    String loc = c.getHeaderField("Location");
                    int hops = 0;
                    while ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308) && loc != null && hops < 10) {
                        c.disconnect();
                        c = (HttpURLConnection) new URL(new URL(r.optString("url", "")), loc).openConnection();
                        c.setConnectTimeout(12000); c.setReadTimeout(30000);
                        c.setRequestProperty("User-Agent", "Mozilla/5.0 (ETVMPC/" + APP_VER + ")");
                        code = c.getResponseCode(); loc = c.getHeaderField("Location"); hops++;
                    }
                    if (code != 200) throw new RuntimeException("HTTP " + code);
                    long total = c.getContentLengthLong();
                    try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(dst)) {
                        byte[] buf = new byte[65536];
                        long done = 0, st = System.currentTimeMillis(); int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n); done += n;
                            long now = System.currentTimeMillis();
                            if (now - st > 700) {
                                st = now;
                                int pct = total > 0 ? (int) (done * 100 / total) : 0;
                                publish(pct, (int) (done / 1024), (int) (total / 1024));
                            }
                        }
                    }
                } catch (Exception ex) {
                    publish(-1);
                    JOptionPane.showMessageDialog(win, "下载失败：" + ex.getMessage(), "ET虚拟机", JOptionPane.ERROR_MESSAGE);
                }
                return null;
            }
            protected void process(List<Integer> chunks) {
                int n = chunks.size();
                Integer last = chunks.get(n - 1);
                if (last == null || last == -1) { bl.setText("下载失败"); bl.setForeground(c(BAD)); return; }
                Integer kbT = n >= 3 ? chunks.get(n - 1) : 0;
                Integer kbD = n >= 2 ? chunks.get(n - 2) : 0;
                Integer pct = n >= 3 ? chunks.get(n - 3) : last;
                bar.setValue(pct == null ? 0 : pct);
                bl.setText((pct == null ? 0 : pct) + "% · " + (kbD == null ? 0 : kbD) + "KB / " + (kbT == null ? 0 : kbT) + "KB");
                bl.setForeground(c(ACCENT));
            }
            protected void done() {
                storeProg.remove(id);
                JOptionPane.showMessageDialog(win, "「" + r.optString("name", "") + "」下载完成 → " + dst.getAbsolutePath(), "ET虚拟机", JOptionPane.INFORMATION_MESSAGE);
                refreshers.get("store").run();
            }
        }.execute();
    }

    /* ===== 连接储存 ===== */
    static JComponent buildFiles() {
        JPanel p = panel(BG);
        p.setLayout(new BorderLayout());
        p.setBorder(new EmptyBorder(18, 22, 18, 22));
        JPanel top = vbox();
        JPanel hh = row();
        JButton back = btn("‹", 0xFF1E2D50); back.setFont(new Font("Segoe UI", Font.BOLD, 20));
        back.addActionListener(e -> show("desktop"));
        hh.add(back); hh.add(lab(S("filesTitle"), 20, TXT, true));
        top.add(hh);
        p.add(top, BorderLayout.NORTH);
        JPanel body = vbox();
        refreshers.put("files", () -> {
            body.removeAll();
            body.add(lab("▍" + S("filesS") + "（" + sharedDir.getAbsolutePath() + "）", 14, TXT, true));
            JPanel shared = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
            shared.setOpaque(false);
            File[] fs = sharedDir.listFiles();
            if (fs != null) for (File f : fs) {
                JButton b = btn(f.getName(), 0xFF182442);
                b.addActionListener(e -> copyToVm(f));
                shared.add(b);
            }
            if (fs == null || fs.length == 0) shared.add(lab("（空）", 12, SUB, false));
            JButton op = btn(S("openFolder"), 0xFF182442);
            op.addActionListener(e -> { try { Desktop.getDesktop().open(sharedDir); } catch (Exception ex) {} });
            shared.add(op);
            body.add(shared);
            body.add(Box.createVerticalStrut(10));
            body.add(lab("▍" + S("filesV") + "（" + vmDir.getAbsolutePath() + "）", 14, TXT, true));
            JPanel vml = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
            vml.setOpaque(false);
            File[] vfs = vmDir.listFiles();
            if (vfs != null) for (File f : vfs) vml.add(lab(f.getName(), 12, TXT, false));
            if (vfs == null || vfs.length == 0) vml.add(lab("（空）", 12, SUB, false));
            JButton ov = btn(S("openFolder"), 0xFF182442);
            ov.addActionListener(e -> { try { Desktop.getDesktop().open(vmDir); } catch (Exception ex) {} });
            vml.add(ov);
            body.add(vml);
            body.add(lab(S("shareBanner"), 11, ACCENT, false));
        });
        p.add(scroller(body), BorderLayout.CENTER);
        return p;
    }
    static void copyToVm(File f) {
        try { Files.copy(f.toPath(), new File(vmDir, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
            JOptionPane.showMessageDialog(win, f.getName() + " → " + S("filesV") + " ✓"); refreshers.get("files").run();
        } catch (Exception ex) { JOptionPane.showMessageDialog(win, "复制失败：" + ex.getMessage()); }
    }

    /* ===== 关于 ===== */
    static JComponent buildAbout() {
        JPanel p = panel(BG);
        p.setLayout(new BorderLayout());
        p.setBorder(new EmptyBorder(18, 22, 18, 22));
        JPanel top = vbox();
        JPanel hh = row();
        JButton back = btn("‹", 0xFF1E2D50); back.setFont(new Font("Segoe UI", Font.BOLD, 20));
        back.addActionListener(e -> show("home"));
        hh.add(back); hh.add(lab(S("aboutTitle"), 20, TXT, true));
        top.add(hh);
        p.add(top, BorderLayout.NORTH);
        JPanel body = vbox();
        try {
            String hash = selfHash.isEmpty() ? sha256Hex(readSelf()) : selfHash;
            body.add(kv("ET虚拟机", "v" + APP_VER + " · ET协会出品 · © ET"));
            body.add(kv(S("aboutTitle"), "com.et.vm · 纯 Java Swing 原生 · 无 WebView"));
            body.add(kv("防注入", (selfOk ? S("sigOk") : S("sigBad")) + "  SHA-256: " + hash.substring(0, 24) + "…"));
            body.add(kv("授权码", License.licenseKey(hash, deviceId)));
            body.add(kv("内置系统", "ET-OS 7.0 · 32 位 · 已安装好 · 不可删除"));
            body.add(kv(S("checkUpdate"), S("checkUpdate") + " → " + "https://github.com/" + REPO));
        } catch (Exception ex) {
            body.add(lab("校验失败：" + ex, 12, BAD, false));
        }
        p.add(scroller(body), BorderLayout.CENTER);
        return p;
    }
    static JPanel kv(String k, String v) {
        JPanel c = card();
        c.setLayout(new BorderLayout(10, 0));
        c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(c(LINE)), new EmptyBorder(10, 14, 10, 14)));
        c.add(lab(k, 13, ACCENT, true), BorderLayout.WEST);
        c.add(lab(v, 12, TXT, false), BorderLayout.CENTER);
        return c;
    }

    /* ===== 检查更新 ===== */
    static void checkUpdate(final boolean manual) {
        new Thread(() -> {
            try {
                /* 扫描 Releases，找含 Windows 便携包资产的最新版本（电脑版平台） */
                HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/" + REPO + "/releases?per_page=20").openConnection();
                c.setConnectTimeout(8000);
                c.setRequestProperty("User-Agent", "ETVMPC/" + APP_VER);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
                JSONArray rels = new JSONArray(sb.toString());
                String tag = "", dlUrl = "";
                for (int i = 0; i < rels.length(); i++) {
                    JSONObject j = rels.getJSONObject(i);
                    JSONArray assets = j.optJSONArray("assets");
                    if (assets != null) for (int k = 0; k < assets.length(); k++) {
                        JSONObject a = assets.getJSONObject(k);
                        if (a.optString("name", "").contains("ETVM-Windows")) {
                            tag = j.optString("tag_name", "");
                            dlUrl = a.optString("browser_download_url", "");
                            break;
                        }
                    }
                    if (!tag.isEmpty()) break;
                }
                final String fTag = tag, fDl = dlUrl;
                if (fTag.isEmpty() || fDl.isEmpty()) { if (manual) SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(win, S("updateLatest"))); return; }
                if (verNewer(fTag, APP_VER)) {
                    final String last = cfg.getProperty("lastUpdateTag", "");
                    if (manual || !fTag.equals(last)) {
                        cfg.setProperty("lastUpdateTag", fTag);
                        saveCfg();
                        SwingUtilities.invokeLater(() -> {
                            int r2 = JOptionPane.showConfirmDialog(win, SF("updateAsk", fTag), S("updateTitle"), JOptionPane.YES_NO_OPTION);
                            if (r2 == JOptionPane.YES_OPTION) downloadUpdate(fDl);
                        });
                    }
                } else if (manual) SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(win, S("updateLatest")));
            } catch (Exception e) {
                if (manual) SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(win, S("updateErr")));
            }
        }).start();
    }
    static boolean verNewer(String tag, String cur) {
        try {
            String[] ta = tag.replaceAll("[^0-9.]", "").split("\\."), ca = cur.replaceAll("[^0-9.]", "").split("\\.");
            for (int i = 0; i < Math.max(ta.length, ca.length); i++) {
                int a = i < ta.length ? Integer.parseInt(ta[i]) : 0, b = i < ca.length ? Integer.parseInt(ca[i]) : 0;
                if (a > b) return true;
                if (a < b) return false;
            }
            return false;
        } catch (Exception e) { return false; }
    }
    static void downloadUpdate(String url) {
        final File dst = new File(System.getProperty("user.home") + "/Downloads", "ETVM-Windows-x64.zip");
        new SwingWorker<Void, Integer>() {
            protected Void doInBackground() {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setInstanceFollowRedirects(true);
                    c.setConnectTimeout(12000);
                    c.connect();
                    try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(dst)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                } catch (Exception ex) { JOptionPane.showMessageDialog(win, "下载失败：" + ex.getMessage()); }
                return null;
            }
            protected void done() {
                JOptionPane.showMessageDialog(win, S("dlDone") + dst.getAbsolutePath() + "\n解压后运行 ETVM.exe", "ET虚拟机", JOptionPane.INFORMATION_MESSAGE);
                try { Desktop.getDesktop().open(dst.getParentFile()); } catch (Exception ignored) {}
            }
        }.execute();
    }
}
