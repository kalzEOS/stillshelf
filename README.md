# StillShelf

StillShelf Android audiobook/music player for Audiobookshelf/Navidrome.

This project is heavily inspired by the iOS app Still.

It started from a personal need: there were not many Audiobookshelf frontend clients on Android, so this app was built to fill that gap. I also couldn’t find a good Navidrome frontend for my music, so support for it was added into the same app.

The goal is a smooth, practical Android experience for browsing, managing, and listening to your ABS  and Navidrome libraries.

Both m4b and mp3 formats are supported on the Audiobooks part. 

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
