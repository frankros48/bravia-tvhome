#!/usr/bin/env bash
# Read-only. Prints what the TV is, so we know what we are working with.
# Usage: TV_HOST=192.168.1.50 tools/tv-probe.sh
source "$(dirname "$0")/common.sh"
connect

show() { printf '%-18s %s\n' "$1" "$2"; }
prop() { show "$1" "$(tv getprop "$2")"; }

prop "manufacturer" ro.product.manufacturer
prop "model" ro.product.model
prop "device" ro.product.device
prop "android" ro.build.version.release
prop "api level" ro.build.version.sdk
prop "security patch" ro.build.version.security_patch
prop "build" ro.build.display.id
show "memory" "$(tv grep MemTotal /proc/meminfo)"
show "home app now" "$(current_home)"
