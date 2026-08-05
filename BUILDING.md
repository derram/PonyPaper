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