#!/usr/bin/env bash
# AFTKRT Fire TV Stick 4K Max (2nd Gen) snapshot: private files stay on the operator's PC.
# This saves reproducible settings, app/package inventory, screenshot and private theme.
# Android/Fire OS does NOT grant ADB a full backup of private app data or logins.
set -euo pipefail
umask 077

if [[ -z "$(printenv TV_HOST 2>/dev/null || true)" ]]; then
    echo "Usage: TV_HOST=<Fire-TV-LAN-IP> bash tools/firetv-backup.sh" >&2
    exit 2
fi

serial="$TV_HOST"
case "$serial" in *:*) ;; *) serial="$serial:5555" ;; esac
base="$HOME/tv-tools/firetv-max-backup"
stamp="$(date +%Y%m%d-%H%M%S)"
folder="final-$stamp"
out="$base/$folder"
archive="$HOME/tv-tools/FireTV-FamilyEdition-Final-Backup-$stamp.tar.gz"

mkdir -p "$out/device" "$out/theme" "$out/host-weather"
adb connect "$serial" >/dev/null
tv() { adb -s "$serial" "$@"; }

model="$(tv shell getprop ro.product.model | tr -d '\r\n')"
sdk="$(tv shell getprop ro.build.version.sdk | tr -d '\r\n')"
if [[ "$model" != AFTKRT || "$sdk" != 30 ]]; then
    echo "Refusing backup: expected AFTKRT / SDK 30, got $model / SDK $sdk" >&2
    exit 1
fi

echo "Reading Fire TV configuration ($model, SDK $sdk)..."
{
    printf "Captured: %s\n" "$(date -Is)"
    printf "ADB target: %s\nModel: %s\nAndroid SDK: %s\n" "$serial" "$model" "$sdk"
    printf "Purpose: Fire TV Family Edition private restore/reference snapshot\n"
} > "$out/manifest.txt"

tv shell getprop > "$out/device/getprop.txt"
tv shell pm list packages > "$out/device/packages-all.txt"
tv shell pm list packages -d > "$out/device/packages-disabled.txt"
tv shell pm list packages -e > "$out/device/packages-enabled.txt"
tv shell pm list packages -3 > "$out/device/packages-third-party.txt"
tv shell pm list packages -i > "$out/device/packages-installers.txt"
tv shell dumpsys accessibility > "$out/device/accessibility.txt"
tv shell dumpsys role > "$out/device/home-roles.txt" || true
tv shell settings get secure accessibility_enabled > "$out/device/accessibility-enabled.txt"
tv shell settings get secure enabled_accessibility_services > "$out/device/accessibility-services.txt"
tv shell settings get secure screensaver_components > "$out/device/screensaver-components.txt"
tv shell settings get system screen_off_timeout > "$out/device/screen-off-timeout.txt"
tv shell dumpsys activity activities | grep 'mResumedActivity' > "$out/device/resumed-activity.txt" || true

for pkg in com.gathin.tvhome com.amazon.tv.launcher com.amazon.firebat \
    com.plexapp.android com.tailscale.ipn com.nuvio.tv com.nuvio.app; do
    if tv shell pm path "$pkg" | grep -q '^package:'; then
        {
            echo "===== $pkg ====="
            tv shell dumpsys package "$pkg" | awk '/versionCode=|versionName=/ { if (n++ < 2) print }'
        } >> "$out/device/key-app-versions.txt"
    fi
done

if tv exec-out screencap -p > "$out/device/home-screen.png"; then
    test -s "$out/device/home-screen.png" || rm -f "$out/device/home-screen.png"
else
    rm -f "$out/device/home-screen.png"
    echo "Screenshot unavailable" >> "$out/manifest.txt"
fi

theme_dir="$base/firetv-cosy-weather-local"
if [[ -f "$theme_dir/config.json" ]]; then
    cp -a "$theme_dir/." "$out/theme/"
else
    echo "Missing private Fire TV Cosy theme config" >> "$out/manifest.txt"
fi

# Read the actual applied overlay from our launcher ContentProvider when available.
# Unlike the host copy, this catches any edits made directly on the Fire TV.
if tv exec-out content read --uri content://com.gathin.tvhome.theme/config.json \
        > "$out/theme/applied-config.json" 2>/dev/null \
        && python3 -m json.tool "$out/theme/applied-config.json" >/dev/null 2>&1; then
    cp "$out/theme/applied-config.json" "$out/theme/config.json"
    echo "Captured and validated live Fire TV theme overlay" >> "$out/manifest.txt"
else
    rm -f "$out/theme/applied-config.json"
    echo "Live theme was not readable; using host-side config when available" >> "$out/manifest.txt"
fi

weather_script="$HOME/docker/familyos-tv-weather/tv_weather.py"
if [[ -f "$weather_script" ]]; then
    cp -p "$weather_script" "$out/host-weather/tv_weather.py"
fi
if command -v docker >/dev/null 2>&1 && docker inspect familyos-tv-weather >/dev/null 2>&1; then
    docker inspect familyos-tv-weather \
      --format '{{range .Config.Env}}{{println .}}{{end}}' \
      | grep -E '^(TV_WEATHER_ALLOWED_IPS|FAMILYOS_ENVIRONMENT_URL)=' > "$out/host-weather/weather-connection.env" || true
    docker inspect familyos-tv-weather \
      --format 'Image: {{.Config.Image}}\nNetwork: {{.HostConfig.NetworkMode}}\nRestart: {{.HostConfig.RestartPolicy.Name}}' \
      > "$out/host-weather/container-basic.txt"
fi

cat > "$out/README-RESTORE.md" <<'EOF'
# Private Fire TV restore reference

This is a partial, non-root ADB backup, NOT a complete Fire OS image.
It captures package and debloat lists, accessibility/HOME state, and the private
theme/weather configuration. No APK installers or protected app data are included.

## Recover Family Edition

1. Keep Amazon HOME and com.amazon.imp enabled; do not bulk-disable packages.
2. Enable ADB on the AFTKRT Fire Stick and connect from Docker.
3. Obtain the trusted, permanently signed Family Edition APK from the approved
   GitHub Actions signed artifact or your separate signing-key backup.
   Do not use a disposable-signature CI preview.
4. Apply the saved theme using TV_HOST=<ip> bash tools/theme.sh <extracted-backup>/theme.
5. Enable and test the reversible HOME overlay using
   TV_HOST=<ip> bash tools/firetv-home.sh on
6. Verify Prime Video, Plex, physical HOME, playback and Alexa.
7. Re-download third-party apps from their official sources as needed; sign back in.

For immediate rollback:
   TV_HOST=<ip> bash tools/firetv-home.sh off

The debloat list in device/packages-disabled.txt is for reference only:
Amazon package requirements can change between firmware versions.

## Important limitations

ADB cannot back up protected private data, logins, Tailscale VPN identity,
Amazon account state or app passwords without elevated device access.
Tailscale and streaming apps might require reauthentication after a factory reset.
Screenshots, local addresses and inventories here are private: do not commit
this archive or its extracted contents to the public GitHub repository.
EOF

tar -C "$base" -czf "$archive" "$folder"
sha256sum "$archive" > "$archive.sha256"
chmod 600 "$archive" "$archive.sha256"

echo
echo "BACKUP COMPLETE"
echo "Folder:   $out"
echo "Archive:  $archive"
echo "Checksum: $archive.sha256"
du -h "$archive"
