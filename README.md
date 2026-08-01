# PonyPaper (modern fork)

A live wallpaper for Android using pixel-art sprites of characters from *My Little Pony: Friendship is Magic*.

This repository is a **grok build modernization fork** of the [Smithers888/PonyPaper](https://github.com/Smithers888/PonyPaper) project. Upstream Ant tooling and an ancient `targetSdk` no longer build or install cleanly on current Android. This fork targets a Gradle-based build and modern SDK levels so you can produce **debug/sideload APKs** without upstream signing keys.

<img src='screenshots/screen1.png' width='180'> <img src='screenshots/screen2.png' width='180'> <img src='screenshots/screen3.png' width='180'> <img src='screenshots/preview.png' width='180'>  <img src='screenshots/preferences.png' width='180'>

## Install (from Releases)

Prebuilt APKs are published on the [Releases](https://github.com/derram/PonyPaper/releases) page. Prefer the latest release asset named like `PonyPaper-<version>.apk`.

1. On your phone, open the release page in a browser and download the `.apk`.
2. Open the downloaded file (notification, Files app, or browser downloads).
3. If Android blocks the install, allow installs from that source when prompted (see [warnings below](#android-install-warnings)).
4. After install, set the wallpaper: long-press the home screen → **Wallpapers** → **Live wallpapers** → **Pony Paper**. Open settings from the wallpaper picker to toggle ponies, background, etc.

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

### 3. GitHub Releases (automated)

This repo includes [`.github/workflows/release.yml`](.github/workflows/release.yml). On a version tag, CI builds a signed APK and attaches it to a GitHub Release.

**One-time: add Actions secrets** (repo → Settings → Secrets and variables → Actions):

| Secret | Value |
|--------|--------|
| `SIGNING_KEYSTORE_BASE64` | `base64 -w0 ponypaper-release.jks` (macOS: `base64 -i ponypaper-release.jks`) |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | e.g. `ponypaper` |
| `SIGNING_KEY_PASSWORD` | key password |

**Publish a release:**

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts` (every Play/sideload upgrade needs a higher `versionCode`).
2. Commit on `master` and push.
3. Tag and push the tag:

```bash
git tag v1.7.0-modern
git push origin v1.7.0-modern
```

4. Wait for the **Release APK** workflow. It creates a GitHub Release named after `versionName` with asset `PonyPaper-<versionName>.apk`.

You can also run the workflow manually (**Actions → Release APK → Run workflow**); manual runs create a **draft** release so you can inspect the APK before publishing.

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
