package com.frank.tvhome;

/** Everything you might want to tweak lives here. Rebuild after editing. */
final class Config {
    private Config() {}

    /** Tiles per row in the Favorites banner row. */
    static final int FAVORITE_COLUMNS = 7;

    /** Tiles per row in the All apps grid. */
    static final int ALL_APPS_COLUMNS = 7;

    /** How long each wallpaper stays up, in milliseconds. */
    static final long WALLPAPER_INTERVAL_MS = 5 * 60 * 1000L;

    /** Crossfade length between wallpapers, in milliseconds. */
    static final long WALLPAPER_FADE_MS = 1500L;

    /** Darkness laid over the wallpaper: 0 = none, 1 = black. */
    static final float OVERLAY_DARKNESS = 0.25f;

    /** Text turns dark when the wallpaper behind it is brighter than this (0 to 1, after the overlay). */
    static final float DARK_TEXT_ABOVE = 0.42f;

    /** Background used when the APK holds no wallpapers. */
    static final int FALLBACK_BACKGROUND = 0xFF08080A;

    /**
     * Favorites, shown first as big banners, in this order. Package names that are not installed
     * are ignored. These are common Android TV package names; confirm yours with tools/tv-backup.sh
     * (packages-all.txt) before relying on them.
     */
    static final String[] PINNED = {
            "com.netflix.ninja",                      // Netflix
            "com.wbd.stream",                         // HBO Max
            "com.amazon.amazonvideo.livingroom",      // Prime Video
            "com.google.android.youtube.tv",          // YouTube
            "com.google.android.youtube.tvunplugged", // YouTube TV
            "com.hulu.livingroomplus",                // Hulu
            "com.aetn.history.watch.androidtv",       // History
    };

    /** Packages that never get a tile. */
    static final String[] HIDDEN = {
    };

    /** Where the TV is (your city). Used for sunrise/sunset and (rounded to the city) for the weather request. */
    static final double LATITUDE = 33.7490;
    static final double LONGITUDE = -84.3880;

    /** Idle minutes on Home before the wallpaper goes full screen, dimmed like an art frame. */
    static final long IDLE_TIMEOUT_MS = 2 * 60 * 1000L;

    /** Extra black over the wallpaper while idle (0 to 1); higher = dimmer, more like a hung picture. */
    static final float IDLE_DIM = 0.35f;

    /** Packages for the remote's color buttons, in order: red, green, yellow, blue. */
    static final String[] COLOR_KEY_APPS = {
            "com.netflix.ninja",                  // red: Netflix
            "com.hulu.livingroomplus",            // green: Hulu
            "com.google.android.youtube.tv",      // yellow: YouTube
            "com.wbd.stream",                     // blue: HBO Max
    };

    /** Soft tick when focus moves between tiles. Volume 0 to 1; set TICK_ENABLED false to mute. */
    static final boolean TICK_ENABLED = true;
    static final float TICK_VOLUME = 0.25f;

    /** Greeting line: "Good evening" above this name. */
    static final String FAMILY_NAME = "Home";

    /** Dates that appear quietly under the date, only in the last COUNTDOWN_SHOW_DAYS days before. */
    static final int COUNTDOWN_SHOW_DAYS = 30;
    static final Countdown[] COUNTDOWNS = {
            // Birthdays: new Countdown("Name", month, day, birthYear),
            new Countdown("Christmas", Countdown.CHRISTMAS, 0, 0),
            new Countdown("Thanksgiving", Countdown.THANKSGIVING, 0, 0),
    };

    /** Night mode: from this hour to the end hour the home screen is softened (24-hour clock). */
    static final int NIGHT_START_HOUR = 21;
    static final int NIGHT_END_HOUR = 6;
    static final float NIGHT_DIM = 0.72f;

    /** Shown at the start of the weather bar. */
    static final String WEATHER_PLACE = "Atlanta, GA";

    /** How often the forecast is refreshed. */
    static final long WEATHER_REFRESH_MS = 30 * 60 * 1000L;
}
