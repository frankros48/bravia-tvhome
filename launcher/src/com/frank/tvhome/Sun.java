package com.frank.tvhome;

import java.util.Calendar;
import java.util.TimeZone;

/** Sunrise and sunset from latitude and longitude alone (NOAA almanac method). No network. */
final class Sun {
    private Sun() {}

    /** Next sunrise (rise = true) or sunset after nowMs, as epoch milliseconds. */
    static long next(long nowMs, boolean rise, double lat, double lon) {
        for (int dayOffset = -1; dayOffset <= 2; dayOffset++) {
            long t = on(nowMs + dayOffset * 86400000L, rise, lat, lon);
            if (t > nowMs) return t;
        }
        return nowMs;
    }

    /** The event on the local calendar day containing dayMs. */
    private static long on(long dayMs, boolean rise, double lat, double lon) {
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(dayMs);
        int dayOfYear = local.get(Calendar.DAY_OF_YEAR);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        long utcMidnight = utc.getTimeInMillis();

        double lngHour = lon / 15.0;
        double t = dayOfYear + ((rise ? 6 : 18) - lngHour) / 24.0;
        double m = 0.9856 * t - 3.289;
        double l = norm(m + 1.916 * sin(m) + 0.020 * sin(2 * m) + 282.634, 360);
        double ra = norm(Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(l)))), 360);
        ra += Math.floor(l / 90) * 90 - Math.floor(ra / 90) * 90;
        ra /= 15.0;
        double sinDec = 0.39782 * sin(l);
        double cosDec = Math.cos(Math.asin(sinDec));
        double cosH = (Math.cos(Math.toRadians(90.833)) - sinDec * sin(lat)) / (cosDec * Math.cos(Math.toRadians(lat)));
        if (cosH > 1 || cosH < -1) return Long.MIN_VALUE; // sun never rises or sets today
        double h = Math.toDegrees(Math.acos(cosH));
        h = (rise ? 360 - h : h) / 15.0;
        double ut = norm(h + ra - 0.06571 * t - 6.622 - lngHour, 24);

        // The result is a time of day in UTC; pick the day that lands nearest local solar noon.
        long solarNoon = utcMidnight + Math.round((12 - lngHour) * 3600000.0);
        long best = utcMidnight + Math.round(ut * 3600000.0);
        for (int shift = -1; shift <= 1; shift++) {
            long cand = utcMidnight + shift * 86400000L + Math.round(ut * 3600000.0);
            if (Math.abs(cand - solarNoon) < Math.abs(best - solarNoon)) best = cand;
        }
        return best;
    }

    private static double sin(double deg) { return Math.sin(Math.toRadians(deg)); }

    private static double norm(double v, double range) {
        v %= range;
        return v < 0 ? v + range : v;
    }
}
