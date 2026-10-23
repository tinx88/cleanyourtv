# Family Edition on Fire TV Stick 4K Max (2nd Gen)

**Experimental / opt-in.** The Fire OS 8 accessibility redirect is adapted from the
[event-driven Fire TV Glass approach](https://github.com/ivantrj/firetv-glass-launcher).
This fork has now been tested on an AFTKRT / Fire OS 8 (Android SDK 30) Fire TV
Stick 4K Max (2nd Gen). The HOME overlay works but has a **known Settings
navigation conflict** described below; leave the Amazon system launcher enabled.

## Why this differs from Sony and TCL

Amazon protects `com.amazon.tv.launcher`, so normal HOME role changes do not take over
Fire OS. The separate `FireTvHomeService` watches for Amazon-launcher window changes
and opens Family Edition on top. It cannot replace the system HOME role; Amazon's
launcher can flash briefly before the redirect.

The existing `HomeKeyService` and `InputKeyService` for Android/Google TV are
untouched. No services are enabled automatically by installing the APK.

## Install and restore (Docker/Linux with adb)

Use the trusted **`family-edition-signed-apk`** artifact, not the disposable-signature
CI preview. Install over an existing Family Edition build with `adb install -r`;
never uninstall to fix a signing mismatch. The stable certificate is shared with the
Sony and TCL installations. On a new Fire TV, the app normally has no previous data.

```bash
TV_HOST=<fire-tv-ip> tools/firetv-home.sh install /path/to/app-release.apk
TV_HOST=<fire-tv-ip> tools/firetv-home.sh status
```

The helper refuses devices other than **AFTKRT, Android SDK 30** and refuses to
continue unless Amazon HOME is enabled. It snapshots the currently disabled
packages, HOME roles and accessibility service list locally before installing.
It only adds the Fire TV redirect to any existing accessibility services; it
does **not** change the HOME role or disable, uninstall or reset Amazon packages.

### Immediate rollback

```bash
TV_HOST=<fire-tv-ip> tools/firetv-home.sh off
```

This removes only our Fire TV accessibility service and sends HOME to Amazon.
The Family Edition APK stays installed, and existing Fire TV debloat remains as-is.
Alternatively turn off **Family Edition Fire TV Home** in Fire TV Accessibility
settings. To restore the redirect, run `tools/firetv-home.sh on`.

## Cosy theme

`themes/firetv-cosy-weather/config.json` supplies a Fire TV dock (Plex, Prime
Video, YouTube, Jellyfin) and the same six-column Cosy rendering as the other
TVs. Only apps actually installed will appear.

Set `weather_url` in a **private copy** of that config to your home weather
service URL, then apply the folder with `tools/theme.sh`. Do not commit your
LAN address to this public repository. The local weather service also needs
the Fire TV's LAN IP in its allowlist; without that, weather updates return
403. The procedural Cosy background itself does not depend on JPG files.

## Field-test results and known limitation

Verified on a real AFTKRT on 23 October 2026:

- The permanently signed Family Edition APK installed successfully.
- The six-column Cosy theme and optional local weather feed render correctly;
  the weather endpoint returned HTTP 200 from the Fire TV.
- Opening Prime Video and Plex, then pressing HOME, returned to Family Edition.
- The accessibility service stayed enabled, bound and crash-free after reboot;
  pressing HOME after boot returned to Family Edition.
- Bringing the Amazon HOME activity to the foreground also returned to Family
  Edition without a subsequent HOME key press.
- Physical HOME remote, actual video/audio playback and Alexa were tested and work.
- Sideloaded apps appeared in the app library.

**Known issue — Fire TV Settings:** The current accessibility listener covers
*any* window-state change from `com.amazon.tv.launcher`, not only
`HomeActivity_vNext`. On this firmware, opening Amazon Settings from Family
Edition may require a second press, and navigating Settings can unexpectedly
bounce back to Family Edition. This was reproduced with the redirect ON and
disappeared with it OFF. The device owner chose to keep the functioning HOME
redirect and accept this inconvenience rather than change the service.

When full Settings access is needed, temporarily disable the redirect:

```bash
TV_HOST=<fire-tv-ip> bash tools/firetv-home.sh off
# Use Fire TV Settings normally. Amazon HOME will be active during this interval.
TV_HOST=<fire-tv-ip> bash tools/firetv-home.sh on
```

Do not change the existing `HomeKeyService` for Sony/TCL to work around this
Fire OS-specific behaviour.

## Private backup and restore

The reusable backup helper captures an **unprivileged ADB snapshot** of the
Fire TV device (build info, installed/disabled packages, accessibility/HOME
state, selected settings, screenshot), plus **only** the permanently signed
Family Edition APK if held on the Docker host, and a local copy of the weather
and Cosy theme config. Third-party installers are deliberately omitted: obtain
them from their official sources when restoring.

```bash
git pull --ff-only origin feature/firetv-home-overlay
TV_HOST=<fire-tv-ip> bash tools/firetv-backup.sh
```

It saves `~/tv-tools/FireTV-FamilyEdition-Final-Backup-<timestamp>.tar.gz`
with a matching `.sha256` checksum. Copy the private archive to separate,
protected storage. **Never commit the archive, IP addresses, screenshots or
private configuration to this public repository.** Signing keys are not backed
up by this helper; keep the separate secure signing-key backup.

Android prevents ordinary ADB backups of protected app data, VPN credentials,
streaming sign-ins and Amazon account state. After a factory reset, those
apps may need fresh sign-in even when their APKs are restored.

## Safety and testing

- Keep `com.amazon.tv.launcher`, `com.amazon.firehomestarter`, Prime Video
  (`com.amazon.firebat`), Plex, sign-in (`com.amazon.imp`), Appstore, casting
  and OTA packages enabled.
- Only the new `FireTvHomeService` should be enabled on Fire TV; do not enable
  the Google TV `HomeKeyService` as a second Home redirect.
- Confirm HOME after boot, after leaving Prime/Plex, and after exiting Settings.
  Verify actual playback, Fire remote, Alexa, audio and screen wake by hand.
- The redirect runs on window change events, not a background polling loop.
  Fire OS may prevent accessibility from starting apps after firmware updates.
  If HOME doesn't redirect or Settings becomes inaccessible, use `off` above.
- Do not use the generic `launcher/build-and-install.sh` HOME-switch installer
  on Fire OS. The Fire TV script deliberately leaves Amazon HOME in control.

The device-specific package inventory and LAN details belong in a *private*
handover, not in the public repository.
