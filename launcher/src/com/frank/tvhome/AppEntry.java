package com.frank.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One launchable app, plus the code that finds them all. */
final class AppEntry {
    final String label;
    final String pkg;
    final ComponentName activity;
    final Drawable art;
    /** True when art is a 16:9 banner, false when it is a square icon. */
    final boolean isBanner;

    private AppEntry(String label, String pkg, ComponentName target, Drawable art, boolean isBanner) {
        this.label = label;
        this.pkg = pkg;
        this.activity = target;
        this.art = art;
        this.isBanner = isBanner;
    }

    /** Every app that declares a TV launcher activity, pinned ones first, then A to Z. */
    static List<AppEntry> load(Context ctx) {
        PackageManager packages = ctx.getPackageManager();
        Intent tvLauncher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);

        Set<String> skip = new HashSet<>(Arrays.asList(Config.HIDDEN));
        skip.add(ctx.getPackageName());
        Set<String> done = new HashSet<>();
        List<AppEntry> apps = new ArrayList<>();

        for (ResolveInfo match : packages.queryIntentActivities(tvLauncher, 0)) {
            ActivityInfo info = match.activityInfo;
            if (info == null) continue;
            String pkg = info.packageName;
            if (skip.contains(pkg) || !done.add(pkg)) continue;

            Drawable banner = info.loadBanner(packages);
            if (banner == null) banner = info.applicationInfo.loadBanner(packages);
            Drawable art = banner != null ? banner : match.loadIcon(packages);

            apps.add(new AppEntry(String.valueOf(match.loadLabel(packages)), pkg,
                    new ComponentName(pkg, info.name), art, banner != null));
        }

        final List<String> order = Arrays.asList(Config.PINNED);
        Collections.sort(apps, (x, y) -> {
            int rx = order.indexOf(x.pkg);
            int ry = order.indexOf(y.pkg);
            if (rx >= 0 && ry >= 0) return Integer.compare(rx, ry);
            if (rx >= 0) return -1;
            if (ry >= 0) return 1;
            return String.CASE_INSENSITIVE_ORDER.compare(x.label, y.label);
        });
        return apps;
    }
}
