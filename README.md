# PonyPaper (modern fork)

A live wallpaper for Android using pixel-art sprites of characters from *My Little Pony: Friendship is Magic*.

This repository is a **grok build modernization fork** of the [Smithers888/PonyPaper](https://github.com/Smithers888/PonyPaper) project. Upstream Ant tooling and an ancient `targetSdk` no longer build or install cleanly on current Android. This fork targets a Gradle-based build and modern SDK levels so you can produce **debug/sideload APKs** without upstream signing keys.

### Features in this fork

- **Modern Gradle build** — builds and installs on current Android (minSdk 21); signed release APKs via GitHub/Gitea Actions
- **Installs alongside upstream** — application id `io.github.derram.ponypaper` (does not replace the original app)
- **Target frame rate** — prefer 30 / 60 / 90 / 120 FPS (default 30); motion is delta-time based so pony speed stays consistent
- **Battery Saver support** — optional respect for system Battery Saver (default on): cap at 25 FPS, at most 3 ponies, and solid-colour backgrounds instead of images
- **On-battery power profile** — optional prefs to force default FPS (30), default pony count (4), and/or disable image backgrounds while unplugged
- **Up to 20 ponies** on screen (was lower upstream)
- **Discrete gaits** — stroll, walk, and trot for more varied movement
- **Hold-to-drag** — press and hold a pony to drag it; uses a lowered/drag sprite while held
- **Optional screen saver** — can be enabled independantly of wallpaper, uses the same herd and settings; enable under system Display settings
- **Optional screensaver clock** — Everyday Clock–style large digital time (and optional date) drawn over the herd; 12/24-hour follows the system setting
- **Crash and stability fixes** — safer canvas handling, preference listener cleanup, hardened custom-pony import, sprite bitmap recycling
- **Waifu selector** — best pony should always come first

<img src='screenshots/screen1.png' width='180'> <img src='screenshots/screen2.png' width='180'> <img src='screenshots/screen3.png' width='180'> <img src='screenshots/preview.png' width='180'>  <img src='screenshots/preferences1.png' width='180'> <img src='screenshots/preferences2.png' width='180'> <img src='screenshots/screensaver.png' width='180'>

## Install (from Releases)

Prebuilt APKs are published on the [Releases](https://github.com/derram/PonyPaper/releases) page. Prefer the latest release asset named like `PonyPaper-<version>.apk`.

1. On your phone, open the release page in a browser and download the `.apk`.
2. Open the downloaded file (notification, Files app, or browser downloads).
3. If Android blocks the install, allow installs from that source when prompted (see [warnings below](#android-install-warnings)).
4. After install, set the wallpaper: long-press the home screen → **Wallpapers** → **Live wallpapers** → **Pony Paper**. Open settings from the wallpaper picker to toggle ponies, background, etc.
5. (Optional) Use as a **screen saver**: system **Settings → Display → Screen saver** (wording varies by OEM) → choose **Pony Paper**. From in-app settings you can also open **Screen saver settings**. The screensaver uses the same preferences as the wallpaper (ponies, FPS, background, etc.). Under **Screen saver** in-app you can optionally enable a large digital **clock** (and date) similar to Pixel’s Everyday Clock.

   **Tap or swipe** while dimmed brightens the screen and keeps the saver running; the same gesture exits only when already bright. **Back** always exits. Hold a pony to drag without dismissing. After brightening, the screen re-dims after ~30s of no interaction.

This fork uses application id `io.github.derram.ponypaper`, so it installs **alongside** the original upstream app (`uk.cpjsmith.ponypaper`) rather than replacing it. Updates only work when the new APK is signed with the **same** release key as the previous install of this fork.

### Android install warnings

Sideloading (installing an APK outside the Play Store) is normal for open-source apps distributed via GitHub. Android will still warn you — that is expected, not a sign that this project is broken.

| What you may see | What it means | What to do |
|------------------|---------------|------------|
| **Blocked by Play Protect** / “harmful app” scan | Play Protect flags many apps that are not on Play, especially uncommon package names | Tap **More details** → **Install anyway** (or **Scan app** first if you prefer). You can also disable the block temporarily under Play Store → profile → Play Protect |
| **For your security, your phone is not allowed to install unknown apps from this source** | Installs from the browser / Files are off by default | Tap **Settings** on the dialog and enable **Allow from this source** for that app only |
| **Package installer** / “Do you want to install this application?” | Normal confirmation for any sideloaded APK | Review the app name, then **Install** |
| **App not installed** after an update | Usually a different signing key, or a downgrade to an older `versionCode` | Uninstall the existing Pony Paper from this fork, then install the new APK (wallpaper settings for this id will be cleared) |
| Browser “file may be harmful” | Generic download warning for `.apk` files | Keep the file if you trust this repository’s release |

**Trust checklist:** download only from this repo’s [Releases](https://github.com/derram/PonyPaper/releases) (not third-party mirrors), confirm the asset name and tag look right, and prefer HTTPS on github.com. This project is not distributed via Google Play, so Play Protect cannot “verify” the developer the way store apps are verified.

Requirements: Android **5.0+** (`minSdk 21`). Live wallpapers must be supported on the device (almost all phones; some locked-down or TV builds may hide them).

## Build (debug)

Requirements:

- **Full JDK 17+** (not a JRE-only install — Android Gradle Plugin needs `jlink`)
- Android SDK with platform **35** and a recent build-tools package
- No release keystore needed for debug builds

```bash
# Point Gradle at your SDK (or copy local.properties.example)
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

# If `java` is only a JRE, point Gradle at a full JDK, e.g.:
# echo 'org.gradle.java.home=/path/to/jdk-17' >> gradle.properties

./gradlew :app:assembleDebug
```

APK output:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install on a device/emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then set the wallpaper: long-press home screen → Wallpapers → Live wallpapers → **Pony Paper**. Open settings from the wallpaper picker to toggle ponies, background image, etc.

## Release APKs

Release builds use `applicationId` `io.github.derram.ponypaper` (no `.debug` suffix) and must be **signed** to install on a device. Without signing config, Gradle produces `app-release-unsigned.apk`, which Android will reject.

### 1. Create a release keystore (once)

```bash
keytool -genkeypair -v \
  -keystore ponypaper-release.jks \
  -storetype JKS \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias ponypaper
```

Keep a backup of `ponypaper-release.jks` and the passwords. Losing them means users cannot upgrade over the same app id without uninstalling.

### 2. Local signed release

```bash
cp keystore.properties.example keystore.properties
# Edit keystore.properties: storeFile, passwords, alias

./gradlew :app:assembleRelease
```

Signed APK:

```
app/build/outputs/apk/release/app-release.apk
```

`keystore.properties`, `*.jks`, and `*.keystore` are gitignored.

### 3. Automated Releases (GitHub + Gitea)

The same release workflow lives in both places so either forge can run it:

| Path | Used by |
|------|---------|
| [`.github/workflows/release.yml`](.github/workflows/release.yml) | GitHub Actions |
| [`.gitea/workflows/release.yml`](.gitea/workflows/release.yml) | Gitea Actions |

Keep those two files identical when you edit the pipeline. On a version tag, CI builds a signed APK and attaches it to a Release on **whichever forge ran the job**.

**One-time: add the same Actions secrets on each host** that should publish:

| Secret | Value |
|--------|--------|
| `SIGNING_KEYSTORE_BASE64` | `base64 -w0 ponypaper-release.jks` (macOS: `base64 -i ponypaper-release.jks`) |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | e.g. `ponypaper` |
| `SIGNING_KEY_PASSWORD` | key password |

- **GitHub:** repo → Settings → Secrets and variables → Actions  
- **Gitea:** repo → Settings → Actions → Secrets (Actions must be enabled for the instance and repo)

`GITHUB_TOKEN` is provided automatically on both; the workflow uses it to create the release on that host’s API (`softprops/action-gh-release`).

#### Gitea runner checklist

1. Install and register [act_runner](https://docs.gitea.com/usage/actions/act-runner) against your Gitea instance.
2. Give the runner a label matching `runs-on` in the workflow (`ubuntu-latest`). Prefer a **full Ubuntu** Docker image or a host executor — thin `node`-only images often break JDK/Android SDK setup.
3. The runner needs outbound HTTPS to download Actions (from GitHub by default), Temurin JDK, and Android SDK packages. If the runner is air-gapped, mirror those actions onto Gitea and set `[actions].DEFAULT_ACTIONS_URL` (or use absolute `uses:` URLs).
4. Add the four `SIGNING_*` secrets on the Gitea repo (same values as GitHub if you want the same signing key).

#### Dual push (this repo’s usual setup)

If `origin` pushes to both Gitea and GitHub, a single tag push can trigger **both** pipelines and publish the APK on each forge’s Releases page:

```bash
# example multi-push remote (adjust URLs to match your remotes)
git remote set-url --add --push origin git@werkhorse.net:derram/PonyPaper.git
git remote set-url --add --push origin git@github.com:derram/PonyPaper.git
```

If Gitea is a **pull mirror** of GitHub only, tag events may not run Actions on Gitea the same way — prefer dual-push (or push the tag to Gitea explicitly) when you want both releases.

**Publish a release:**

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts` (every Play/sideload upgrade needs a higher `versionCode`).
2. Commit on `master` and push.
3. Tag and push the tag:

```bash
git tag v1.7.0-modern
git push origin v1.7.0-modern
```

4. Wait for the **Release APK** workflow on each forge that received the tag. It creates a Release named after `versionName` with asset `PonyPaper-<versionName>.apk`.

You can also run the workflow manually (**Actions → Release APK → Run workflow**); manual runs create a **draft** release so you can inspect the APK before publishing. On GitHub, auto-generated release notes are enabled; on Gitea they are skipped (API difference).

### 4. Manual upload (no CI)

```bash
./gradlew :app:assembleRelease
cp app/build/outputs/apk/release/app-release.apk "PonyPaper-1.7.0-modern.apk"
gh release create v1.7.0-modern "PonyPaper-1.7.0-modern.apk" --generate-notes
```

## Project layout

```
app/                 Android application module (Gradle)
  src/main/java/     Wallpaper + settings Java sources
  src/main/res/      Sprites, pony frame timings, preferences XML
custom/              Desktop custom-pony editor (unchanged, Ant/Java SE)
screenshots/         README images
```

The desktop editor under `custom/` is separate from the Android build.

## Original features

- Compatible in spirit with [Desktop Ponies](https://github.com/RoosterDragon/Desktop-Ponies), with a smaller pony set and fewer features.
- Enable/disable individual ponies; a few appear at once and rotate on/off screen.
- Optional custom ponies (see [custom/README.md](custom/README.md)).
- Optional background image, auto-pixellated to match the sprites.
- Drag ponies with touch (enabled in this fork).

## Licensing / credits

All artwork was created by contributors to the Desktop Ponies team ([DeviantArt](http://desktop-pony-team.deviantart.com/), [source](https://github.com/RoosterDragon/Desktop-Ponies)). Artwork and original source are licensed under [CC BY-NC-SA 3.0](http://creativecommons.org/licenses/by-nc-sa/3.0/).

Original Android source: [Smithers888](http://cpjsmith.uk) / [Smithers888/PonyPaper](https://github.com/Smithers888/PonyPaper).

You may share and modify this project under the same terms: credit, non-commercial use, and share-alike.
