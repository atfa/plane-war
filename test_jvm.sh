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
test/TestLogic.java"

out=$(javac -nowarn -d "$OUT" $SRC 2>&1)
rc=$?
echo "$out" | grep -v '^Note:' || true
if [ $rc -ne 0 ] || [ ! -f "$OUT/dev/termux/planewar/TestLogic.class" ]; then
  echo "FAIL: javac (rc=$rc)"
  exit 1
fi

if java -cp "$OUT" dev.termux.planewar.TestLogic; then
  echo "PASS: JVM logic tests"
  exit 0
fi
echo "FAIL: JVM logic tests"
exit 1
