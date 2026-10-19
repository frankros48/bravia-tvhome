package com.frank.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * The whole home screen: rotating wallpaper, a top tab bar (Apps, Home, App Store, Settings, Inputs),
 * compact weather top left, a small clock top right, a greeting, and a Favorites row. The full app
 * list lives on the Apps tab. Network access is limited to Weather.java.
 */
public class HomeActivity extends Activity {
    private static final String WALLPAPER_DIR = "wallpapers";
    private static final String PLAY_STORE = "com.android.vending";

    private float dp;
    private FrameLayout frame;
    private LinearLayout root;
    private View overlay;

    // Pages
    private LinearLayout homePage;
    private ScrollView appsPage;
    private LinearLayout appsContent;
    private LinearLayout favRow;
    private FrameLayout pageContainer;
    private TextView allAppsLabel;
    private FrameLayout tabsFrame;
    private LinearLayout tabsRow;
    private GlassPill glass;
    private final List<TextView> tabList = new ArrayList<>();
    private boolean glassShown;
    private boolean homeShown = true;
    private Tile firstTile;

    // Hero and top bar
    private TextView greeting;
    private TextView sunLine;
    private TextView upcoming;
    private final List<TextView> labelViews = new ArrayList<>();
    private int labelInk = Color.WHITE;
    private android.animation.ValueAnimator inkAnim;
    private WeatherIcon weatherIcon;
    private TextView weatherTemp;
    private TextView weatherSummary;
    private TextView weatherRange;

    // Apps, favorites, moving
    private List<AppEntry> allApps = new ArrayList<>();
    private List<String> favoriteOrder = new ArrayList<>();
    private String movingPkg;

    // Idle screen, sound
    private FrameLayout idleLayer;
    private boolean idle;
    private SoundPool sounds;
    private int tickId;
    private boolean quiet = true; // no tick while the screen is being built

    // Wallpaper state. "base" sits underneath; "fade" is faded in on top, then swapped down.
    private ImageView base;
    private ImageView fade;
    private Bitmap baseBitmap;
    private Bitmap fadeBitmap;
    private List<String> wallpapers = new ArrayList<>();
    private int wallpaperIndex = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable nextWallpaper = this::showNextWallpaper;
    private final Runnable weatherTick = this::refreshWeather;
    private final Runnable idleTick = this::enterIdle;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setBackgroundDrawable(new ColorDrawable(Config.FALLBACK_BACKGROUND));

        frame = new FrameLayout(this);
        frame.setBackgroundColor(Config.FALLBACK_BACKGROUND);
        base = backdrop();
        fade = backdrop();
        fade.setAlpha(0f);
        overlay = new View(this);
        overlay.setBackgroundColor(Color.argb(Math.round(255 * Config.OVERLAY_DARKNESS), 0, 0, 0));
        frame.addView(base);
        frame.addView(fade);
        frame.addView(overlay);

