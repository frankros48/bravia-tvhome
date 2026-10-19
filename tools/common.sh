#!/usr/bin/env bash
# Shared helpers. Source this file from the other scripts; do not run it directly.
# Talks to the TV only through Google's own `adb`, to the single address in TV_HOST.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="$ROOT/backup"
APP_ID="com.frank.tvhome"
APP_HOME="$APP_ID/.HomeActivity"
ADB="${ADB:-adb}"
HOME_QUERY="cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"

die() {
  echo "error: $*" >&2
  exit 1
}

# Sets SERIAL and checks the TV answers. Run this before any tv call.
connect() {
  [ -n "${TV_HOST:-}" ] || die "set TV_HOST to the TV's IP address, e.g. TV_HOST=192.168.1.50"
  command -v "$ADB" >/dev/null 2>&1 || die "adb not found (brew install android-platform-tools)"
  SERIAL="$TV_HOST:${TV_PORT:-5555}"
  "$ADB" connect "$SERIAL" >/dev/null 2>&1 || true
  local state
  state="$("$ADB" -s "$SERIAL" get-state 2>/dev/null || true)"
  [ "$state" = "device" ] ||
    die "TV not reachable (state: ${state:-none}). Is debugging on, and did you accept the prompt on the TV?"
}

# Run one shell command on the TV. stdin is closed so adb cannot swallow a calling loop's input.
tv() {
  "$ADB" -s "$SERIAL" shell "$@" </dev/null | tr -d '\r'
}

# Package names from a list file: strips comments and blanks.
read_list() {
  sed -E 's/#.*$//; s/[[:space:]]+//g' "$1" | grep -v '^$' || true
}

# Packages that must never be disabled by a list unless FORCE=1.
# Add anything else your TV breaks without (Sony system apps, for instance).
is_protected() {
  case "$1" in
    android | com.android.systemui | com.android.settings | com.android.tv.settings | \
      com.android.providers.* | com.android.shell | com.android.inputdevices | \
      com.android.packageinstaller | com.google.android.packageinstaller | \
      com.google.android.permissioncontroller | com.android.inputmethod.latin | \
      com.google.android.inputmethod.latin | com.google.android.gms | com.google.android.gsf | \
      com.android.vending | com.google.android.tvlauncher | com.google.android.leanbacklauncher | \
      "$APP_ID")
      return 0
      ;;
  esac
  return 1
}

# The component currently answering the Home button, e.g. com.vendor.launcher/.Home
current_home() {
  tv $HOME_QUERY | tail -n 1
}
