package com.et.vm;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * ET虚拟机 · 纯 Java 镜像/APK 实时检测引擎（无 Android 依赖，可 JVM 单测）
 * ET协会出品 · © ET
 */
public class RomDetect {

    public static class Result {
        public boolean ok;
        public String type;
        public String bits;        // 32 | 64 | both | unknown
        public String bitsLabel;
        public String note;
        public long size;
        public int entries;
        public boolean apk;
    }

    private static final int MAX_ENTRIES = 5000;

    /* ---------- ZIP 中央目录解析（流式，不整读大文件） ---------- */
    public static List<String> parseZipEntries(RandomAccessFile raf, long fileLen) throws Exception {
        List<String> entries = new ArrayList<>();
        long eocdStart = Math.max(0, fileLen - 70000);
        long eocd = -1;
        byte[] tail = new byte[(int) (fileLen - eocdStart)];
        raf.seek(eocdStart);
        int read = raf.read(tail);
        for (int i = Math.max(0, read - 22); i >= 0; i--) {
            if (tail[i] == 0x50 && tail[i + 1] == 0x4b && tail[i + 2] == 0x05 && tail[i + 3] == 0x06) { eocd = eocdStart + i; break; }
        }
        if (eocd < 0) return entries;
        byte[] e = new byte[22];
        raf.seek(eocd);
        if (raf.read(e) < 22) return entries;
        int total = le16(e, 10);
        long cdOff = le32(e, 16) & 0xffffffffL;
        if (cdOff + 46 > fileLen) return entries;
        raf.seek(cdOff);
        byte[] hdr = new byte[46];
        for (int i = 0; i < total && i < MAX_ENTRIES; i++) {
            if (raf.read(hdr) < 46) break;
            if (!(hdr[0] == 0x50 && hdr[1] == 0x4b && hdr[2] == 0x01 && hdr[3] == 0x02)) break;
            int nlen = le16(hdr, 28);
            int elen = le16(hdr, 30);
            int clen = le16(hdr, 32);
            byte[] name = new byte[nlen];
            if (raf.read(name) < nlen) break;
            entries.add(new String(name, "UTF-8"));
            raf.skipBytes(elen + clen);
        }
        return entries;
    }

