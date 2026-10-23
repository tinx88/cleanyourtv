#!/usr/bin/env bash
# AFTKRT / Fire OS 8: install and enable the optional Family Edition HOME overlay.
# Amazon's original HOME, HOME role, OTA packages and existing debloat are untouched.
#
# TV_HOST=<fire-tv-ip> tools/firetv-home.sh install /path/to/stable-signed-apk
# TV_HOST=<fire-tv-ip> tools/firetv-home.sh status
# TV_HOST=<fire-tv-ip> tools/firetv-home.sh off     # instant rollback
# TV_HOST=<fire-tv-ip> tools/firetv-home.sh on
set -euo pipefail

ACTION="${1:-status}"
: "${TV_HOST:?Set TV_HOST to your Fire TV IP address}"
SERIAL="${TV_HOST}"
case "$SERIAL" in *:*) ;; *) SERIAL="$SERIAL:5555" ;; esac
PKG="com.gathin.tvhome"
SERVICE="$PKG/$PKG.FireTvHomeService"
BACKUP_DIR="${BACKUP_DIR:-$HOME/.local/state/cleanyourtv/firetv-home}"

adb connect "$SERIAL" >/dev/null
tv() { adb -s "$SERIAL" "$@" </dev/null; }
setting() { tv shell settings get secure enabled_accessibility_services | tr -d '\r\n'; }

# Fail closed: this helper is for the confirmed Fire OS 8 AFTKRT only.
MODEL="$(tv shell getprop ro.product.model | tr -d '\r\n')"
SDK="$(tv shell getprop ro.build.version.sdk | tr -d '\r\n')"
if [[ "$MODEL" != AFTKRT || "$SDK" != 30 ]]; then
    echo "Refusing to change $MODEL (SDK $SDK); this helper is tested for AFTKRT / Fire OS 8 only." >&2
    exit 1
fi
if ! tv shell pm list packages -e | tr -d '\r' | grep -qx 'package:com.amazon.tv.launcher'; then
    echo "Amazon HOME must stay enabled. Restore it before using this helper." >&2
    exit 1
fi

show_status() {
    echo "Model: $MODEL (Android SDK $SDK)"
    current="$(setting)"
    case ":$current:" in
        *":$SERVICE:"*) echo "Family Edition Fire TV redirect: enabled in settings" ;;
        *) echo "Family Edition Fire TV redirect: OFF (Amazon HOME)" ;;
    esac
    tv shell dumpsys accessibility | grep -E 'Enabled services:|Bound services:|Crashed services:' || true
}

enable_redirect() {
    if ! tv shell pm path "$PKG" | grep -q '^package:'; then
        echo "Install the stable signed Family Edition APK first." >&2
        exit 1
    fi
    current="$(setting)"
    [[ "$current" == null ]] && current=""
    case ":$current:" in
        *":$SERVICE:"*) ;;
        *)
            updated="${current:+$current:}$SERVICE"
            tv shell settings put secure enabled_accessibility_services "$updated"
            ;;
    esac
    tv shell settings put secure accessibility_enabled 1
    echo "Fire TV redirect enabled; Amazon HOME is still installed and active."
}

disable_redirect() {
    current="$(setting)"
    [[ "$current" == null ]] && current=""
    updated=""
    IFS=: read -r -a services <<< "$current"
    for item in "${services[@]}"; do
        [[ -z "$item" || "$item" == "$SERVICE" ]] && continue
        updated="${updated:+$updated:}$item"
    done
    if [[ -n "$updated" ]]; then
        tv shell settings put secure enabled_accessibility_services "$updated"
        tv shell settings put secure accessibility_enabled 1
    else
        tv shell settings delete secure enabled_accessibility_services >/dev/null
        tv shell settings put secure accessibility_enabled 0
    fi
    tv shell input keyevent KEYCODE_HOME
    echo "Fire TV redirect OFF; Amazon HOME restored. Other disabled packages unchanged."
}

case "$ACTION" in
    install)
        APK="${2:-}"
        [[ -f "$APK" ]] || { echo "Usage: TV_HOST=<ip> $0 install /path/to/family-edition-signed.apk" >&2; exit 2; }
        mkdir -p "$BACKUP_DIR"
        SNAP="$BACKUP_DIR/before-$(date +%Y%m%d-%H%M%S)"
        tv shell pm list packages -d > "$SNAP.disabled-packages.txt"
        setting > "$SNAP.accessibility-services.txt"
        tv shell dumpsys role > "$SNAP.roles.txt"
        echo "Read-only safety snapshots in $BACKUP_DIR"
        tv install -r "$APK"
        START="$(tv shell am start -W -n "$PKG/.MainActivity")"
        echo "$START"
        if [[ "$START" != *"Status: ok"* || "$START" == *"Error:"* ]]; then
            echo "Family Edition did not start; accessibility redirect NOT enabled." >&2
            exit 1
        fi
        tv shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1 || true
        enable_redirect
        ;;
    on) enable_redirect ;;
    off) disable_redirect ;;
    status) show_status ;;
    *)
        echo "Usage: TV_HOST=<ip> $0 {install <signed.apk>|on|off|status}" >&2
        exit 2
        ;;
esac
