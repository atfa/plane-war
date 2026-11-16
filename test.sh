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

# The loop is vsync driven: when the screen times out (this phone: 30s) the game stops
# updating and KEYCODE_WAKEUP alone does not always bring it back, so the test pins the
# screen on for its duration and restores the setting on the way out.
STAY=$(su -c "settings get system stay_on_while_plugged_in" 2>/dev/null | tr -d '\r')
[ "$STAY" = "null" ] && STAY=0
restore() { su -c "settings put system stay_on_while_plugged_in $STAY" >/dev/null 2>&1; }
trap restore EXIT
su -c "settings put system stay_on_while_plugged_in 7" >/dev/null 2>&1

su -c "am force-stop $PKG; pm uninstall $PKG" >/dev/null 2>&1
su -c "pm install -g '$APK'" >/dev/null || { echo "install failed"; exit 1; }
su -c "logcat -c" 2>/dev/null
su -c "input keyevent KEYCODE_WAKEUP" 2>/dev/null
su -c "wm dismiss-keyguard" 2>/dev/null
# Android 14+ blocks activity starts from the shell unless the launcher is on top
su -c "input keyevent KEYCODE_HOME" 2>/dev/null
sleep 2

LAUNCH=$(su -c "am start -n $PKG/.MainActivity --ez autostart true --ez autopilot true \
   --ez god true --ei report 4 --ei autodie 26 --ei diff $DIFF --ei super 1 --ei bomb 1" 2>&1)
echo "launch:  $LAUNCH"

for i in $(seq 1 40); do
  sleep 2
  su -c "input keyevent KEYCODE_WAKEUP" >/dev/null 2>&1
  if su -c "logcat -d -s planewar:V" | grep -q 'gameover score=[1-9]'; then break; fi
done
# Scores.submit() uses SharedPreferences.apply(), which writes on a background thread:
# force-stopping the app immediately after the crash can drop the leaderboard write.
sleep 3
su -c "am force-stop $PKG"
su -c "input keyevent KEYCODE_HOME" 2>/dev/null
su -c "am start -n $PKG/.MainActivity" >/dev/null
sleep 3
if ! su -c "logcat -d -s planewar:V" | grep -qE 'loaded best=[1-9]'; then
  echo "retry: the board read back empty, relaunching once"
  su -c "am force-stop $PKG"
  sleep 2
  su -c "am start -n $PKG/.MainActivity" >/dev/null
  sleep 3
fi

echo "--- game log ---"
su -c "logcat -d -s planewar:V" | tail -14
LOG=$(su -c "logcat -d -s planewar:V")
echo "--- checks ---"
OVER=$(echo "$LOG" | grep -oE 'gameover score=[1-9][0-9]*' | tail -1)
EVENT=$(echo "$LOG" | grep -c 'event ')
AUDIO=$(echo "$LOG" | grep -c 'audio ready')
LOADED=$(echo "$LOG" | grep -o 'loaded best=[0-9]*' | tail -1)
STARTED=$(echo "$LOG" | grep -c 'I planewar: start')
SUPER=$(echo "$LOG" | grep -c 'super name=')
BOMB=$(echo "$LOG" | grep -c 'bomb n=')
STATS=$(echo "$LOG" | grep -c 'stats .*energy=[0-9]* bomb=[0-9]* graze=[0-9]* fever=[0-9]* boss=[0-9]* fps=[0-9]')
FPS=$(echo "$LOG" | grep -oE 'fps=[0-9.]+' | tail -1 | cut -d= -f2)
echo "difficulty: $DIFF (0 简单 1 普通 2 困难)"
echo "run:     $OVER"
[ "$STARTED" -ge 1 ] || echo "WARN: the game never started - check the launch: line"
echo "events:  $EVENT"
echo "super:   $SUPER (energy super blast fired)"
echo "bomb:    $BOMB (player bomb fired)"
echo "stats:   $STATS lines with energy/bomb/graze/fever/boss/fps"
echo "fps:     ${FPS:-none} (rolling average, must be >= 45)"
echo "audio:   $AUDIO (1 = all sfx loaded)"
echo "persist: ${LOADED:-none}"
FPS_OK=$(awk -v f="${FPS:-0}" 'BEGIN { print (f >= 45) ? 1 : 0 }')
if [ -n "$OVER" ] && [ "$EVENT" -ge 1 ] && [ "$AUDIO" -ge 1 ] && [ "$SUPER" -ge 1 ] \
   && [ "$BOMB" -ge 1 ] && [ "$STATS" -ge 1 ] && [ "$FPS_OK" = "1" ] \
   && echo "$LOADED" | grep -qE 'loaded best=[1-9]'; then
  echo "PASS: autopilot flew, shot, killed, triggered events, fired the super blast and a bomb,"
  echo "      kept fps >= 45; score persisted and read back"
  exit 0
fi
echo "FAIL"
exit 1
