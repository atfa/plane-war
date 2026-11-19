#!/data/data/com.termux/files/usr/bin/bash
# JVM unit tests for the pure-logic game classes (no Android, no device, ~2s).
# Only files that do not import android.* may be listed here - keep it that way.
set -uo pipefail
cd "$(dirname "$0")"

OUT=out/test
rm -rf "$OUT"
mkdir -p "$OUT"

SRC="src/dev/xduo/plane/Tuning.java
src/dev/xduo/plane/Super.java
src/dev/xduo/plane/Boss.java
src/dev/xduo/plane/Weapon.java
src/dev/xduo/plane/Wave.java
src/dev/xduo/plane/UiSpec.java
src/dev/xduo/plane/ShipSpec.java
test/TestLogic.java
test/TestBoss.java
test/TestUi.java
test/TestShips.java"

# the pure classes must stay free of Android: that is what makes this suite runnable without a phone
ANDROID=$(grep -l "import android" src/dev/xduo/plane/Tuning.java src/dev/xduo/plane/Super.java \
  src/dev/xduo/plane/Boss.java src/dev/xduo/plane/Weapon.java src/dev/xduo/plane/Wave.java \
  src/dev/xduo/plane/UiSpec.java src/dev/xduo/plane/ShipSpec.java 2>/dev/null)
if [ -n "$ANDROID" ]; then
  echo "FAIL: pure-logic class imports Android, the JVM suite cannot cover it: $ANDROID"
  exit 1
fi

out=$(javac -nowarn -d "$OUT" $SRC 2>&1)
rc=$?
echo "$out" | grep -v '^Note:' || true
if [ $rc -ne 0 ] || [ ! -f "$OUT/dev/xduo/plane/TestLogic.class" ]; then
  echo "FAIL: javac (rc=$rc)"
  exit 1
fi

# Ships.java is the Android side of the art contract: it cannot be compiled here, but it can be
# grepped, so assert it names every silhouette ShipSpec declares. A silhouette Ships never draws is
# an aircraft that renders as the fallback shape, which is exactly the "everything looks the same"
# regression this suite exists to catch.
SHIPS=src/dev/xduo/plane/Ships.java
if [ -f "$SHIPS" ]; then
  missing=""
  for s in INTERCEPTOR DIAMOND ARROW HEXPOD PENTAGON BROADSIDE LOBES BARGE NEEDLE SAUCER \
           FORTRESS STORM WRAITH; do
    grep -q "SIL_$s" "$SHIPS" || missing="$missing SIL_$s"
  done
  if [ -n "$missing" ]; then
    echo "FAIL: Ships.java never draws:$missing - every ShipSpec silhouette needs a case"
    exit 1
  fi
fi

rc=0
for t in TestLogic TestBoss TestUi TestShips; do
  if java -cp "$OUT" dev.xduo.plane.$t; then :; else rc=1; fi
done
if [ $rc -eq 0 ]; then
  echo "PASS: JVM logic tests"
  exit 0
fi
echo "FAIL: JVM logic tests"
exit 1
