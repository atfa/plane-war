#!/data/data/com.termux/files/usr/bin/bash
# On-device Android build: aapt2 + javac + d8 + apksigner (all native aarch64, Termux pkgs)
set -euo pipefail

SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
PLATFORM="android-33"
JAR="$SDK_DIR/platforms/$PLATFORM/android.jar"
MIN_API=24
APK=plane.apk
[ -f "$JAR" ] || { echo "missing $JAR - run ../apkdemo/get-sdk.sh"; exit 1; }

OUT=out
rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/apk"

echo "==> 1/6 aapt2 compile (resources)"
aapt2 compile --dir res -o "$OUT/res.zip"

echo "==> 2/6 aapt2 link (binary manifest + R.java)"
aapt2 link -o "$OUT/apk/base.apk" -I "$JAR" \
    --manifest AndroidManifest.xml "$OUT/res.zip" \
    --java "$OUT/gen" --auto-add-overlay --min-sdk-version "$MIN_API" --target-sdk-version 33

echo "==> 3/6 javac"
find src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$JAR" -classpath "$JAR" \
    -d "$OUT/classes" "@$OUT/sources.txt"

echo "==> 4/6 d8 (.class -> classes.dex)"
# Termux's d8 is R8 3.3 (Android 13 era) and crashes on anonymous classes emitted by
# JDK 21 (NestMembers attribute) -> prefer a modern R8 jar from Google's Maven repo.
# Unified location: the skill caches it in vendor/ and hardlinks it to ~/android-tools/r8.jar.
R8="${R8_JAR:-$HOME/android-tools/r8.jar}"
if [ -f "$R8" ]; then D8=(java -cp "$R8" com.android.tools.r8.D8); else D8=(d8); fi
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"${D8[@]}" --release --lib "$JAR" --min-api "$MIN_API" --output "$OUT/dex" $(cat "$OUT/classes.txt")

echo "==> 5/6 package"
cp "$OUT/apk/base.apk" "$OUT/apk/unaligned.apk"
(cd "$OUT/dex" && zip -q -X -j "$OLDPWD/$OUT/apk/unaligned.apk" classes.dex)

echo "==> 6/6 sign (v1+v2+v3)"
KS="$HOME/.android/debug.keystore"
mkdir -p "$(dirname "$KS")"
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -alias androiddebugkey \
    -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --out "$APK" "$OUT/apk/unaligned.apk"

echo "==> ok: $(pwd)/$APK ($(stat -c%s "$APK") bytes)"
