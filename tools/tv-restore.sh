#!/usr/bin/env bash
# Puts the TV back: re-enables every package these scripts disabled (plus any in an optional list),
# re-enables the stock launcher, and removes our launcher so the stock Home returns.
# Usage: TV_HOST=192.168.1.50 tools/tv-restore.sh [extra-list.txt]
source "$(dirname "$0")/common.sh"
connect

mkdir -p "$BACKUP_DIR"
log="$BACKUP_DIR/disabled-by-us.txt"
{
  [ -f "$log" ] && cat "$log"
  [ -n "${1:-}" ] && read_list "$1"
  [ -s "$BACKUP_DIR/stock-home.txt" ] && sed -E 's#/.*$##' "$BACKUP_DIR/stock-home.txt"
  true
} | sort -u > "$BACKUP_DIR/.restore-set"

while read -r pkg; do
  [ -n "$pkg" ] || continue
  echo "enable: $pkg  $(tv pm enable "$pkg" 2>&1 || true)"
done < "$BACKUP_DIR/.restore-set"
rm -f "$BACKUP_DIR/.restore-set"

if tv pm list packages | grep -qx "package:$APP_ID"; then
  "$ADB" -s "$SERIAL" uninstall "$APP_ID"
fi
: > "$log"
echo "Home app is now: $(current_home)"
