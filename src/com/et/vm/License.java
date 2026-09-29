package com.et.vm;

import java.security.MessageDigest;

/**
 * ET虚拟机 · 授权与防注入（纯 Java，可 JVM 单测）
 * 授权码 = SHA-256(签名哈希 + AndroidID) 分四段格式化；
 * 签名校验用于阻止重打包/注入不合规软件。
 * ET协会出品 · © ET
 */
public class License {

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return "00000000000000000000000000000000";
        }
    }

    /** 生成设备绑定授权码：ET-XXXX-XXXX-XXXX-XXXX */
    public static String licenseKey(String sigHex, String androidId) {
        String h = sha256Hex(sigHex + "|ET-VM|" + androidId).toUpperCase();
        return "ET-" + h.substring(0, 4) + "-" + h.substring(4, 8) + "-" + h.substring(8, 12) + "-" + h.substring(12, 16);
    }

    /** 签名指纹格式化：AA:BB:... */
    public static String formatFingerprint(byte[] digest) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", digest[i] & 0xff));
        }
        return sb.toString();
    }
}
