#!/data/data/com.termux/files/usr/bin/bash
# Screenshot the game and render it as ASCII, so a UI change can be checked by eye and by pixel.
#
#   ./shot.sh                                   capture what is on screen now
#   ./shot.sh --ez autostart true --ei powerall 8 ...   relaunch with test hooks, wait, capture
#
# Env: WAIT=<s> seconds to let the game settle (default 9), COLS=<n> ASCII width (default 78),
#      OUT=<png> output path (default $TMPDIR/screen.png), PIX=1 to also run check.py.
set -uo pipefail
PKG=dev.termux.planewar
OUT=${OUT:-$TMPDIR/screen.png}
COLS=${COLS:-78}
WAIT=${WAIT:-9}

if [ $# -gt 0 ]; then
  su -c "am force-stop $PKG" >/dev/null 2>&1
  su -c "input keyevent KEYCODE_WAKEUP" >/dev/null 2>&1
  su -c "wm dismiss-keyguard" >/dev/null 2>&1
  # Android 14+ blocks activity starts from the shell unless the launcher is on top
  su -c "input keyevent KEYCODE_HOME" >/dev/null 2>&1
  sleep 2
  su -c "am start -n $PKG/.MainActivity $*" >/dev/null
  sleep "$WAIT"
fi

su -c "screencap -p /data/local/tmp/screen.png && chmod 644 /data/local/tmp/screen.png" >/dev/null \
  || { echo "screencap failed (needs root)"; exit 1; }
if ! cp /data/local/tmp/screen.png "$OUT" 2>/dev/null; then
  su -c "cat /data/local/tmp/screen.png" > "$OUT"
fi
echo "shot: $OUT  ($(wc -c < "$OUT") bytes)"
python view.py "$OUT" "$COLS"
[ "${PIX:-0}" = 1 ] && python check.py "$OUT"
