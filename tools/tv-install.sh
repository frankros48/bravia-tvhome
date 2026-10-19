#!/usr/bin/env bash
# Installs the launcher and makes it the Home app. The stock launcher stays installed and enabled
# unless you pass --disable-stock. Undo everything with tools/tv-restore.sh.
# Usage: TV_HOST=192.168.1.50 tools/tv-install.sh [--disable-stock]
source "$(dirname "$0")/common.sh"

apk="$ROOT/launcher/build/tvhome.apk"
[ -f "$apk" ] || die "build the launcher first: launcher/build.sh"
[ -e "$BACKUP_DIR/latest/packages-all.txt" ] || die "run tools/tv-backup.sh first"
connect

sdk="$(tv getprop ro.build.version.sdk)"
[ "${sdk:-0}" -ge 26 ] 2>/dev/null || die "the launcher needs Android 8.0 or newer (API 26); the TV reports API ${sdk:-unknown}"

# Remember the stock Home app so restore can bring it back.
stock="$(current_home)"
case "$stock" in
  */*) ;;
  *) die "could not tell which app is Home now (got: ${stock:-nothing})" ;;
esac
stock_pkg="${stock%%/*}"
if [ "$stock_pkg" != "$APP_ID" ]; then
  echo "$stock" > "$BACKUP_DIR/stock-home.txt"
fi

"$ADB" -s "$SERIAL" install -r "$apk"
tv cmd package set-home-activity --user 0 "$APP_HOME"
echo "Home app is now: $(current_home)"

if [ "${1:-}" = "--disable-stock" ]; then
  [ "$stock_pkg" != "android" ] && [ "$stock_pkg" != "$APP_ID" ] ||
    die "refusing to disable '$stock_pkg'"
  tv pm disable-user --user 0 "$stock_pkg"
  grep -qx "$stock_pkg" "$BACKUP_DIR/disabled-by-us.txt" 2>/dev/null ||
    echo "$stock_pkg" >> "$BACKUP_DIR/disabled-by-us.txt"
  echo "disabled stock launcher: $stock_pkg"
fi
echo "Press Home on the remote."
