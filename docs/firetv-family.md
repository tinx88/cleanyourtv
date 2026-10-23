# Family Edition on Fire TV Stick 4K Max (2nd Gen)

**Experimental / opt-in.** The Fire OS 8 accessibility redirect is adapted from the
[event-driven Fire TV Glass approach](https://github.com/ivantrj/firetv-glass-launcher).
That project reports tests on AFTKRT / Android 11. This fork's Family Edition redirect
still needs physical device testing before it is considered stable.

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
