package com.frank.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

/**
 * The ONLY file in this app that touches the network.
 * It makes one read-only HTTPS request to one fixed host and sends nothing but the
 * city-level coordinates from Config. No accounts, keys, cookies or device identifiers.
 */
final class Weather {
    private Weather() {}

    /** The only host this app may contact. */
    private static final String ALLOWED_HOST = "api.open-meteo.com";
    private static final int MAX_BYTES = 64 * 1024;

    static final class Report {
        final int temp;
        final int high;
        final int low;
        final int rainChance;
        final String summary;
        final int code;
        final boolean isDay;

        Report(int temp, int high, int low, int rainChance, String summary, int code, boolean isDay) {
            this.temp = temp;
            this.high = high;
            this.low = low;
            this.rainChance = rainChance;
            this.summary = summary;
            this.code = code;
            this.isDay = isDay;
        }
    }

    /** Blocking. Call from a background thread. */
    static Report fetch() throws IOException, JSONException {
        String address = String.format(Locale.US,
                "https://%s/v1/forecast?latitude=%.2f&longitude=%.2f"
                        + "&current=temperature_2m,weather_code,is_day"
                        + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                        + "&temperature_unit=fahrenheit&timezone=auto&forecast_days=1",
                ALLOWED_HOST, Config.LATITUDE, Config.LONGITUDE);
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol()) || !ALLOWED_HOST.equals(url.getHost())) {
            throw new IOException("blocked host");
        }
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setInstanceFollowRedirects(false); // never follow a redirect to another host
        try {
            if (conn.getResponseCode() != 200) throw new IOException("http " + conn.getResponseCode());
            JSONObject json = new JSONObject(read(conn.getInputStream()));
            JSONObject now = json.getJSONObject("current");
            JSONObject daily = json.getJSONObject("daily");
            return new Report(
                    (int) Math.round(now.getDouble("temperature_2m")),
                    (int) Math.round(first(daily, "temperature_2m_max")),
                    (int) Math.round(first(daily, "temperature_2m_min")),
                    (int) Math.round(first(daily, "precipitation_probability_max")),
                    describe(now.getInt("weather_code")),
                    now.getInt("weather_code"),
                    now.optInt("is_day", 1) == 1);
        } finally {
            conn.disconnect();
        }
    }

    private static double first(JSONObject daily, String key) throws JSONException {
        JSONArray values = daily.getJSONArray(key);
        return values.isNull(0) ? 0 : values.getDouble(0);
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = stream.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) throw new IOException("response too large");
            }
            return out.toString("UTF-8");
        }
    }

    /** WMO weather codes to plain words. */
    private static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorms";
        return "Weather";
    }
}
