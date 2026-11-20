#!/usr/bin/env bash
# EMULATOR ONLY. Requires root and uninstalls fixture data. Never run on a user's device.
set -euo pipefail
cd "$(dirname "$0")/.."
apk=$1
pkg=app.spool.music.dev
base=com.music.bitchord
# Use the actual prior candidate, whose manifest has no LauncherSpool.
if [ "$#" -ge 2 ]; then
  # Emulator fixture only: begin with an install of the actual older APK.
  adb uninstall "$pkg"
  adb install "$2"
  adb shell am start -W -n "$pkg/$base.LauncherDefault" > /tmp/spool-fixture-start.txt
  cat /tmp/spool-fixture-start.txt
  if grep -qE 'Error:|Exception|Status: timeout' /tmp/spool-fixture-start.txt; then exit 1; fi
  adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$pkg" > /tmp/spool-old-query.txt
  cat /tmp/spool-old-query.txt
  grep -q "$base.LauncherDefault" /tmp/spool-old-query.txt
  if grep -q "$base.LauncherSpool" /tmp/spool-old-query.txt; then
    echo 'Old upgrade fixture unexpectedly has the new canonical alias'; exit 1
  fi
fi
# State setup is an emulator-only privilege, not an app capability.
adb root
adb wait-for-device
test "$(adb shell id -u | tr -d '\r')" = 0
# Emulate saved package state from an old selected icon, then an upgrade.
adb shell am force-stop "$pkg"
adb shell pm enable "$pkg/$base.LauncherNeonCyan"
adb shell pm disable "$pkg/$base.LauncherDefault"
adb install -r "$apk"
# The new canonical component must be in the drawer before any manual app launch.
for attempt in $(seq 1 20); do
  adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$pkg" > /tmp/spool-prelaunch-query.txt
  if grep -q 'com.music.bitchord.LauncherSpool' /tmp/spool-prelaunch-query.txt; then break; fi
  sleep 1
done
cat /tmp/spool-prelaunch-query.txt
grep -q 'com.music.bitchord.LauncherSpool' /tmp/spool-prelaunch-query.txt
python3 - <<'CHECK'
from pathlib import Path
rows=[x.strip() for x in Path('/tmp/spool-prelaunch-query.txt').read_text().splitlines() if 'app.spool.music.dev/' in x]
assert len(rows)==1 and rows[0].endswith('com.music.bitchord.LauncherSpool'), rows
print('Exactly one canonical drawer target before first app launch')
CHECK
adb shell am start -W -n "$pkg/$base.LauncherNeonCyan" > /tmp/spool-old-alias-launch.txt
cat /tmp/spool-old-alias-launch.txt
if grep -qE 'Error:|Exception|Status: timeout' /tmp/spool-old-alias-launch.txt; then exit 1; fi
sleep 3
adb shell pidof "$pkg"
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$pkg" > /tmp/spool-launcher-query.txt
cat /tmp/spool-launcher-query.txt
python3 - <<'CHECK'
from pathlib import Path
rows=[x.strip() for x in Path('/tmp/spool-launcher-query.txt').read_text().splitlines() if 'app.spool.music.dev/' in x]
assert len(rows)==1, rows
assert rows[0].endswith('com.music.bitchord.LauncherSpool'), rows
print('Exactly one app-drawer launcher after saved-state upgrade')
CHECK
# Android music-app pickers must still find the real music handlers.
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.APP_MUSIC -p "$pkg" > /tmp/spool-music-query.txt
cat /tmp/spool-music-query.txt
grep -q "$pkg/" /tmp/spool-music-query.txt
# Every retired explicit component must remain launchable after normalization.
python3 - <<'CHECK' > /tmp/spool-aliases.txt
import re
s=open('app/src/main/java/com/music/bitchord/AppIcon.kt').read()
aliases=re.findall(r'"(Launcher\w+)"',s)
legacy=[x for x in aliases if x != 'LauncherSpool']
assert len(legacy)==23 and len(set(legacy))==23, legacy
print('\n'.join(legacy))
CHECK
while IFS= read -r alias; do
  adb shell am start -W -n "$pkg/$base.$alias" > /tmp/spool-explicit.txt
  if grep -qE 'Error:|Exception|Status: timeout' /tmp/spool-explicit.txt; then cat /tmp/spool-explicit.txt; exit 1; fi
  printf 'Explicit component OK: %s\n' "$alias"
done < /tmp/spool-aliases.txt
# This proves component intents, not a particular launcher's pinned-shortcut UI.

# Common upgrade state too: Default was selected and enabled, no alternate icon.
if [ "$#" -ge 2 ]; then
  adb uninstall "$pkg"
  adb install "$2"
  adb shell am start -W -n "$pkg/$base.LauncherDefault" > /tmp/spool-fixture-start.txt
  cat /tmp/spool-fixture-start.txt
  if grep -qE 'Error:|Exception|Status: timeout' /tmp/spool-fixture-start.txt; then exit 1; fi
  adb shell am force-stop "$pkg"
  adb install -r "$apk"
  adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$pkg" > /tmp/spool-default-upgrade.txt
  cat /tmp/spool-default-upgrade.txt
  python3 - <<'CHECK'
from pathlib import Path
rows=[x.strip() for x in Path('/tmp/spool-default-upgrade.txt').read_text().splitlines() if 'app.spool.music.dev/' in x]
assert len(rows)==1 and rows[0].endswith('com.music.bitchord.LauncherSpool'), rows
print('Default-selected upgrade has exactly one canonical drawer target before first launch')
CHECK
  adb shell am start -W -n "$pkg/$base.LauncherSpool"
  adb shell pidof "$pkg"
fi
