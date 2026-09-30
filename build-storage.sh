#!/bin/bash
set -e
SDK=/home/user/android-sdk
BT=$SDK/build-tools/34.0.0
PLATFORM=$SDK/platforms/android-34/android.jar
JAVAC=/home/user/jdk-17.0.20.1+1/bin/javac
ROOT=/home/user/Doubao/chats/38444626514490626/ETVM
OUT=$ROOT/build-storage
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/obj"
echo "== 资源 =="
"$BT/aapt2" compile --dir "$ROOT/src-storage/res" -o "$OUT/res.zip"
echo "== 链接 =="
"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$PLATFORM" \
  --manifest "$ROOT/src-storage/AndroidManifest.xml" \
  --java "$OUT/gen" --min-sdk-version 21 --target-sdk-version 34 \
  --auto-add-overlay "$OUT/res.zip"
echo "== 编译 =="
"$JAVAC" -source 8 -target 8 -classpath "$PLATFORM" -d "$OUT/obj" \
  $(find "$ROOT/src-storage" "$OUT/gen" -name '*.java')
echo "== dex =="
"$BT/d8" --release --lib "$PLATFORM" --output "$OUT" $(find "$OUT/obj" -name '*.class')
/opt/python3.12/bin/python3 - "$OUT" << 'PYEOF'
import sys, zipfile
out = sys.argv[1]
z = zipfile.ZipFile(out + '/unsigned.apk', 'a', zipfile.ZIP_DEFLATED)
z.write(out + '/classes.dex', 'classes.dex')
z.close()
PYEOF
echo "== 对齐签名 =="
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$ROOT/keystore/et.keystore" --ks-pass pass:etvm123 --key-pass pass:etvm123 \
  --out "$ROOT/assets/storage/com.et.storage.apk" "$OUT/aligned.apk"
echo "== DONE =="
ls -la "$ROOT/assets/storage/com.et.storage.apk"