        try {
            String[] found = getAssets().list(WALLPAPER_DIR);
            if (found != null) {
                wallpapers = new ArrayList<>(Arrays.asList(found));
                Collections.shuffle(wallpapers);
            }
        } catch (IOException e) {
            wallpapers.clear();
        }
        if (wallpapers.isEmpty()) overlay.setVisibility(View.GONE);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(28), px(16), px(28), px(12));
        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        pageContainer = new FrameLayout(this);
        pageContainer.setClipChildren(false);
        buildHomePage();
        buildAppsPage();
        pageContainer.addView(homePage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pageContainer.addView(appsPage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(pageContainer, pageParams);

        frame.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        buildIdleLayer(frame);
        setContentView(frame);
        setPage(true);
        loadTick();
    }

    @Override
    protected void onDestroy() {
        if (sounds != null) sounds.release();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload on every return so installs and removals show up without a restart.
        quiet = true;
        allApps = AppEntry.load(this);
        loadFavorites();
        buildFavorites();
        buildAllApps();
        updateInfo();
        refreshWeather();
        if (getCurrentFocus() == null && firstTile != null) firstTile.requestFocus();
        root.post(() -> quiet = false);
        resetIdle();
        if (!wallpapers.isEmpty()) {
            if (baseBitmap == null) showNextWallpaper();
            else handler.postDelayed(nextWallpaper, Config.WALLPAPER_INTERVAL_MS);
        }
    }

    @Override
    protected void onPause() {
        // Stop rotating while an app is in front; finish any half-done fade cleanly.
        handler.removeCallbacks(nextWallpaper);
        handler.removeCallbacks(weatherTick);
        handler.removeCallbacks(idleTick);
        movingPkg = null;
        exitIdle();
        fade.animate().cancel();
        finishFade();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Pressing Home while already here goes back to the Home page and its first tile.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            setPage(true);
            if (firstTile != null) firstTile.requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        // This is the home screen: Back only leaves the Apps page.
        if (!homeShown) setPage(true);
    }

    // ---- top bar ---------------------------------------------------------------------------

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        // Left slot: weather pill with the sun line underneath. It is as wide as the right slot,
        // so the tab bar in the middle stays centered and never touches the weather.
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(buildWeatherPill(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        sunLine = text(11f, 0xFFD6D8E0, "sans-serif");
        sunLine.setShadowLayer(px(4), 0, px(1), 0xAA000000);
        sunLine.setPadding(px(10), px(5), 0, 0);
        left.addView(sunLine);
        bar.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        bar.addView(buildTabs(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.END | Gravity.TOP);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(22f);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setShadowLayer(px(5), 0, px(1), 0xAA000000);
        clock.setPadding(0, px(6), px(4), 0);
        right.addView(clock);
        bar.addView(right, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    private View buildWeatherPill() {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(px(10), px(4), px(14), px(4));
        GradientDrawable bg = rounded(0x77000000, 30);
        bg.setStroke(Math.max(1, px(1)), 0x33FFFFFF);
        pill.setBackground(bg);

        weatherIcon = new WeatherIcon(this);
        weatherIcon.setWeather(3, true);
        pill.addView(weatherIcon, new LinearLayout.LayoutParams(px(26), px(26)));

        weatherTemp = text(20f, Color.WHITE, "sans-serif-light");
        weatherTemp.setText("--°");
        LinearLayout.LayoutParams tempLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tempLp.leftMargin = px(6);
        pill.addView(weatherTemp, tempLp);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        weatherSummary = text(11f, Color.WHITE, "sans-serif-medium");
        weatherSummary.setText("Loading weather");
        weatherRange = text(11f, 0xFFD0D4DE, "sans-serif");
        words.addView(weatherSummary);
        words.addView(weatherRange);
        LinearLayout.LayoutParams wordsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wordsLp.leftMargin = px(8);
        pill.addView(words, wordsLp);
        return pill;
    }

    private View buildTabs() {
        tabsFrame = new FrameLayout(this);
        tabsFrame.setBackground(rounded(0x30FFFFFF, 30));

        glass = new GlassPill(this, dp);
        glass.setAlpha(0f);
        tabsFrame.addView(glass, new FrameLayout.LayoutParams(px(60), px(30)));

        tabsRow = new LinearLayout(this);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setPadding(px(3), px(3), px(3), px(3));
        tabsRow.addView(tab("Apps", v -> setPage(false)));
        tabsRow.addView(tab("Home", v -> setPage(true)));
        tabsRow.addView(tab("App Store", v -> openPlayStore()));
        tabsRow.addView(tab("Settings", v -> startSafely(new Intent(Settings.ACTION_SETTINGS))));
        tabsRow.addView(tab("Inputs", v -> showInputPicker()));
        tabsFrame.addView(tabsRow, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return tabsFrame;
    }

    private TextView tab(String name, final View.OnClickListener onClick) {
        final TextView t = text(14f, 0xDDFFFFFF, "sans-serif-medium");
        t.setText(name);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(12), px(6), px(12), px(6));
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnClickListener(v -> {
            squishGlass();
            onClick.onClick(v);
        });
        t.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                tick();
                glideGlassTo(t);
            } else {
                // If focus left the tab bar altogether, let the glass fade away.
                root.post(() -> {
                    View now = getCurrentFocus();
                    if (now == null || !tabList.contains(now)) hideGlass();
                });
            }
            t.setTextColor(focused ? Color.WHITE : 0xDDFFFFFF);
        });
        tabList.add(t);
        return t;
    }

    /** The glass slides to the focused tab, stretching on the way and settling with a small overshoot. */
    private void glideGlassTo(TextView tab) {
        final float toX = tabsRow.getLeft() + tab.getLeft();
        final float toY = tabsRow.getTop() + tab.getTop();
        final int toW = tab.getWidth();
        final int toH = tab.getHeight();
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) glass.getLayoutParams();
        glass.animate().cancel();
        if (!glassShown) { // first appearance: drop in softly where the tab is
            lp.width = toW;
            lp.height = toH;
            glass.setLayoutParams(lp);
            glass.setTranslationX(toX);
            glass.setTranslationY(toY);
            glass.setScaleX(0.88f);
            glass.setScaleY(0.88f);
            glass.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
            glassShown = true;
            return;
        }
        final float fromX = glass.getTranslationX();
        final int fromW = lp.width;
        android.animation.ValueAnimator slide = android.animation.ValueAnimator.ofFloat(0f, 1f);
        slide.setDuration(420);
        slide.setInterpolator(new android.view.animation.OvershootInterpolator(1.25f));
        slide.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            glass.setTranslationX(fromX + (toX - fromX) * f);
            lp.width = Math.round(fromW + (toW - fromW) * f);
            lp.height = toH;
            glass.setLayoutParams(lp);
        });
        glass.setTranslationY(toY);
        glass.animate().alpha(1f).setDuration(120).start();
        slide.start();
        // A little squash as it travels, like a drop of liquid.
        glass.setScaleY(1.14f);
        glass.animate().scaleY(1f).setDuration(420)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
    }

    private void hideGlass() {
        glassShown = false;
        glass.animate().cancel();
        glass.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(220).start();
    }

    private void squishGlass() {
        glass.animate().cancel();
        glass.animate().scaleX(0.94f).scaleY(0.9f).setDuration(90).withEndAction(() ->
                glass.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.4f)).start()).start();
    }

    private void setPage(boolean home) {
        homeShown = home;
        homePage.setVisibility(home ? View.VISIBLE : View.GONE);
        appsPage.setVisibility(home ? View.GONE : View.VISIBLE);
        if (!home) appsPage.scrollTo(0, 0);
    }

    // ---- home page: greeting and favorites -------------------------------------------------

    private void buildHomePage() {
        homePage = new LinearLayout(this);
        homePage.setOrientation(LinearLayout.VERTICAL);
        homePage.setClipChildren(false);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(px(8), 0, 0, 0);

        greeting = text(11f, 0xFFEDEDF2, "sans-serif-medium");
        greeting.setLetterSpacing(0.28f);
        greeting.setShadowLayer(px(4), 0, px(1), 0xAA000000);
        hero.addView(greeting);

        TextView name = text(48f, Color.WHITE, "sans-serif-light");
        name.setText(Config.FAMILY_NAME);
        name.setShadowLayer(px(8), 0, px(2), 0xAA000000);
        name.setIncludeFontPadding(false);
        name.setPadding(0, px(2), 0, 0);
        hero.addView(name);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, MMMM d");
        date.setFormat24Hour("EEEE, MMMM d");
        date.setTextColor(0xFFEDEDF2);
        date.setTextSize(15f);
        date.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        date.setShadowLayer(px(5), 0, px(1), 0xAA000000);
        date.setPadding(0, px(6), 0, 0);
        hero.addView(date);

        upcoming = text(12f, 0xFFC4C8D4, "sans-serif");
        upcoming.setShadowLayer(px(4), 0, px(1), 0xAA000000);
        upcoming.setPadding(0, px(6), 0, 0);
        hero.addView(upcoming);

        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        heroLp.topMargin = px(34);
        homePage.addView(hero, heroLp);

        View spacer = new View(this);
        homePage.addView(spacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.BOTTOM);
        header.setPadding(px(8), 0, px(8), px(4));
        TextView favLabel = text(13f, Color.WHITE, "sans-serif-medium");
        favLabel.setText("Favorites");
        labelViews.add(favLabel);
        applyInkTo(favLabel, labelInk);
        header.addView(favLabel, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView hint = text(11f, 0xFFD0D4DE, "sans-serif");
        hint.setText("Hold OK to rearrange");
        hint.setShadowLayer(px(4), 0, px(1), 0xAA000000);
        header.addView(hint);
        homePage.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        favRow = new LinearLayout(this);
        favRow.setOrientation(LinearLayout.VERTICAL);
        favRow.setClipChildren(false);
        favRow.setPadding(px(4), px(4), px(4), px(8));
        homePage.addView(favRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void buildAppsPage() {
        appsContent = new LinearLayout(this);
        appsContent.setOrientation(LinearLayout.VERTICAL);
        appsContent.setClipChildren(false);
        appsContent.setPadding(px(4), px(8), px(4), px(32));

        appsPage = new ScrollView(this);
        appsPage.setVerticalScrollBarEnabled(false);
        appsPage.setOverScrollMode(View.OVER_SCROLL_NEVER);
        appsPage.setClipChildren(false);
        appsPage.setClipToPadding(false);
        appsPage.setFillViewport(true);
        appsPage.addView(appsContent, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
    }

    // ---- favorites (saved on the TV) and the Apps grid ------------------------------------

    private SharedPreferences prefs() {
        return getSharedPreferences("tvhome", MODE_PRIVATE);
    }

    private void loadFavorites() {
        String saved = prefs().getString("favorites", null);
        favoriteOrder = new ArrayList<>(saved == null
                ? Arrays.asList(Config.PINNED)
                : Arrays.asList(saved.isEmpty() ? new String[0] : saved.split(",")));
    }

    private void saveFavorites() {
        prefs().edit().putString("favorites", android.text.TextUtils.join(",", favoriteOrder)).apply();
    }

    private AppEntry find(String pkg) {
        for (AppEntry e : allApps) if (e.pkg.equals(pkg)) return e;
        return null;
    }

    private void buildFavorites() {
        favRow.removeAllViews();
        firstTile = null;
        List<AppEntry> favorites = new ArrayList<>();
        for (String pkg : favoriteOrder) {
            AppEntry e = find(pkg);
            if (e != null) favorites.add(e);
        }
        addRows(favRow, favorites, Config.FAVORITE_COLUMNS, 12f, true);
    }

    private void buildAllApps() {
        appsContent.removeAllViews();
        if (allAppsLabel != null) labelViews.remove(allAppsLabel);
        TextView label = allAppsLabel = text(13f, Color.WHITE, "sans-serif-medium");
        label.setText("All apps");
        label.setPadding(px(8), 0, 0, px(4));
        labelViews.add(label);
        applyInkTo(label, labelInk);
        appsContent.addView(label);
        List<AppEntry> sorted = new ArrayList<>(allApps);
        Collections.sort(sorted, (x, y) -> String.CASE_INSENSITIVE_ORDER.compare(x.label, y.label));
        addRows(appsContent, sorted, Config.ALL_APPS_COLUMNS, 11f, false);
    }

    private void addRows(LinearLayout container, List<AppEntry> apps, int columns, float labelSp,
                         final boolean favoritesRow) {
        for (int i = 0; i < apps.size(); i += columns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            for (int c = 0; c < columns; c++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (i + c < apps.size()) {
                    final AppEntry app = apps.get(i + c);
                    Tile tile = new Tile(this, dp, app, labelSp, this::tick);
                    tile.setTag(app.pkg);
                    tile.setOnClickListener(v -> launch(app));
                    tile.setOnLongClickListener(v -> {
                        if (favoritesRow) startMove(app);
                        else showAppMenu(app);
                        return true;
                    });
                    if (favoritesRow && firstTile == null) firstTile = tile;
                    if (favoritesRow && app.pkg.equals(movingPkg)) tile.setMoving(true);
                    row.addView(tile, lp);
                } else {
                    View spacer = new View(this); // keeps a short last row aligned
                    spacer.setVisibility(View.INVISIBLE);
                    row.addView(spacer, lp);
                }
            }
            container.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    /** Hold OK on a favorite: it gets a gold ring; Left and Right move it, OK (or Back) finishes. */
    private void startMove(AppEntry app) {
        movingPkg = app.pkg;
        refreshFavoritesKeepingFocus();
        Toast.makeText(this, "Use left and right to move, OK when done", Toast.LENGTH_LONG).show();
    }

    private void moveFavorite(int direction) {
        int at = favoriteOrder.indexOf(movingPkg);
        int to = at + direction;
        if (at < 0 || to < 0 || to >= favoriteOrder.size()) return;
        Collections.swap(favoriteOrder, at, to);
        saveFavorites();
        refreshFavoritesKeepingFocus();
    }

    private void endMove() {
        movingPkg = null;
        refreshFavoritesKeepingFocus();
    }

    private void refreshFavoritesKeepingFocus() {
        final String focusPkg = movingPkg != null ? movingPkg : currentTilePkg();
        quiet = true;
        buildFavorites();
        for (int r = 0; r < favRow.getChildCount(); r++) {
            LinearLayout row = (LinearLayout) favRow.getChildAt(r);
            for (int c = 0; c < row.getChildCount(); c++) {
                View v = row.getChildAt(c);
                if (focusPkg != null && focusPkg.equals(v.getTag())) v.requestFocus();
            }
        }
        root.post(() -> quiet = false);
    }

    private String currentTilePkg() {
        View focused = getCurrentFocus();
        return focused != null && focused.getTag() instanceof String ? (String) focused.getTag() : null;
    }

    /** Hold OK in the Apps grid: add to or remove from Favorites, or open the app's system page. */
    private void showAppMenu(final AppEntry app) {
        final boolean isFavorite = favoriteOrder.contains(app.pkg);
        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(new String[]{isFavorite ? "Remove from Favorites" : "Add to Favorites", "App info"},
                        (dialog, which) -> {
                            if (which == 0) {
                                if (isFavorite) favoriteOrder.remove(app.pkg);
                                else favoriteOrder.add(app.pkg);
                                saveFavorites();
                                buildFavorites();
                            } else {
                                openAppInfo(app);
                            }
                        })
                .show();
    }

    // ---- greeting, sunrise/sunset, upcoming dates, night mode --------------------------------

    private void updateInfo() {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        greeting.setText((hour < 12 ? "GOOD MORNING" : hour < 17 ? "GOOD AFTERNOON" : "GOOD EVENING"));

        long ms = System.currentTimeMillis();
        long rise = Sun.next(ms, true, Config.LATITUDE, Config.LONGITUDE);
        long set = Sun.next(ms, false, Config.LATITUDE, Config.LONGITUDE);
        String r = "Sunrise in " + span(rise - ms);
        String s = "Sunset in " + span(set - ms);
        sunLine.setText(rise < set ? r + "   ·   " + s : s + "   ·   " + r);

        upcoming.setText(upcomingLine(now));
        upcoming.setVisibility(upcoming.getText().length() == 0 ? View.GONE : View.VISIBLE);

        boolean night = hour >= Config.NIGHT_START_HOUR || hour < Config.NIGHT_END_HOUR;
        pageContainer.setAlpha(night ? Config.NIGHT_DIM : 1f);
    }

    /** Quiet one-liner: only dates within the next COUNTDOWN_SHOW_DAYS days, soonest first. */
    private String upcomingLine(final Calendar today) {
        List<Countdown> soon = new ArrayList<>();
        for (Countdown c : Config.COUNTDOWNS) {
            if (c.daysLeft(today) <= Config.COUNTDOWN_SHOW_DAYS) soon.add(c);
        }
        Collections.sort(soon, (x, y) -> Integer.compare(x.daysLeft(today), y.daysLeft(today)));
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < Math.min(2, soon.size()); i++) {
            Countdown c = soon.get(i);
            int days = c.daysLeft(today);
            int age = c.turning(today);
            String what = age > 0 ? c.name + " turns " + age : c.name;
            if (line.length() > 0) line.append("   ·   ");
            line.append(days == 0 ? what + " today" : days == 1 ? what + " tomorrow" : what + " in " + days + " days");
        }
        return line.toString();
    }

    private static String span(long ms) {
        long mins = Math.max(0, ms / 60000);
        return (mins / 60) + "h " + String.format(java.util.Locale.US, "%02d", mins % 60) + "m";
    }

    // ---- weather (network code is in Weather.java only) --------------------------------------

    private TextView text(float sp, int color, String family) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        return t;
    }

    /** Fetches the forecast off the UI thread, then repeats every Config.WEATHER_REFRESH_MS. */
    private void refreshWeather() {
        handler.removeCallbacks(weatherTick);
        new Thread(() -> {
            Weather.Report report = null;
            try {
                report = Weather.fetch();
            } catch (IOException | org.json.JSONException | RuntimeException e) {
                // Keep showing the last good reading.
            }
            final Weather.Report result = report;
            handler.post(() -> {
                if (result != null) {
                    weatherIcon.setWeather(result.code, result.isDay);
                    weatherTemp.setText(result.temp + "°");
                    weatherSummary.setText(result.summary + " · " + Config.WEATHER_PLACE);
                    weatherRange.setText("High " + result.high + "°  Low " + result.low
                            + "°  Rain " + result.rainChance + "%");
                } else if (weatherSummary.getText().toString().equals("Loading weather")) {
                    weatherSummary.setText("Weather unavailable");
                }
                handler.postDelayed(weatherTick, Config.WEATHER_REFRESH_MS);
            });
        }).start();
    }

    // ---- section label colour that follows the wallpaper ------------------------------------------

    /** Section labels: dark text on a bright picture, white on a dark one; recolours over the fade. */
    private void applyInk(Bitmap b) {
        final int fromLabel = labelInk;
        final int toLabel = lightPicture(b, 0.03f, 0.60f, 0.97f, 0.80f) ? 0xFF0B0B10 : Color.WHITE;
        if (inkAnim != null) inkAnim.cancel();
        inkAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        inkAnim.setDuration(Config.WALLPAPER_FADE_MS);
        final android.animation.ArgbEvaluator mix = new android.animation.ArgbEvaluator();
        inkAnim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            labelInk = (Integer) mix.evaluate(f, fromLabel, toLabel);
            for (TextView t : labelViews) applyInkTo(t, labelInk);
        });
        inkAnim.start();
    }

    private void applyInkTo(TextView t, int ink) {
        if (t == null) return;
        boolean dark = Color.red(ink) < 128;
        t.setTextColor(ink);
        t.setShadowLayer(px(4), 0, px(1), dark ? 0x66FFFFFF : 0xAA000000);
    }

    /** True when the part of the picture that will be visible in this region is bright. */
    private boolean lightPicture(Bitmap b, float x0, float y0, float x1, float y1) {
        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;
        float picAspect = (float) b.getWidth() / b.getHeight();
        float screenAspect = (float) sw / sh;
        // CENTER_CROP shows only a centred part of the picture.
        float visW = picAspect > screenAspect ? screenAspect / picAspect : 1f;
        float visH = picAspect < screenAspect ? picAspect / screenAspect : 1f;
        float offX = (1f - visW) / 2f;
        float offY = (1f - visH) / 2f;
        long sum = 0;
        int n = 0;
        for (int i = 0; i < 12; i++) {
            for (int j = 0; j < 12; j++) {
                float fx = offX + visW * (x0 + (x1 - x0) * (i + 0.5f) / 12f);
                float fy = offY + visH * (y0 + (y1 - y0) * (j + 0.5f) / 12f);
                int px = b.getPixel(Math.min(b.getWidth() - 1, (int) (fx * b.getWidth())),
                        Math.min(b.getHeight() - 1, (int) (fy * b.getHeight())));
                sum += 0.2126f * Color.red(px) + 0.7152f * Color.green(px) + 0.0722f * Color.blue(px);
                n++;
            }
        }
        float luminance = sum / (float) n / 255f * (1f - Config.OVERLAY_DARKNESS);
        return luminance > Config.DARK_TEXT_ABOVE;
    }

    // ---- wallpaper -------------------------------------------------------------------------

    private ImageView backdrop() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return v;
    }

    private void showNextWallpaper() {
        handler.removeCallbacks(nextWallpaper);
        if (wallpapers.isEmpty()) return;
        wallpaperIndex = (wallpaperIndex + 1) % wallpapers.size();
        Bitmap next = decode(wallpapers.get(wallpaperIndex));
        if (next == null) {
            handler.postDelayed(nextWallpaper, Config.WALLPAPER_INTERVAL_MS);
            return;
        }
        updateInfo();
        applyInk(next);
        if (baseBitmap == null) { // first picture: no fade needed
            baseBitmap = next;
            base.setImageBitmap(next);
        } else {
            fadeBitmap = next;
            fade.setImageBitmap(next);
            fade.setAlpha(0f);
            fade.animate().alpha(1f).setDuration(Config.WALLPAPER_FADE_MS)
                    .withEndAction(this::finishFade)
                    .start();
        }
        handler.postDelayed(nextWallpaper, Config.WALLPAPER_INTERVAL_MS);
    }

    /** Moves the faded-in picture to the base layer and recycles the old one. */
    private void finishFade() {
        if (fadeBitmap == null) return;
        Bitmap old = baseBitmap;
        baseBitmap = fadeBitmap;
        fadeBitmap = null;
        base.setImageBitmap(baseBitmap);
        fade.setAlpha(0f);
        fade.setImageDrawable(null);
        if (old != null) old.recycle();
    }

    /** Decodes one wallpaper at roughly screen size. Returns null if it cannot be read. */
    private Bitmap decode(String name) {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        String path = WALLPAPER_DIR + "/" + name;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getAssets().open(path)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= screenW) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (InputStream in = getAssets().open(path)) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    // ---- keys: move mode, idle wake, colour buttons --------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (idle) { // the first key press only wakes the screen
            if (e.getAction() == KeyEvent.ACTION_DOWN) exitIdle();
            return true;
        }
        resetIdle();
        if (movingPkg != null) { // rearranging: only left, right and OK/Back do anything
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                int key = e.getKeyCode();
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) moveFavorite(-1);
                else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) moveFavorite(1);
                else if (e.getRepeatCount() == 0 && (key == KeyEvent.KEYCODE_DPAD_CENTER
                        || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_BACK)) endMove();
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            int slot = e.getKeyCode() - KeyEvent.KEYCODE_PROG_RED;
            if (slot >= 0 && slot < Config.COLOR_KEY_APPS.length) {
                AppEntry app = find(Config.COLOR_KEY_APPS[slot]);
                if (app != null) launch(app);
                else Toast.makeText(this, "App not installed", Toast.LENGTH_SHORT).show();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // ---- idle screen -------------------------------------------------------------------------

    private void resetIdle() {
        handler.removeCallbacks(idleTick);
        handler.postDelayed(idleTick, Config.IDLE_TIMEOUT_MS);
    }

    private void buildIdleLayer(FrameLayout frame) {
        idleLayer = new FrameLayout(this);
        idleLayer.setBackgroundColor(Color.argb(Math.round(255 * Config.IDLE_DIM), 0, 0, 0));
        idleLayer.setVisibility(View.GONE);
        frame.addView(idleLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void enterIdle() {
        if (idle) return;
        idle = true;
        root.setVisibility(View.INVISIBLE);
        idleLayer.setAlpha(0f);
        idleLayer.setVisibility(View.VISIBLE);
        idleLayer.animate().alpha(1f).setDuration(1500).start();
    }

    private void exitIdle() {
        if (!idle) return;
        idle = false;
        idleLayer.animate().cancel();
        idleLayer.setVisibility(View.GONE);
        root.setVisibility(View.VISIBLE);
        quiet = true;
        updateInfo();
        if (firstTile != null && getCurrentFocus() == null) firstTile.requestFocus();
        root.post(() -> quiet = false);
        resetIdle();
    }

    // ---- sound -------------------------------------------------------------------------------

    private void loadTick() {
        if (!Config.TICK_ENABLED) return;
        try {
            sounds = new SoundPool.Builder().setMaxStreams(2)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .build();
            AssetFileDescriptor fd = getAssets().openFd("tick.wav");
            tickId = sounds.load(fd, 1);
            fd.close();
        } catch (IOException | RuntimeException e) {
            sounds = null; // no sound; everything else still works
        }
    }

    private void tick() {
        if (quiet || sounds == null) return;
        sounds.play(tickId, Config.TICK_VOLUME, Config.TICK_VOLUME, 1, 0, 1f);
    }

    // ---- actions -----------------------------------------------------------------------------

    private void launch(AppEntry app) {
        Intent start = new Intent(Intent.ACTION_MAIN);
        start.setComponent(app.activity);
        start.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startSafely(start);
    }

    private void openPlayStore() {
        Intent store = getPackageManager().getLeanbackLaunchIntentForPackage(PLAY_STORE);
        if (store == null) store = getPackageManager().getLaunchIntentForPackage(PLAY_STORE);
        if (store == null) {
            Toast.makeText(this, "Play Store not found", Toast.LENGTH_SHORT).show();
            return;
        }
        startSafely(store);
    }

    /** The app's system page (uninstall, disable, permissions). */
    private void openAppInfo(AppEntry app) {
        startSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg)));
    }

    private void startSafely(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(this, "Could not open that", Toast.LENGTH_SHORT).show();
        }
    }

    private void showInputPicker() {
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        final List<String> ids = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        try {
            if (tim != null) {
                for (TvInputInfo input : tim.getTvInputList()) {
                    if (input.isPassthroughInput()) {
                        ids.add(input.getId());
                        names.add(String.valueOf(input.loadLabel(this)));
                    }
                }
            }
        } catch (RuntimeException e) {
            // Fall through to the empty-list message below.
        }
        if (ids.isEmpty()) {
            Toast.makeText(this, "No inputs found", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Inputs")
                .setItems(names.toArray(new String[0]), (dialog, which) ->
                        startSafely(new Intent(Intent.ACTION_VIEW,
                                TvContract.buildChannelUriForPassthroughInput(ids.get(which)))))
                .show();
    }

    // ---- drawing helpers ------------------------------------------------------

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(radiusDp));
        return g;
    }

    private int px(int dips) {
        return Math.round(dips * dp);
    }
}
