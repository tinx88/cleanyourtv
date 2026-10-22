# Family Edition: permanent APK signing

The GitHub Actions PR preview APK uses an **ephemeral debug key**. Do not install it as an update on either TV: each runner may have a different certificate, causing `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

Only use artifacts named **`family-edition-signed-apk`** for ongoing TV installations. These are signed using a persistent, private owner-held PKCS#12 key.

## One-time owner setup (Docker VM)

1. Update the checkout and run `bash tools/setup-family-signing.sh`. This requires OpenSSL and creates a new signing identity once. Re-running will **not** rotate an existing key.
2. Back up **both** `~/.config/cleanyourtv/signing/family-edition-release.p12` and `keystore-password.txt` to secure storage you control. Never place them in this public repository or a GitHub Actions artifact.
3. In the repository, open **Settings → Secrets and variables → Actions → New repository secret**. Create:
   - `FAMILY_EDITION_KEYSTORE_BASE64` — contents of `~/.config/cleanyourtv/signing/keystore-base64.txt`
   - `FAMILY_EDITION_KEYSTORE_PASSWORD` — contents of `~/.config/cleanyourtv/signing/keystore-password.txt`
   
   Copy directly into the GitHub secret fields. Do **not** paste either value into ChatGPT, a commit, an issue or a PR.
4. When both secrets exist, push a source change to `feature/family-dynamic-wallpapers` or `main` (or run the manual workflow after it is on the default branch). The **Signed Family Edition APK** workflow automatically builds/tests/signs on trusted pushes; missing secrets cause an explicit failure, not a silently re-signed debug APK.

The encrypted secrets live in GitHub Actions; the original key stays in your private Docker directory. GitHub repo connectors cannot configure those secrets for you.

## Build behaviour

- **CI / pull_request:** tests, lint, preview APK with throwaway debug signing. Artifact: `family-edition-preview-apk-ephemeral-signature`. Signing secrets are not passed to these builds.
- **Signed Family Edition APK / trusted push:** same tests and lint; restores the private PKCS#12 key into the ephemeral runner, builds with that key and checks APK Signature Scheme v2. Artifact: `family-edition-signed-apk`.
- **Local Gradle builds:** use disposable debug signing unless `FAMILY_EDITION_SIGNING_KEYSTORE` and `FAMILY_EDITION_SIGNING_PASSWORD` are provided. CI sets `FAMILY_EDITION_REQUIRE_STABLE_SIGNING=true` so a missing key cannot silently fall back to debug.

The signed workflow prints the **public certificate SHA-256 digest**. That digest should stay the same on every future signed build. Certificate digests aren't secret; the keystore and password are.

## First switch to the permanent key

An APK already installed from a previous GitHub Actions run was signed by that runner's private debug key. The new permanent key cannot reproduce it. **One final uninstall and reinstall is necessary for each TV currently on a different signature.** Back up each TV's `config.json`, theme images and on-TV preferences first. On Sony, restore stock Google TV as safe HOME fallback before uninstalling; never disable launcherx or setupwraith simply to make Home work. After installing the permanent-key APK, restore the theme, HOME role and required accessibility services as documented in `docs/devices/sony-xr-65x90j-m-pa.md`.

Subsequent builds signed with this same private key update normally using `adb install -r`, preserving app data, assuming the package name and version remain compatible. **Do not rotate or lose the signing key.**
