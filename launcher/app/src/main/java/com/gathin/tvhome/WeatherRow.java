package com.gathin.tvhome;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

/**
 * The one row the weather_provider app hands Home (config.xml): every text already in the TV's language, so the
 * launcher only lays them out. Columns it does not have read as empty or 0, so an older provider still gives a
 * block with what it has.
 */
final class WeatherRow {
    String emoji = "", sky = "", feels = "", rain = "", air = "", mask = "", place = "";
    int temp;
    int code = 3;
    boolean isDay = true;
    boolean rainAlert;
    float pm25 = -1;
    int band = -1;
    int airColour;
    long sunrise, sunset;
    boolean effects;

    /** null when the app is missing, has no row yet or fails; Home then keeps what it showed. Off the main thread. */
    static WeatherRow read(ContentResolver resolver, String authority) {
        try (Cursor c = resolver.query(Uri.parse("content://" + authority), null, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            WeatherRow r = new WeatherRow();
            r.emoji = text(c, "emoji");
            r.sky = text(c, "sky");
            r.feels = text(c, "feels_text");
            r.rain = text(c, "rain_text");
            r.air = text(c, "air_text");
            r.mask = text(c, "mask_text");
            r.place = text(c, "place");
            r.temp = (int) number(c, "temp", 0);
            r.code = (int) number(c, "code", 3);
            r.isDay = number(c, "is_day", 1) == 1;
            r.rainAlert = number(c, "rain_alert", 0) == 1;
            r.pm25 = (float) real(c, "pm25", -1);
            r.band = (int) number(c, "pm_band", -1);
            r.airColour = (int) number(c, "air_color", 0);
            r.sunrise = number(c, "sunrise", 0);
            r.sunset = number(c, "sunset", 0);
            r.effects = number(c, "effects", 0) == 1;
            return r;
        } catch (RuntimeException e) {
            return null;   // a provider that crashed or refused costs the block its update, never Home
        }
    }

    private static String text(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        String s = i >= 0 ? c.getString(i) : null;
        return s == null ? "" : s.trim();
    }

    private static long number(Cursor c, String column, long fallback) {
        int i = c.getColumnIndex(column);
        return i >= 0 && !c.isNull(i) ? c.getLong(i) : fallback;
    }

    private static double real(Cursor c, String column, double fallback) {
        int i = c.getColumnIndex(column);
        return i >= 0 && !c.isNull(i) ? c.getDouble(i) : fallback;
    }
}
