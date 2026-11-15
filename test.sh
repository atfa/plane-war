#!/data/data/com.termux/files/usr/bin/bash
# Automated playtest, no tapping needed:
#   1. clean install (a root-created /data/user/0/<pkg> breaks SharedPreferences on this device)
#   2. launch with hooks: autopilot + god + report every 4s + auto-crash after 26s
#      -> the plane must fly, shoot, kill enemies, trigger events and post a score
#   3. relaunch and check the leaderboard is read back from disk
# The loop is vsync driven and the game pauses when a notification steals focus, so the
# test keeps waking the screen and polls the log instead of sleeping a fixed time.
set -uo pipefail
PKG=dev.termux.planewar
APK=$(readlink -f "${1:-plane.apk}")

DIFF=${DIFF:-0}   # 0 简单 1 普通 2 困难
su -c "am force-stop $PKG; pm uninstall $PKG" >/dev/null 2>&1
su -c "pm install -g '$APK'" >/dev/null || { echo "install failed"; exit 1; }
su -c "logcat -c" 2>/dev/null
su -c "input keyevent KEYCODE_WAKEUP" 2>/dev/null
# Android 14+ blocks activity starts from the shell unless the launcher is on top
su -c "input keyevent KEYCODE_HOME" 2>/dev/null
sleep 2

LAUNCH=$(su -c "am start -n $PKG/.MainActivity --ez autostart true --ez autopilot true \
   --ez god true --ei report 4 --ei autodie 26 --ei diff $DIFF" 2>&1)
echo "launch:  $LAUNCH"

for i in $(seq 1 40); do
  sleep 2
  su -c "input keyevent KEYCODE_WAKEUP" >/dev/null 2>&1
  if su -c "logcat -d -s planewar:V" | grep -q 'gameover score=[1-9]'; then break; fi
done
su -c "am force-stop $PKG"
su -c "input keyevent KEYCODE_HOME" 2>/dev/null
su -c "am start -n $PKG/.MainActivity" >/dev/null
sleep 3

echo "--- game log ---"
su -c "logcat -d -s planewar:V" | tail -14
LOG=$(su -c "logcat -d -s planewar:V")
echo "--- checks ---"
OVER=$(echo "$LOG" | grep -oE 'gameover score=[1-9][0-9]*' | tail -1)
EVENT=$(echo "$LOG" | grep -c 'event ')
AUDIO=$(echo "$LOG" | grep -c 'audio ready')
LOADED=$(echo "$LOG" | grep -o 'loaded best=[0-9]*' | tail -1)
STARTED=$(echo "$LOG" | grep -c 'I planewar: start')
echo "difficulty: $DIFF (0 简单 1 普通 2 困难)"
echo "run:     $OVER"
[ "$STARTED" -ge 1 ] || echo "WARN: the game never started - check the launch: line"
echo "events:  $EVENT"
echo "audio:   $AUDIO (1 = all sfx loaded)"
echo "persist: ${LOADED:-none}"
if [ -n "$OVER" ] && [ "$EVENT" -ge 1 ] && [ "$AUDIO" -ge 1 ] \
   && echo "$LOADED" | grep -qE 'loaded best=[1-9]'; then
  echo "PASS: autopilot flew, shot, killed, triggered events; score persisted and read back"
  exit 0
fi
echo "FAIL"
exit 1
