#!/usr/bin/env bash
# Verifies that all launcher actions and settings shortcuts have at least one
# working resolver on the target connected TV (Android 11 or Android 14 Google TV).
#
# Usage:
#   kit/verify_tv_intents.sh [device-ip-or-serial]
set -euo pipefail

ADB="${ADB:-$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"
if [ ! -x "$ADB" ]; then
  echo "Error: adb binary not found." >&2
  exit 1
fi

DEVICE="${1:-}"
if [ -z "$DEVICE" ]; then
  # Grab the first connected device
  DEVICE="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
fi

if [ -z "$DEVICE" ]; then
  echo "Error: No connected ADB device found." >&2
  exit 1
fi

echo "=================================================="
echo "Verifying Launcher Action Resolvers on: $DEVICE"
echo "=================================================="

MODEL="$("$ADB" -s "$DEVICE" shell getprop ro.product.model | tr -d '\r')"
SDK="$("$ADB" -s "$DEVICE" shell getprop ro.build.version.sdk | tr -d '\r')"
echo "Target: $MODEL (Android SDK $SDK)"
echo "--------------------------------------------------"

TOTAL_PASS=0
TOTAL_FAIL=0

check_action() {
  local feature_name="$1"
  shift
  local resolved=""

  for candidate in "$@"; do
    local out=""
    case "$candidate" in
      -a\ *)
        out="$("$ADB" -s "$DEVICE" shell cmd package resolve-activity "$candidate" 2>/dev/null || true)"
        ;;
      -n\ *)
        out="$("$ADB" -s "$DEVICE" shell cmd package resolve-activity "$candidate" 2>/dev/null || true)"
        ;;
      service:*)
        local comp="${candidate#service:}"
        # Services don't always have resolve-service CLI, check dumpsys package
        local pkg="${comp%%/*}"
        local cls="${comp##*/}"
        out="$("$ADB" -s "$DEVICE" shell dumpsys package "$pkg" 2>/dev/null | grep "$cls" || true)"
        ;;
      package:*)
        local pkg="${candidate#package:}"
        out="$("$ADB" -s "$DEVICE" shell pm list packages "$pkg" 2>/dev/null || true)"
        ;;
    esac

    if [ -n "$out" ] && ! echo "$out" | grep -q "No activity found"; then
      # Extract matched component name if available
      local match_name
      match_name="$(echo "$out" | awk -F'=' '/name=/ {print $2; exit}' | tr -d ' ')"
      if [ -z "$match_name" ]; then
        match_name="$candidate"
      fi
      resolved="$match_name"
      break
    fi
  done

  if [ -n "$resolved" ]; then
    printf "  [PASS] %-25s -> %s\n" "$feature_name" "$resolved"
    TOTAL_PASS=$((TOTAL_PASS + 1))
  else
    printf "  [FAIL] %-25s -> NO WORKING RESOLVER FOUND!\n" "$feature_name"
    TOTAL_FAIL=$((TOTAL_FAIL + 1))
  fi
}

# 1. Screensaver
check_action "Screensaver" \
  "-a com.google.android.pano.action.SLEEP" \
  "-n com.android.tv.settings/com.android.tv.settings.device.display.daydream.DaydreamVoiceAction" \
  "-a com.google.android.tv.settings.ambient" \
  "-n com.android.systemui/com.android.systemui.Somnambulator" \
  "-a android.settings.DREAM_SETTINGS"

# 2. TV Inputs
check_action "TV Inputs" \
  "-a com.android.tv.action.VIEW_INPUTS" \
  "-n com.tcl.suspension/com.tcl.suspension.ui.main.InputActivity" \
  "service:com.tcl.tv/com.tcl.player.service.ShowWindowService" \
  "service:com.tcl.tv/com.tcl.sourcemenu.insrc.ShowWindowService" \
  "-n com.tcl.tvinput/com.tcl.tvinput.MainActivity"

# 3. Accounts & Sign-in
check_action "Accounts" \
  "-n com.android.tv.settings/com.google.android.tv.settings.AccountActivity" \
  "-a android.settings.ACCOUNT_SYNC_SETTINGS" \
  "-n com.android.tv.settings/com.android.tv.settings.accounts.AccountSyncActivity" \
  "-a android.settings.SETTINGS"

# 4. Network Settings
check_action "Network Settings" \
  "-n com.android.tv.settings/com.android.tv.settings.connectivity.NetworkActivity" \
  "-a android.settings.NETWORK_SETTINGS_TV_OEM_LINK" \
  "-a com.tcl.settings.action.NETWORKSETTINGS" \
  "-n com.android.tv.settings/com.android.tv.settings.oemlink.NetworkSettingsActivity" \
  "-a android.settings.WIRELESS_SETTINGS" \
  "-a android.settings.SETTINGS"

# 5. Remotes & Bluetooth
check_action "Remotes & Bluetooth" \
  "-a com.android.tv.settings.ACCESSORIES_CONNECTED_DEVICES" \
  "-n com.android.tv.settings/com.tcl.settings.accessories.AccessoriesActivity" \
  "-n com.android.tv.settings/com.tcl.android.tv.settings.accessories.AccessoriesActivity" \
  "-n com.android.tv.settings/com.android.tv.settings.accessories.AddAccessoryActivity" \
  "-a android.settings.BLUETOOTH_SETTINGS" \
  "-a android.settings.SETTINGS"

# 6. Picture & Display
check_action "Picture Settings" \
  "-n com.android.tv.settings/com.tcl.settings.PictureActivity" \
  "-a android.settings.DISPLAY_SETTINGS" \
  "-a android.settings.SETTINGS"

# 7. Accessibility Settings
check_action "Accessibility" \
  "-a android.settings.ACCESSIBILITY_TV_OEM_LINK" \
  "-n com.android.tv.settings/com.android.tv.settings.oemlink.AccessibilitySettingsActivity" \
  "-a android.settings.ACCESSIBILITY_SETTINGS" \
  "-a android.settings.SETTINGS"

# 8. App Management
check_action "App Management" \
  "-n com.android.tv.settings/com.android.tv.settings.device.apps.AllAppsActivity" \
  "-n com.android.tv.settings/com.android.tv.settings.device.apps.AppsActivity" \
  "-a android.settings.MANAGE_APPLICATIONS_SETTINGS" \
  "-a android.settings.SETTINGS"

# 9. Main Settings
check_action "Main Settings" \
  "-a android.settings.SETTINGS"

# 10. Live TV
check_action "Live TV" \
  "package:com.tcl.tv" \
  "-n com.tcl.tv/com.tcl.player.TVActivity" \
  "-n com.tcl.tv/com.tcl.tv.TVActivity"

# 11. YouTube / Clients
check_action "YouTube / Leanback" \
  "package:com.google.android.youtube.tv" \
  "package:com.liskovsoft.videomanager" \
  "package:com.liskovsoft.smarttubetv.beta" \
  "package:com.liskovsoft.smarttubetv"

echo "--------------------------------------------------"
echo "Summary: $TOTAL_PASS passed, $TOTAL_FAIL failed."

if [ "$TOTAL_FAIL" -gt 0 ]; then
  echo "Verification FAILED: One or more features lack working intent resolvers on this TV!" >&2
  exit 1
fi

echo "Verification SUCCESSFUL: All 11 launcher features are verified on $DEVICE."
exit 0