    private static int le16(byte[] b, int o) { return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8); }
    private static int le32(byte[] b, int o) { return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8) | ((b[o + 2] & 0xff) << 16) | ((b[o + 3] & 0xff) << 24); }

    /* ---------- 读取文件头（流式） ---------- */
    private static byte[] readHead(RandomAccessFile raf, int len) throws Exception {
        raf.seek(0);
        byte[] buf = new byte[len];
        int n = raf.read(buf);
        byte[] out = new byte[Math.max(0, n)];
        System.arraycopy(buf, 0, out, 0, out.length);
        return out;
    }

    /* ---------- 主检测 ---------- */
    public static Result detect(String path, String name, long size) {
        Result r = new Result();
        r.ok = false;
        r.bits = "unknown";
        r.bitsLabel = "未知（未发现明确 ABI 标记）";
        r.size = size;
        r.type = "未知系统包";
        r.note = "该文件不是有效的 ZIP/IMG 镜像包，无法识别为 Android 系统。";
        try {
            String lower = (name == null ? "" : name).toLowerCase();
            RandomAccessFile raf = new RandomAccessFile(new File(path), "r");
            long len = raf.length();
            byte[] head = readHead(raf, 4096);
            boolean isPE = head.length > 2 && head[0] == 0x4D && head[1] == 0x5A;
            if (isPE) {
                r.type = "Windows 系统镜像（PE 格式）";
                r.bits = "unknown";
                r.bitsLabel = "—";
                r.note = "检测到 Windows 引导头 (MZ/PE)。ET虚拟机仅支持 Android 系统，已拒绝该镜像。";
                raf.close();
                return r;
            }
            boolean iso9660 = head.length > 0x8000 + 6 && head[0x8000] == 0x01 && head[0x8000 + 1] == 0x43 && head[0x8000 + 2] == 0x44;
            List<String> entries = parseZipEntries(raf, len);
            r.entries = entries.size();
            raf.close();
            boolean hasWin = contains(entries, "windows") || lower.contains("windows")
                    || (contains(entries, "efi/boot") && contains(entries, "sources/install.wim"));
            if (hasWin || (iso9660 && lower.contains("windows"))) {
                r.type = "Windows 系统镜像";
                r.bits = "unknown";
                r.bitsLabel = "—";
                r.note = "检测到 Windows 安装文件（install.wim/EFI）。仅支持 Android 系统，已拒绝。";
                return r;
            }
            boolean isApk = contains(entries, "AndroidManifest.xml") && contains(entries, "classes.dex");
            boolean isAndroid = iso9660 || containsAny(entries, new String[]{
                            "(^|/)system.img", "(^|/)system/build.prop", "build.prop", "(^|/)boot.img",
                            "(^|/)vendor.img", "META-INF/com/google/android", "payload.bin",
                            "(^|/)system/framework", "(^|/)vendor/build.prop", "(^|/)apex/", "update.zip", "ota",
                            "(^|/)product/build.prop"})
                    || lower.contains("android") || lower.contains("rom") || lower.contains("miui")
                    || lower.contains("lineage") || lower.contains("pixel") || lower.contains("coloros")
                    || lower.contains("oxygen") || lower.contains("emui") || lower.contains("harmony");
            boolean has64 = contains(entries, "lib/arm64-v8a/") || contains(entries, "lib/x86_64/")
                    || containsAny(entries, new String[]{"arm64", "aarch64"}) || lower.contains("arm64") || lower.contains("_64");
            boolean has32 = contains(entries, "lib/armeabi-v7a/") || contains(entries, "lib/arm/")
                    || contains(entries, "lib/x86/") || containsAny(entries, new String[]{"armeabi", "armv7", "i686"})
                    || lower.contains("armv7") || lower.contains("-32");
            String bits;
            if (has64 && has32) bits = "both";
            else if (has64) bits = "64";
            else if (has32) bits = "32";
            else bits = "unknown";
            r.bits = bits;
            r.bitsLabel = bitsLabel(bits);
            if (!isAndroid && !isApk) {
                r.type = "未知系统包";
                r.note = entries.isEmpty()
                        ? "该文件不是有效的 ZIP/IMG 镜像包，无法识别为 Android 系统。"
                        : "未发现 Android 系统镜像标记（system.img / build.prop / boot.img 等）。请确认上传的是 Android ROM 或刷机包。";
                return r;
            }
            r.ok = true;
            r.type = isApk ? "Android 应用包 (APK)" : "Android 系统镜像 / ROM";
            r.apk = isApk;
            StringBuilder note = new StringBuilder("检测到 Android 系统镜像。");
            if (bits.equals("64")) note.append("镜像包含 64 位 ABI（arm64-v8a / x86_64）。");
            else if (bits.equals("32")) note.append("镜像仅包含 32 位 ABI（armeabi-v7a / x86）。");
            else if (bits.equals("both")) note.append("镜像同时包含 32/64 位 ABI，双向兼容。");
            else note.append("未发现明确 ABI 目录，按通用处理。");
            if (!entries.isEmpty()) note.append(" · 扫描到 ").append(entries.size()).append(" 个文件条目。");
            r.note = note.toString();
            return r;
        } catch (Exception e) {
            r.type = "检测异常";
            r.note = "检测异常：" + e.getMessage();
            return r;
        }
    }

    /* ---------- APK 兼容性检测（纯 Java） ---------- */
    public static class ApkCompat {
        public boolean ok;
        public String bits;   // 32 | 64 | both
        public String detail;
        public String reason;
    }

    public static ApkCompat checkApk(String path, String vmBits) {
        ApkCompat c = new ApkCompat();
        try {
            RandomAccessFile raf = new RandomAccessFile(new File(path), "r");
            List<String> entries = parseZipEntries(raf, raf.length());
            raf.close();
            boolean has64 = contains(entries, "lib/arm64-v8a/") || contains(entries, "lib/x86_64/");
            boolean has32 = contains(entries, "lib/armeabi-v7a/") || contains(entries, "lib/arm/") || contains(entries, "lib/x86/");
            if (!has64 && !has32) {
                c.ok = true; c.bits = "both"; c.detail = "无原生库（纯 Java 应用）";
                c.reason = "该应用不含原生 .so 库，任意位数虚拟机均可运行，兼容。";
                return c;
            }
            if (has64 && has32) {
                c.ok = true; c.bits = "both"; c.detail = "arm64 + armv7 混合";
                c.reason = "应用同时包含 64 位与 32 位原生库，与 " + vmBits + " 位虚拟机匹配，兼容。";
                return c;
            }
            if (has64) {
                c.bits = "64";
                if (vmBits.equals("64")) {
                    c.ok = true; c.detail = "仅 arm64-v8a / x86_64";
                    c.reason = "应用包含 64 位原生库，与 " + vmBits + " 位虚拟机匹配，兼容。";
                } else {
                    c.ok = false; c.detail = "仅 arm64-v8a / x86_64";
                    c.reason = "应用仅包含 64 位原生库，与当前 32 位虚拟机不匹配。请创建 64 位虚拟机后导入。";
                }
                return c;
            }
            c.bits = "32";
            if (vmBits.equals("32")) {
                c.ok = true; c.detail = "仅 armeabi-v7a / x86";
                c.reason = "应用包含 32 位原生库，与 " + vmBits + " 位虚拟机匹配，兼容。";
            } else {
                c.ok = false; c.detail = "仅 armeabi-v7a / x86";
                c.reason = "应用仅包含 32 位原生库（armeabi-v7a/x86），与当前 64 位虚拟机不匹配。可切换到 32 位虚拟机，或选择 64 位版本应用。";
            }
            return c;
        } catch (Exception e) {
            c.ok = false; c.detail = "读取失败"; c.reason = "读取应用包失败：" + e.getMessage();
            return c;
        }
    }

    /* ---------- 内置系统包读取 ---------- */
    public static String readMeta(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    public static String bitsLabel(String bits) {
        if (bits.equals("64")) return "64 位 (arm64 / x86_64)";
        if (bits.equals("32")) return "32 位 (armv7 / x86)";
        if (bits.equals("both")) return "混合（32+64 位兼容）";
        return "未知（未发现明确 ABI 标记）";
    }

    private static boolean contains(List<String> list, String sub) {
        for (String s : list) { if (s.toLowerCase().contains(sub.toLowerCase())) return true; }
        return false;
    }
    private static boolean containsAny(List<String> list, String[] subs) {
        for (String sub : subs) if (contains(list, sub)) return true;
        return false;
    }
}
