#!/data/data/com.termux/files/usr/bin/bash
# JVM unit tests for the pure-logic game classes (no Android, no device, ~2s).
# Only files that do not import android.* may be listed here - keep it that way.
set -uo pipefail
cd "$(dirname "$0")"

OUT=out/test
rm -rf "$OUT"
mkdir -p "$OUT"

SRC="src/dev/termux/planewar/Tuning.java
src/dev/termux/planewar/Super.java
src/dev/termux/planewar/Boss.java
src/dev/termux/planewar/Weapon.java
src/dev/termux/planewar/Wave.java
test/TestLogic.java
test/TestBoss.java"

# the pure classes must stay free of Android: that is what makes this suite runnable without a phone
ANDROID=$(grep -l "import android" src/dev/termux/planewar/Tuning.java src/dev/termux/planewar/Super.java \
  src/dev/termux/planewar/Boss.java src/dev/termux/planewar/Weapon.java src/dev/termux/planewar/Wave.java 2>/dev/null)
if [ -n "$ANDROID" ]; then
  echo "FAIL: pure-logic class imports Android, the JVM suite cannot cover it: $ANDROID"
  exit 1
fi

out=$(javac -nowarn -d "$OUT" $SRC 2>&1)
rc=$?
echo "$out" | grep -v '^Note:' || true
if [ $rc -ne 0 ] || [ ! -f "$OUT/dev/termux/planewar/TestLogic.class" ]; then
  echo "FAIL: javac (rc=$rc)"
  exit 1
fi

rc=0
for t in TestLogic TestBoss; do
  if java -cp "$OUT" dev.termux.planewar.$t; then :; else rc=1; fi
done
if [ $rc -eq 0 ]; then
  echo "PASS: JVM logic tests"
  exit 0
fi
echo "FAIL: JVM logic tests"
exit 1
