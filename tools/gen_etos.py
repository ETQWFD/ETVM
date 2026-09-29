#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# 生成 ET-OS 7.0 内置精简系统镜像包（32 位，≤100MB），供 ET虚拟机原生版打包使用
import zlib, struct, json, zipfile, os

OUT = '/home/user/Doubao/chats/38444626514490626/ETVM/assets/etos/etos-7.0-x86.zip'

# ---------- 纯 Python PNG 生成（渐变壁纸 720x1280） ----------
def png_chunk(tag, data):
    c = struct.pack('>I', len(data)) + tag + data
    c += struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    return c

def make_png(w, h):
    def px(x, y):
        t = y / h
        r = int(10 + 30 * t)
        g = int(20 + 70 * t)
        b = int(60 + 160 * t)
        if (x // 48 + y // 48) % 2 == 0:
            r = min(255, r + 6); g = min(255, g + 10); b = min(255, b + 14)
        return bytes([r, g, b])
    raw = b''.join(b'\x00' + b''.join(px(x, y) for x in range(w)) for y in range(h))
    ihdr = struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)
    return (b'\x89PNG\r\n\x1a\n' + png_chunk(b'IHDR', ihdr) +
            png_chunk(b'IDAT', zlib.compress(raw, 6)) + png_chunk(b'IEND', b''))

wallpaper = make_png(720, 1280)

# ---------- 系统内容 ----------
def make_img(size_mb, marker):
    # 精简镜像占位（可扩展存储分区），内容为系统标记文本
    head = marker if isinstance(marker, bytes) else marker.encode('utf-8')
    fill = b'\x00' * (size_mb * 1024 * 1024 - len(head))
    return head + fill

build_prop = """# ET-OS 7.0 内置精简系统
ro.build.version.release=7.0
ro.build.version.sdk=24
ro.build.version.incremental=ET-OS-7.0.20260929
ro.product.model=ET-OS 7.0 内置虚拟机
ro.product.brand=ET
ro.product.device=etos7
ro.product.cpu.abi=x86
ro.product.cpu.abilist=x86,armeabi-v7a
ro.build.fingerprint=ET/etos7/etos7:7.0/NRD90M/20260929:user/release-keys
ro.vm.bits=32
ro.vm.bundled=1
persist.sys.etvm.shared=1
"""

meta = {
    "name": "ET-OS 7.0 精简系统（内置）",
    "ver": "Android 7.0",
    "bits": "32",
    "bundled": True,
    "sizeMb": 6,
    "wallpaper": "wallpaper.png",
    "apps": [
        {"label": "系统设置", "key": "set"},
        {"label": "ET 浏览器", "key": "browser"},
        {"label": "连接储存", "key": "files"},
        {"label": "相机", "key": "camera"},
        {"label": "相册", "key": "gallery"}
    ]
}

with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as z:
    z.writestr('build.prop', build_prop)
    z.writestr('system/build.prop', build_prop)
    z.writestr('vendor/build.prop', 'ro.vendor.etvm=1\n')
    z.writestr('boot.img', make_img(1, b'ANDROID!ET-OS7-BOOT'))
    z.writestr('system/img/system.img', make_img(3, b'ET-OS7-SYSTEM'))
    z.writestr('lib/x86/libandroid_runtime.so', b'\x7fELF stub 32-bit x86 ET-OS\n')
    z.writestr('lib/armeabi-v7a/libetvm.so', b'\x7fELF stub armeabi-v7a\n')
    z.writestr('meta.json', json.dumps(meta, ensure_ascii=False))
    z.writestr('wallpaper.png', wallpaper)

sz = os.path.getsize(OUT)
print('ET-OS 7.0 bundle generated:', OUT)
print('size: %.2f MB (limit 100MB)' % (sz / 1048576.0))
