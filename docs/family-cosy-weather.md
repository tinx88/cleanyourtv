# Family Edition: Cosy Home + FamilyOS weather

This is the original *Cosy Home* cottage, sky, hills, stars, chimney smoke and
slow clouds by [Oliver Roberts](https://github.com/oliverjwroberts/cosy-home-tv).
The scene renderer is MIT-licensed; see `docs/licenses/COSY-HOME-MIT.txt`.

Our **optional** `themes/family-cosy-weather` mode retains the same cottage
through dawn/day/dusk/night and draws extra effects based on the local FamilyOS
BOM weather bridge:

| Condition | Over the cottage |
| --- | --- |
| sunny | Original clear sky and drifting daytime clouds |
| cloudy | Cloud cover, slightly dimmer sky |
| rainy | Fine moving raindrops and cloud cover |
| stormy | Heavier rain, clouds and occasional subtle distant lightning |
| offline/invalid/stale | **Original Cosy Home** with no weather overlays |

Important: the weather condition comes from BOM's current **daily forecast**
icon/summary, not an instantaneous rain radar observation; it can indicate a
rainy day before the first raindrops arrive. Poll every five minutes while
Home is visible. All animations run locally and stop when leaving Home.

## Weather feed

The separate private FamilyOS repository contains
`tv-weather-bridge/tv_weather.py`, a minimal unauthenticated but IP-allowlisted
LAN adapter for the existing environment bridge. Its sole `GET /weather`
route exposes a non-sensitive summary (condition, temperature and timestamp),
**not** FamilyOS login details, tokens, UV/pollen, or user records.

- On the Docker VM's local checkout, set the `weather_url` key in
  `themes/family-cosy-weather/config.json` to
  `http://<DOCKER_VM_LAN_IP>:5056/weather`, then apply the theme. Leave the
  value blank in the public GitHub repo. The app only fetches private numeric
  IPv4 addresses on port 5056 and does not follow redirects.
- Keep port 5056 local to the LAN, do not forward it from the router, and limit
  access to the Sony's IP in the bridge config / firewall.
- Do **not** expose FamilyOS's authenticated `/environment/home` to the TV.
- The launcher has no access to the Docker filesystem. A dead bridge or
  failed HTTP request is normal, and immediately selects the offline scene.

## Apply only after building and installing a tested APK

```sh
TV_HOST=<TV_IP> tools/theme.sh themes/family-cosy-weather
```

The APK needs the `INTERNET` permission and local network security policy;
theme changes alone cannot add these to an older installed launcher.

**Keep `com.google.android.apps.tv.launcherx` and
`com.google.android.tungsten.setupwraith` enabled on this Sony.** Its Home
button takeover uses the opt-in HomeKeyService rather than disabling the
system components. See `docs/devices/sony-xr-65x90j-m-pa.md`.

To restore the old photographic daypart wallpaper:
```sh
TV_HOST=<TV_IP> tools/theme.sh themes/family-time-of-day
```

To restore launcher ownership to Google TV:
```sh
adb shell cmd package set-home-activity --user 0 \
  com.google.android.apps.tv.launcherx/.home.HomeActivity
```

Note: GitHub Actions produces independently debug-signed APKs. If an upgrade
is rejected because signatures do not match, hand the HOME role back to
Google TV **before** uninstalling the old app, then reinstall, reapply the
theme and enable HomeKeyService. Replacing the app is not required just to
adjust the theme.
