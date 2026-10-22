# Family Edition: daylight-aware wallpapers

This optional enhancement keeps CleanYourTV's existing launcher, apps, dock,
input support and debloat tooling, but selects wallpaper *pictures* based on
local sunrise and sunset. It does not require the internet or FamilyOS.

## Wallpaper behaviour

- `morning-*`: 45 minutes before until 90 minutes after sunrise
- `day-*`: from morning's end until 90 minutes before sunset
- `sunset-*`: 90 minutes before until 45 minutes after sunset
- `night-*`: all remaining times
- Rotate within the matching image group every 45 minutes while Home is visible.
- Change groups on the minute tick, or immediately upon reopening Home.
- Missing groups use all available photos; existing shuffle/sequential themes
  remain unchanged.
- Coordinates in `launcher/app/src/main/res/values/config.xml` are set to
  Melbourne city. Change the two strings and rebuild for another region.
  Ensure the TV timezone is correct.

The `themes/family-time-of-day/wallpapers/` files are copies of the upstream
CleanYourTV scenes, grouped by filename. All photos stay on the TV once pushed.

## Safe rollout

1. Start with the Sony on a test build. Enable ADB only temporarily.
2. Read the upstream PROMPT.md; capture package lists, settings and backup
   state *outside this public repository*. Do not disable anything yet.
3. Build with `cd launcher && ./gradlew testReleaseUnitTest lintRelease assembleRelease`.
4. Install according to `launcher/build-and-install.sh`, with the rollback
   procedure ready. **Do not disable stock Home or setupwraith on the Sony
   X90J trial**: use the opt-in HomeKeyService workaround documented in
   [the device report](devices/sony-xr-65x90j-m-pa.md). Other models need
   their own HOME and casting dependency inspection.
5. Apply `TV_HOST=<TV_IP> tools/theme.sh themes/family-time-of-day`.
6. Check real remote buttons, HDMI, live TV, streaming apps and firmware updates.
   Reboot once. Turn off and revoke ADB debugging when finished.
7. Repeat the same theme on TCL only after its own device-specific inspection.
   Fire OS does not support this launcher takeover.

## Restore

- Theme only: `TV_HOST=<TV_IP> tools/theme.sh --reset`.
- Full launcher: follow CleanYourTV's documented restore, *re-enabling the
  original Home before removing the replacement launcher*.
- Never publicly commit device backups, IPs, tokens or environment snapshots.

## Planned weather upgrade (not included)

FamilyOS has an authenticated environment API backed by BOM. A future small
read-only LAN bridge can expose only a weather condition and timestamp, without
placing household credentials in the TV launcher. Offline daylight wallpapers
will continue to work when that bridge is unavailable.
