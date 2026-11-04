package tv.home.airweather;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import java.util.Locale;

/** The words and colours: WMO weather codes, the PM2.5 bands, the two lines the launcher shows, the backdrop. */
final class Words {
    private static final int[] PM_LABEL = {R.string.pm_very_good, R.string.pm_good, R.string.pm_moderate,
            R.string.pm_sensitive, R.string.pm_unhealthy};

    private Words() { }

    /** "28° Cloudy" */
    static String weatherLine(Context c, Snapshot s) {
        return s.tempC + "° " + c.getString(sky(s.code, s.day));
    }

    /** "PM2.5 19 Good", or empty without a reading. Whole numbers: the page has the decimal. */
    static String airLine(Context c, Snapshot s) {
        if (s.pm25 < 0) return "";
        return c.getString(R.string.pm_line, Math.round(s.pm25), c.getString(pmLabel(s.pm25)));
    }

    static int pmLabel(double pm25) {
        return PM_LABEL[Air.band(pm25)];
    }

    /** "PM2.5 19.0 µg/m³ · Good", or empty without a reading. */
    static String airText(Context c, Snapshot s) {
        if (s.pm25 < 0) return "";
        return c.getString(R.string.pm_pill, pm(s.pm25), c.getString(pmLabel(s.pm25)));
    }

    /** Bands 0 and 1 (blue and green) are safe; from moderate up, the mask reminder shows. */
    static boolean pastGreen(double pm25) {
        return pm25 >= 0 && Air.band(pm25) >= 2;
    }

    /** "😷 Don't forget to wear a mask" past the green band with the reminder on; empty otherwise. */
    static String maskText(Context c, Snapshot s) {
        return pastGreen(s.pm25) && Store.maskReminder(c) ? "😷 " + c.getString(R.string.mask) : "";
    }

    /** "Feels like 31°" */
    static String feelsText(Context c, Snapshot s) {
        return c.getString(R.string.feels_short, s.feelsC);
    }

    /** Rain codes that are falling right now (drizzle, rain, showers, thunderstorms). */
    static boolean raining(int code) {
        return (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
    }

    /** The first of the next hours with a rain chance of at least this, as the strip's index, or -1. */
    static final int RAIN_LIKELY = 50;

    static int rainHour(Snapshot s) {
        for (int i = 0; i < s.hours.size(); i++) {
            if (s.hours.get(i).rain >= RAIN_LIKELY) return i;
        }
        return -1;
    }

    /**
     * "Rain 10% · likely at 13:00 (63%)", "Raining now · 80%", "Thunderstorm now", or "Rain 10%". Always the chance
     * for this hour; the hour it gets likely when one of the next twelve does.
     */
    static String rainText(Context c, Snapshot s) {
        if (raining(s.code)) {
            String now = c.getString(s.code >= 95 ? R.string.storm_now : R.string.rain_now);
            return s.rainNow > 0 ? now + " · " + s.rainNow + "%" : now;
        }
        StringBuilder out = new StringBuilder(c.getString(R.string.rain_chance, s.rainNow));
        int i = rainHour(s);
        if (i >= 0) {
            Snapshot.Hour h = s.hours.get(i);
            out.append(" · ").append(c.getString(R.string.rain_likely, hourOf(h.time), h.rain));
        }
        return out.toString();
    }

    /** Raining now, or likely within six hours: the launcher colours the rain line. */
    static boolean rainAlert(Snapshot s) {
        int i = rainHour(s);
        return raining(s.code) || (i >= 0 && i < 6);
    }

    /** "13:00" from "2026-10-25T13:00". */
    static String hourOf(String time) {
        return time.length() >= 16 ? time.substring(11, 16) : time;
    }

    /** One emoji for a WMO code, by day or night. */
    static String emoji(int code, boolean day) {
        switch (code) {
            case 0: return day ? "☀️" : "🌙";                  // ☀️ 🌙
            case 1: return day ? "🌤️" : "🌙";            // 🌤️ 🌙
            case 2: return day ? "⛅" : "☁️";                        // ⛅ ☁️
            case 3: return "☁️";                                         // ☁️
            case 45: case 48: return "🌫️";                         // 🌫️
            case 51: case 53: case 55: case 56: case 57: return "🌦️"; // 🌦️
            case 71: case 73: case 75: case 77: case 85: case 86: return "🌨️"; // 🌨️
            default:
                if (code >= 95) return "⛈️";                             // ⛈️
                if (code >= 61) return "🌧️";                       // 🌧️
                return "☁️";
        }
    }

    /** One decimal, as Air4Thai reports it: "18.7". */
    static String pm(double pm25) {
        return String.format(Locale.ROOT, "%.1f", pm25);
    }

    /** The words for a WMO weather code. */
    static int sky(int code, boolean day) {
        switch (code) {
            case 0: return day ? R.string.sky_sunny : R.string.sky_clear;
            case 1: return day ? R.string.sky_mostly_sunny : R.string.sky_mostly_clear;
            case 2: return R.string.sky_partly_cloudy;
            case 3: return R.string.sky_cloudy;
            case 45: case 48: return R.string.sky_fog;
            case 51: case 53: case 55: case 56: case 57: return R.string.sky_drizzle;
            case 61: return R.string.sky_light_rain;
            case 63: case 66: return R.string.sky_rain;
            case 65: case 67: return R.string.sky_heavy_rain;
            case 71: case 73: case 75: case 77: case 85: case 86: return R.string.sky_snow;
            case 80: case 81: case 82: return R.string.sky_showers;
            default: return code >= 95 ? R.string.sky_thunderstorm : R.string.sky_cloudy;
        }
    }

    /** A full-screen gradient in the weather's colours, dark at the bottom. */
    static Drawable backdrop(int code, boolean day) {
        int[] colours;
        if (code <= 1) {
            colours = day ? new int[]{0xFF2A6AA8, 0xFF0A1626} : new int[]{0xFF0D1B3E, 0xFF020308};
        } else if (code == 45 || code == 48) {
            colours = new int[]{0xFF5A6068, 0xFF101113};
        } else if (code >= 95) {
            colours = new int[]{0xFF2D2140, 0xFF07050C};
        } else if (code >= 51) {
            colours = new int[]{0xFF1E2C3D, 0xFF06090E};
        } else {
            colours = new int[]{0xFF3B4654, 0xFF0C0F14};
        }
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colours);
    }
}
