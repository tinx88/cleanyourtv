# Sony XR-65X90J (Australia, BRAVIA VH2) — Family Edition Home takeover

**Status:** Single-device hands-on trial in October 2026. Not a universal Sony
debloat recommendation or a long-term stability certification.

## Device and precautions

- Sony XR-65X90J, `BRAVIA_VH2_M_PA`, Android 12.
- Firmware fingerprint included `STT2.230505.001.S136/682241`.
- Stock Google TV Home: `com.google.android.apps.tv.launcherx/.home.HomeActivity`.
- Sony Live TV: `com.sony.dtv.tvx`.
- Existing TV configuration and package-state backup kept **privately** on the
  operator's Docker VM; do not publish IPs, backups or credentials.
- Google TV Home and `com.google.android.tungsten.setupwraith` must remain
  enabled in this trial to preserve recovery and possible Cast authentication.

## Why `set-home-activity` alone was insufficient

`cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME`
reported:

| Priority | Activity |
| ---: | --- |
| 2 | `com.google.android.apps.tv.launcherx/.home.HomeActivity` |
| 1 | `com.google.android.tungsten.setupwraith/.RecoveryActivity` |
| 0 | `com.gathin.tvhome/.MainActivity` |
| -1000 | `com.android.tv.settings/.system.FallbackHome` |

`cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity`
printed `Success`, but `resolve-activity` still selected launcherx.
`cmd role get-role-holders android.app.role.HOME` was not supported; instead
`dumpsys role` showed `name=android.app.role.HOME` with
`holders=com.gathin.tvhome`.

Attempting `pm disable-user --user 0
com.google.android.tungsten.setupwraith/.RecoveryActivity` failed with
`SecurityException: Shell cannot change component state`.
The setup wizard also exposes
`com.google.android.libraries.tv.setupwraith.libs.castinfo.CastInfoProvider`.
`dumpsys wifi` reported saved network ownership as
`cname=android.uid.system:1000`, not setupwraith; that does **not** eliminate
the separate Cast-credential dependency risk.

## Opt-in workaround

Based on the separately tested community implementation
[Gathin23/cleanyourtv PR #698](https://github.com/Gathin23/cleanyourtv/pull/698)
(from `moverperfect/cleanyourtv`), the Family Edition branch includes an
opt-in `take_over_home=true` build-time flag and an updated
`HomeKeyService` accessibility service:

- Keep launcherx and setupwraith enabled.
- Assign the HOME role to `com.gathin.tvhome`.
- Enable `com.gathin.tvhome/com.gathin.tvhome.HomeKeyService`, **preserving**
  any existing accessibility services.
- The service intercepts the physical HOME key and observes only window-state
  changes, not screen content. It covers the stock Home when it is displayed.
- Our fork **removed** PR #698's unconditional early-boot redirect, because
  the service could otherwise appear over an app/stream when initially bound.
  It therefore does not promise to open Family Edition automatically on boot.
- This workaround is not merged upstream as of this trial and is not guaranteed
  on other Sony builds.

## Observations on the TV

Verified by the operator **before reboot**:

- Family Edition APK manually installed and activity launched (`Status: ok`).
- `family-time-of-day` theme applied; photo displayed on TV.
  The 45-minute rotation/daypart switching has not yet been independently
  observed over its full cycle.
- HOME role assigned to Family Edition, and HomeKeyService configured/enabled.
- Physical HOME returned from Prime Video to Family Edition.
- Casting from a phone to the Sony worked.
- HDMI input selector, HDMI 3 with PS5, Live TV selection, Settings, Wi-Fi
  button and dedicated remote app shortcuts worked.

Verified **after reboot**:

- Google TV Home appeared briefly during startup, after which Sony resumed
  the last selected input (HDMI 3 or Live TV).
- Pressing the physical HOME button returned to Family Edition.

**Not yet confirmed after reboot:** fresh Cast authentication, a cold power-cycle,
multi-day stability, and complete daypart wallpaper transitions. Do not
describe these as passed.

## Revert just the HOME takeover

With ADB connected and launcherx still enabled:

```sh
adb shell cmd package set-home-activity --user 0 \
  com.google.android.apps.tv.launcherx/.home.HomeActivity
adb shell dumpsys role
```

The takeover logic checks that Family Edition holds the HOME role; handing the
role back to Google TV turns off that redirect. If needed, disable the
Family Edition HomeKeyService in TV Accessibility settings, without disabling
unrelated accessibility services. **Do not** disable setupwraith or launcherx
as a shortcut for this device.

Theme reset (from the repository checkout):

```sh
TV_HOST=<tv-address> tools/theme.sh --reset
```

APK upgrades built by separate GitHub Actions runs may have different debug
signing keys. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` requires handing the HOME
role back to stock **before** replacing the APK; uninstalling the old app also
removes its settings/theme files, which must then be reapplied.

## Related issues

- [#500: setupwraith RecoveryActivity and Cast provider](https://github.com/Gathin23/cleanyourtv/issues/500)
- [#361: Cast authentication failure with setupwraith disabled](https://github.com/Gathin23/cleanyourtv/issues/361)
- [#698: tested opt-in Home-button accessibility workaround](https://github.com/Gathin23/cleanyourtv/pull/698)
