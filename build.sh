#!/bin/bash
set -e
SDK=/home/user/android-sdk
BT=$SDK/build-tools/34.0.0
PLATFORM=$SDK/platforms/android-34/android.jar
JAVAC=/home/user/jdk-17.0.20.1+1/bin/javac
KEYTOOL=/home/user/jdk-17.0.20.1+1/bin/keytool
ROOT=/home/user/Doubao/chats/38444626514490626/ETVM
OUT=$ROOT/build
APPNAME="ET虚拟机-v3.1.1.apk"
KS=$ROOT/keystore/et.keystore

echo "== 1/7 编译资源 =="
rm -rf $OUT && mkdir -p $OUT/gen $OUT/obj
$BT/aapt2 compile --dir $ROOT/res -o $OUT/res.zip

echo "== 2/7 链接资源与清单 =="
$BT/aapt2 link -o $OUT/unsigned.apk -I $PLATFORM \
  --manifest $ROOT/AndroidManifest.xml -A $ROOT/assets \
  --java $OUT/gen --min-sdk-version 24 --target-sdk-version 34 \
  $OUT/res.zip

echo "== 3/7 编译 Java =="
$JAVAC -source 8 -target 8 -classpath $PLATFORM -d $OUT/obj \
  $(find $ROOT/src $OUT/gen -name '*.java')

echo "== 4/7 生成 dex =="
$BT/d8 --release --lib $PLATFORM --output $OUT \
  $(find $OUT/obj -name '*.class')

echo "== 5/7 打包 dex 入 APK =="
/opt/python3.12/bin/python3 - << EOF
import zipfile
z = zipfile.ZipFile('$OUT/unsigned.apk', 'a', zipfile.ZIP_DEFLATED)
z.write('$OUT/classes.dex', 'classes.dex')
z.close()
print('classes.dex added')
EOF

echo "== 6/7 对齐 =="
$BT/zipalign -f 4 $OUT/unsigned.apk $OUT/aligned.apk

echo "== 7/7 签名 =="
if [ ! -f $KS ]; then
  $KEYTOOL -genkeypair -keystore $KS -alias et -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass etvm123 -keypass etvm123 \
    -dname "CN=ET, OU=ET协会, O=ET, L=Meizhou, ST=Guangdong, C=CN"
fi
$BT/apksigner sign --ks $KS --ks-pass pass:etvm123 --key-pass pass:etvm123 \
  --out "$ROOT/$APPNAME" $OUT/aligned.apk

echo "== 验证 =="
$BT/apksigner verify --print-certs "$ROOT/$APPNAME"
$BT/aapt2 dump badging "$ROOT/$APPNAME" | head -20
ls -la "$ROOT/$APPNAME"
echo "DONE"
