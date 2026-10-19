# Plain-English setup guide

This turns a Sony BRAVIA (Google TV / Android TV) into a clean home screen with a rotating
wallpaper, weather, your favorite apps, and an art-frame style idle screen. You do not need to be
a programmer, but you do need to be comfortable copying and pasting a few lines into Terminal.

**You need:** a Mac, the TV on the same Wi-Fi or Ethernet network, and about 30 minutes.
**Is it safe?** Nothing is deleted. You can undo everything with one command (see "Go back").

## Step 1. Turn on a setting on the TV

1. On the TV go to **Settings > Device Preferences > About**.
2. Click **Build** (OK on the remote) **7 times**. A message says you are now a developer.
3. Press Back, open **Developer options**, and turn on **USB debugging** (on newer models it may be
   called **Network debugging** or **Wireless debugging**).
4. Find the TV's address: **Settings > Network & Internet**, open your connection, and write down the
   **IP address** (looks like `192.168.1.50`).

## Step 2. Install the tools on your Mac

1. Install Homebrew if you don't have it: https://brew.sh (copy its one-line install command).
2. Open **Terminal** (press Cmd+Space, type Terminal, press Enter) and paste these lines, one block at a time:

```bash
brew install android-platform-tools openjdk android-commandlinetools
yes | sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

3. Download this project: on the GitHub page click the green **Code** button, then **Download ZIP**,
   and unzip it. In Terminal type `cd ` (with a space), drag the unzipped folder onto the Terminal
   window, and press Enter.
4. Tell Terminal where things are (replace the numbers with your TV's address from Step 1):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk PATH=/opt/homebrew/opt/openjdk/bin:$PATH
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export TV_HOST=192.168.1.50
```

If the Mac asks to allow Terminal to find devices on your local network, click **Allow**.

## Step 3. Make a backup (required)

```bash
tools/tv-backup.sh
```

The first time, the TV shows **Allow USB debugging?** Choose **Always allow** and run it again.
The backup is saved in a `backup` folder. The install step will not run without it.

## Step 4. Choose your settings (optional)

Open `launcher/src/com/frank/tvhome/Config.java` in any text editor. The things you will most
likely change are near the top and have a comment above each:

- `FAMILY_NAME`: the name shown under "Good evening".
- `LATITUDE`, `LONGITUDE`, `WEATHER_PLACE`: your location, for weather and sunrise/sunset.
- `PINNED`: the apps in your Favorites row.
- `IDLE_TIMEOUT_MS` and `IDLE_DIM`: when the idle screen starts and how dim it is.
- `COUNTDOWNS`: birthdays to show a month ahead. Add a line like `new Countdown("Mom", 3, 14, 1960),`.

## Step 5. Add your wallpapers (optional)

Make a folder called `wallpapers` **next to** the project folder (the same folder that contains
`tvhome`... or set `WALLPAPER_DIR=/path/to/your/pictures` before building) and put JPG or PNG
pictures in it. They are shrunk automatically. Without any pictures you get a plain dark background.

## Step 6. Build and install

```bash
launcher/build.sh
tools/tv-install.sh
```

Press **Home** on the TV remote. Done. If the TV asks which Home app to use, pick **tvhome** and
choose **Always**.

## Using it

- **Left/Right/Up/Down** move around. The top bar switches between Apps, Home, the app store,
  Settings and Inputs.
- **Hold OK on a favorite** to move it with Left/Right, press OK to drop it. **Hold OK in the Apps
  list** to add or remove a favorite.
- After a couple of minutes with no button presses the screen shows only the dimmed wallpaper.
  Press any button to wake it.
- Colored remote buttons open Netflix (red), Hulu (green), YouTube (yellow) and HBO Max (blue) if
  those apps are installed.

## Go back to how it was

```bash
tools/tv-restore.sh
```

This removes the launcher and turns back on everything the scripts switched off. (Optional
"debloat" lists are in `lists/`; they only *disable* apps, never delete them, and you preview with
`DRY_RUN=1 tools/tv-disable.sh lists/mine.txt` first. Skip them if you're unsure.)

## It didn't work

| What you see | What to do |
|---|---|
| `No route to host` or `failed to connect` | Check the IP address, make sure the TV is on, and allow Terminal under System Settings > Privacy & Security > **Local Network**. |
| `unauthorized` | Look at the TV for the **Allow USB debugging?** box and accept it. |
| `command not found: sdkmanager` or `adb` | Re-run Step 2, then open a new Terminal window. |
| Build says `Set ANDROID_HOME` | Re-run the three `export` lines in Step 2. |
| Home button opens the old screen | In TV Settings > Device Preferences > Home screen, pick tvhome. |
