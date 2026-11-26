# StillShelf

StillShelf Android audiobook/music player for Audiobookshelf/Navidrome.

This project is heavily inspired by the iOS app Still.

It started from a personal need: there were not many Audiobookshelf frontend clients on Android, so this app was built to fill that gap. I also couldn’t find a good Navidrome frontend for my music, so support for it was added into the same app.

The goal is a smooth, practical Android experience for browsing, managing, and listening to your ABS  and Navidrome libraries.

Both m4b and mp3 formats are supported on the Audiobooks part. 

## Download and install

<p align="center">
  <a href="https://kalzeos.github.io/stillshelf-fdroid/repo/?fingerprint=416566D6E30D37FA5D0EB8875D075CEF7B03AC5F5750A387FF669CFA3DF5CDD0"><img src="docs/fdroid-banner.svg" width="340" alt="Available via F-Droid, StillShelf repository" /></a>
</p>

### F-Droid

Install StillShelf through its developer-maintained F-Droid repository. This is a separate repository from the official F-Droid catalog.

<p align="center">
  <a href="https://kalzeos.github.io/stillshelf-fdroid/repo/?fingerprint=416566D6E30D37FA5D0EB8875D075CEF7B03AC5F5750A387FF669CFA3DF5CDD0"><img src="docs/fdroid-qr.svg" width="260" alt="Scan to add the StillShelf F-Droid repository" /></a>
</p>

1. Install [F-Droid](https://f-droid.org/) if you do not already have it.
2. [Add the StillShelf repository](https://kalzeos.github.io/stillshelf-fdroid/repo/?fingerprint=416566D6E30D37FA5D0EB8875D075CEF7B03AC5F5750A387FF669CFA3DF5CDD0) or scan the QR code above. Alternatively, open F-Droid settings, select Repositories and add the following address:

   ```text
   https://kalzeos.github.io/stillshelf-fdroid/repo/
   ```

3. Confirm the repository fingerprint, refresh the repository list and search for StillShelf.
4. Install StillShelf. Future updates are managed by F-Droid.

Repository SHA-256 fingerprint:

```text
416566D6E30D37FA5D0EB8875D075CEF7B03AC5F5750A387FF669CFA3DF5CDD0
```

### GitHub

Prefer direct APK downloads and the built-in updater? Download the regular APK from [GitHub Releases](https://github.com/kalzEOS/stillshelf/releases/latest).

Both distributions use the same package name and developer signing key. You can switch between them by installing a compatible version over your existing installation, without uninstalling or clearing your data. The F-Droid ZIP release asset is intended for repository publishing; F-Droid users do not need to extract it manually.

## Maintenance Status
​StillShelf is stable and feature-complete! I use this app daily, so it is actively maintained for bugs, API breaks, and Android OS compatibility, even if there are long gaps between commits. Feel free to open an issue if you encounter any bugs.

## Building

StillShelf can be built directly from source using Gradle.

Requirements:
- Java 11 or newer
- Android SDK

Build the standard GitHub release APK with:

```bash
./gradlew assembleGithubRelease
```

Build the F-Droid flavor with:

```bash
./gradlew assembleFdroidRelease
```

The generated APKs will be located at:

- `app/build/outputs/apk/github/release/`
- `app/build/outputs/apk/fdroid/release/`

## Screenshots

### Audiobooks

<p align="center">
  <img src="docs/screenshots/home.png" alt="StillShelf screenshot 1" width="240" />
  <img src="docs/screenshots/Home2.png" alt="StillShelf screenshot 2" width="240" />
  <img src="docs/screenshots/Screenshot_20260306_181125.png" alt="StillShelf screenshot 3" width="240" />
</p>
<p align="center">
  <img src="docs/screenshots/Screenshot_20260306_181141.png" alt="StillShelf screenshot 4" width="240" />
  <img src="docs/screenshots/Screenshot_20260306_181203.png" alt="StillShelf screenshot 5" width="240" />
  <img src="docs/screenshots/Screenshot_20260306_181220.png" alt="StillShelf screenshot 6" width="240" />
</p>
<p align="center">
  <img src="docs/screenshots/Screenshot_20260306_181237.png" alt="StillShelf screenshot 7" width="240" />
  <img src="docs/screenshots/Screenshot_20260306_181245.png" alt="StillShelf screenshot 8" width="240" />
  <img src="docs/screenshots/Screenshot_20260306_181311.png" alt="StillShelf screenshot 9" width="240" />
</p>
<p align="center">
  <img src="docs/screenshots/Screenshot_20260306_181351.png" alt="StillShelf screenshot 10" width="240" />
  <img src="docs/screenshots/Screenshot_20260306_181402.png" alt="StillShelf screenshot 11" width="240" />
  <img src="docs/screenshots/Screenshot_20260306_181422.png" alt="StillShelf screenshot 12" width="240" />
</p>
<p align="center">
  <img src="docs/screenshots/Screenshot_20260306_181435.png" alt="StillShelf screenshot 13" width="240" />
</p>

### Navidrome

<p align="center">
  <img src="docs/screenshots/nv_Screenshot_20260408_162925.png" alt="StillShelf Navidrome screenshot 1" width="240" />
  <img src="docs/screenshots/nv_Screenshot_20260408_162935.png" alt="StillShelf Navidrome screenshot 2" width="240" />
  <img src="docs/screenshots/nv_Screenshot_20260408_162948.png" alt="StillShelf Navidrome screenshot 3" width="240" />
</p>
<p align="center">
  <img src="docs/screenshots/nv_Screenshot_20260408_163014.png" alt="StillShelf Navidrome screenshot 4" width="240" />
  <img src="docs/screenshots/nv_Screenshot_20260408_163022.png" alt="StillShelf Navidrome screenshot 5" width="240" />
  <img src="docs/screenshots/nv_Screenshot_20260408_163034.png" alt="StillShelf Navidrome screenshot 6" width="240" />
</p>
<p align="center">
  <img src="docs/screenshots/nv_Screenshot_20260408_163056.png" alt="StillShelf Navidrome screenshot 7" width="240" />
  <img src="docs/screenshots/nv_Screenshot_20260408_163112.png" alt="StillShelf Navidrome screenshot 8" width="240" />
  <img src="docs/screenshots/nv_Screenshot_20260408_163126.png" alt="StillShelf Navidrome screenshot 9" width="240" />
</p>
<p align="center">
  <img src="docs/screenshots/nv_Screenshot_20260408_163149.png" alt="StillShelf Navidrome screenshot 10" width="240" />
  <img src="docs/screenshots/nv_Screenshot_20260408_163208.png" alt="StillShelf Navidrome screenshot 11" width="240" />
  <img src="docs/screenshots/nv_Screenshot_20260408_163240.png" alt="StillShelf Navidrome screenshot 12" width="240" />
</p>
<p align="center">
  <img src="docs/screenshots/nv_Screenshot_20260408_163250.png" alt="StillShelf Navidrome screenshot 13" width="240" />
  <img src="docs/screenshots/nv_Screenshot_20260408_163313.png" alt="StillShelf Navidrome screenshot 14" width="240" />
  <img src="docs/screenshots/nv_Screenshot_20260408_163331.png" alt="StillShelf Navidrome screenshot 15" width="240" />
</p>
<p align="center">
  <img src="docs/screenshots/nv_Screenshot_20260408_163406.png" alt="StillShelf Navidrome screenshot 16" width="240" />
</p>

## License

GPL-3.0-only. See [LICENSE](LICENSE).
