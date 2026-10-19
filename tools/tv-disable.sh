#!/usr/bin/env bash
# Disables the packages named in a list file (one per line, # comments allowed).
# Disabled, not uninstalled: tools/tv-restore.sh turns them all back on.
# Refuses core system packages unless FORCE=1. DRY_RUN=1 shows what would happen.
# Usage: TV_HOST=192.168.1.50 tools/tv-disable.sh lists/mine.txt
source "$(dirname "$0")/common.sh"

list="${1:-}"
[ -n "$list" ] || die "usage: tv-disable.sh <list file>"
[ -f "$list" ] || die "no such file: $list"
[ -e "$BACKUP_DIR/latest/packages-all.txt" ] || die "run tools/tv-backup.sh first"
connect

installed="$(tv pm list packages)"
log="$BACKUP_DIR/disabled-by-us.txt"
touch "$log"

while read -r pkg; do
  if is_protected "$pkg" && [ "${FORCE:-0}" != "1" ]; then
    echo "skip (protected):     $pkg"
    continue
  fi
  if ! printf '%s\n' "$installed" | grep -qx "package:$pkg"; then
    echo "skip (not installed): $pkg"
    continue
  fi
  if [ "${DRY_RUN:-0}" = "1" ]; then
    echo "would disable:        $pkg"
    continue
  fi
  # One pm call per package: chained pm commands can silently stop after the first.
  out="$(tv pm disable-user --user 0 "$pkg" 2>&1 || true)"
  if printf '%s' "$out" | grep -q 'disabled-user'; then
    echo "disabled:             $pkg"
    grep -qx "$pkg" "$log" || echo "$pkg" >> "$log"
  else
    echo "FAILED:               $pkg ($out)"
  fi
done < <(read_list "$list")
