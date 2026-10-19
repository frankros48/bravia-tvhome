# tvhome

A small home screen for an Android TV, plus scripts to debloat the TV safely over ADB.
Written from scratch for private use. It shares no code with any third-party repo.

## Why it is safe to run on your network

- **The only network code is `Weather.java`.** The manifest has one `uses-permission` line, `INTERNET`,
  used to fetch the weather over HTTPS from one fixed host (`api.open-meteo.com`), read-only, sending
  only city-level coordinates. Verify: `aapt2 dump permissions launcher/build/tvhome.apk` lists only
  INTERNET, and `grep -rl "java.net" launcher/src` matches only `Weather.java`.
- **No dependencies.** The APK holds our Java and nothing else. No AndroidX, no Gradle plugins,
  no downloaded libraries. The whole thing is a few hundred lines you can read in ten minutes.
- **The scripts use only Google's own `adb`** and talk to one address, the `TV_HOST` you set.
  No Python packages, no pip, no third-party ADB client.
- **Signing key is yours.** `build.sh` makes a key in `launcher/.signing/` the first time. It is
  git-ignored and never leaves your machine.
- **Nothing is uninstalled.** Packages are disabled with `pm disable-user`, and everything the
  scripts disable is logged so `tv-restore.sh` can undo it.
- **Core packages are protected.** `tv-disable.sh` refuses system essentials (settings, Play
  services, input methods, launchers) unless you set `FORCE=1`. Extend the list in
  `tools/common.sh` for anything your TV needs.

## Setup

On the TV: Settings > Device Preferences > About, press OK on **Build** seven times, go back,
open Developer options, turn on **USB debugging**. Keep the TV on, on the same Wi-Fi as your Mac.
Accept the "Allow USB debugging?" prompt the first time you connect.

On the Mac:

```bash
brew install android-platform-tools openjdk android-commandlinetools
sdkmanager "platforms;android-34" "build-tools;34.0.0"
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools   # adjust if different
export TV_HOST=192.168.1.50   # the TV's address under Settings > Network
```

If `adb connect` says "No route to host" on macOS, allow your terminal under System Settings >
Privacy & Security > Local Network.

## Use

```bash
tools/tv-probe.sh                 # what is this TV? (read-only)
tools/tv-backup.sh                # save package lists and settings to backup/ (read-only)
launcher/build.sh                 # build launcher/build/tvhome.apk
tools/tv-install.sh               # install it and make it Home (stock launcher stays enabled)
tools/tv-disable.sh lists/mine.txt   # disable the packages you listed
tools/tv-restore.sh               # undo all of it
```

Always run `tv-backup.sh` before installing or disabling anything. The install and disable
scripts refuse to run without it.

### Building a debloat list

There is no shipped list on purpose: every TV model differs, and a wrong guess can break inputs
or HDMI-CEC. After `tv-backup.sh`, open `backup/latest/packages-all.txt`, decide what to cut, and
put one package name per line in `lists/mine.txt`. Comments with `#` are fine. Use
`DRY_RUN=1 tools/tv-disable.sh lists/mine.txt` to preview. Disable a few at a time, use the TV
for a day, then do more.

### Customising the launcher

Edit `launcher/src/com/frank/tvhome/Config.java` (columns, pinned apps, hidden apps) and rebuild.
Long-press a tile to open that app's system page, where you can disable or uninstall it.

## If something goes wrong

- Run `tools/tv-restore.sh`. It re-enables everything the scripts disabled, re-enables the stock
  launcher and removes ours.
- If the TV is unreachable, use the TV's own Settings > Apps > See all apps > Show system apps to
  re-enable anything by hand. Nothing here is permanent.

## Layout

| Where | Contents |
|---|---|
| `launcher/src/com/frank/tvhome/` | The launcher: `HomeActivity`, `AppEntry`, `AppAdapter`, `Config` |
| `launcher/AndroidManifest.xml` | Declares the Home activity, no permissions |
| `launcher/build.sh` | SDK-only build and sign |
| `tools/` | `adb` scripts: probe, backup, install, disable, restore |
