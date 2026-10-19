#!/usr/bin/env bash
# Read-only on the TV. Saves its package lists, properties and settings under backup/.
# The folder holds serial numbers and device ids, so it is git-ignored and locked down. Keep it local.
# Usage: TV_HOST=192.168.1.50 tools/tv-backup.sh
source "$(dirname "$0")/common.sh"
connect

stamp="$(date +%Y%m%d-%H%M%S)"
dir="$BACKUP_DIR/$stamp"
mkdir -p "$dir"
chmod -R go-rwx "$BACKUP_DIR"

tv pm list packages -f > "$dir/packages-all.txt"
tv pm list packages -d > "$dir/packages-disabled.txt"
tv pm list packages -3 > "$dir/packages-user.txt"
tv getprop > "$dir/getprop.txt"
for ns in system secure global; do
  tv settings list "$ns" > "$dir/settings-$ns.txt"
done
current_home > "$dir/home-default.txt"

ln -sfn "$dir" "$BACKUP_DIR/latest"
echo "saved to $dir ($(wc -l < "$dir/packages-all.txt" | tr -d ' ') packages)"
