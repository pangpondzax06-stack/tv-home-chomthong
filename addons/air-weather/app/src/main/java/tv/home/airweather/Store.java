package tv.home.airweather;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The place (automatic, or the one chosen in a search) and the last snapshot, in the app's own preferences; and the
 * one refresh that the hourly job, the provider's catch-up and the page all use.
 */
final class Store {
    /** A little over the hourly job, so a job that runs a few minutes late does not read as stale. */
    static final long STALE_MS = 65 * 60_000L;
    /** The internet address rarely moves; ask where it is every six hours, not every hour. */
    private static final long AUTO_PLACE_MS = 6 * 60 * 60_000L;
    private static final String PREFS = "store";

    /** This TV's place until a search or "Use automatic location" changes it: Chom Thong district, Bangkok. */
    private static final String HOME_NAME = "\u0e08\u0e2d\u0e21\u0e17\u0e2d\u0e07";
    private static final double HOME_LAT = 13.6773, HOME_LON = 100.4846;

    private Store() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Snapshot last(Context c) {
        return Snapshot.fromJson(prefs(c).getString("snapshot", null));
    }

    static boolean stale(Snapshot s) {
        return s == null || System.currentTimeMillis() - s.at > STALE_MS;
    }

    static boolean manual(Context c) {
        return prefs(c).getBoolean("manual", true);
    }

    /** From the page's search: this place from now on, until Use automatic location. */
    static void choose(Context c, Sources.Place p) {
        prefs(c).edit().putBoolean("manual", true).putString("name", p.name)
                .putLong("lat", Double.doubleToRawLongBits(p.latitude))
                .putLong("lon", Double.doubleToRawLongBits(p.longitude)).commit();
    }

    /** Back to the internet address's place, asked again at the next refresh. */
    static void useAutomatic(Context c) {
        prefs(c).edit().putBoolean("manual", false).putLong("auto_at", 0).commit();
    }

    /**
     * Fetch everything for the current place and keep it. One at a time: the job and the page can both ask. The
     * weather must answer; each PM2.5 source may fail on its own, and with both gone the PM2.5 is -1.
     */
    static synchronized Snapshot refresh(Context c) throws IOException, JSONException {
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        boolean manual = p.getBoolean("manual", true);
        String place;
        double lat, lon;
        if (manual && !p.contains("lat")) {
            place = HOME_NAME;
            lat = HOME_LAT;
            lon = HOME_LON;
        } else if (manual) {
            place = p.getString("name", "");
            lat = Double.longBitsToDouble(p.getLong("lat", 0));
            lon = Double.longBitsToDouble(p.getLong("lon", 0));
        } else {
            if (now - p.getLong("auto_at", 0) > AUTO_PLACE_MS || !p.contains("auto_lat")) {
                try {
                    Sources.Place a = Sources.fromInternetAddress();
                    p.edit().putString("auto_name", a.name)
                            .putLong("auto_lat", Double.doubleToRawLongBits(a.latitude))
                            .putLong("auto_lon", Double.doubleToRawLongBits(a.longitude))
                            .putLong("auto_at", now).commit();
                } catch (IOException | JSONException e) {
                    if (!p.contains("auto_lat")) throw e;   // the last known place still beats no weather at all
                }
            }
            place = p.getString("auto_name", "");
            lat = Double.longBitsToDouble(p.getLong("auto_lat", 0));
            lon = Double.longBitsToDouble(p.getLong("auto_lon", 0));
        }

        Sources.Weather w = Sources.weather(lat, lon);
        Map<String, Double> model = null;
        try {
            model = Sources.modelPm25(lat, lon);
        } catch (IOException | JSONException e) {
            // the hours go without PM2.5, and a station can still give "now"
        }
        Sources.Station station = null;
        if (Air.inThailand(lat, lon)) {
            try {
                station = Sources.nearestStation(lat, lon, "th".equals(Locale.getDefault().getLanguage()));
            } catch (IOException | JSONException e) {
                // the model's estimate stands in below
            }
        }

        // Near a station, scale the model's hours by how far off it was at the station's own hour (Air.correction).
        double factor = 1;
        boolean adjusted = false;
        if (station != null && model != null) {
            Double atStation = model.get(station.hourKey());
            if (atStation != null) {
                factor = Air.correction(station.pm25, atStation);
                adjusted = true;
            }
        }
        List<Snapshot.Hour> hours = new ArrayList<>();
        for (int i = 0; i < w.hours.size(); i++) {
            Snapshot.Hour h = w.hours.get(i);
            Double pm = model == null ? null : model.get(h.time);
            double value = pm == null ? -1 : adjusted ? Air.adjust(pm, factor, i + 1) : pm;
            hours.add(new Snapshot.Hour(h.time, h.tempC, h.code, h.day, h.rain, value));
        }
        double pm25 = -1;
        boolean measured = false;
        if (station != null) {
            pm25 = station.pm25;
            measured = true;
        } else if (model != null && model.containsKey("now")) {
            pm25 = model.get("now");
        }

        Map<String, Double> dayMeans = model == null ? null
                : dailyMeans(model, w.hours.isEmpty() ? null : w.hours.get(0).time, adjusted ? factor : 1);
        List<Snapshot.Day> days = new ArrayList<>();
        for (Snapshot.Day d : w.days) {
            Double mean = dayMeans == null ? null : dayMeans.get(d.date);
            days.add(new Snapshot.Day(d.date, d.code, d.maxC, d.minC, d.rain, mean == null ? -1 : mean));
        }

        Snapshot s = new Snapshot(place, manual, now, w.tempC, w.feelsC, w.code, w.day, w.humidity, w.rainNow, pm25,
                measured, station == null ? "" : station.name, station == null ? "" : station.time, hours, adjusted,
                days, w.sunrise, w.sunset);
        p.edit().putString("snapshot", s.toJson()).commit();
        return s;
    }

    /**
     * The model's mean PM2.5 per day ("2026-10-25"), each hour scaled by the station factor as the hours strip is,
     * counted from the hour before {@code nextHour}. A day with fewer than 12 hours (the far end of CAMS) is left out.
     */
    static Map<String, Double> dailyMeans(Map<String, Double> model, String nextHour, double factor) {
        LocalDateTime base = null;
        try {
            if (nextHour != null) base = LocalDateTime.parse(nextHour).minusHours(1);
        } catch (DateTimeParseException e) {
            // no base: every hour counts as now, fully corrected
        }
        Map<String, double[]> sums = new HashMap<>();   // date -> {sum, count}
        for (Map.Entry<String, Double> e : model.entrySet()) {
            if (e.getKey().length() < 13 || e.getKey().charAt(10) != 'T') continue;   // "now"
            int ahead = 0;
            try {
                if (base != null) ahead = (int) Duration.between(base, LocalDateTime.parse(e.getKey())).toHours();
            } catch (DateTimeParseException x) {
                continue;
            }
            double[] s = sums.computeIfAbsent(e.getKey().substring(0, 10), k -> new double[2]);
            s[0] += Air.adjust(e.getValue(), factor, ahead);
            s[1]++;
        }
        Map<String, Double> out = new HashMap<>();
        for (Map.Entry<String, double[]> e : sums.entrySet()) {
            if (e.getValue()[1] >= 12) out.put(e.getKey(), e.getValue()[0] / e.getValue()[1]);
        }
        return out;
    }

    // ---- the two switches on the page ---------------------------------------------------------------

    /** The sky, rain and haze behind Home (the launcher reads it as the provider's "effects"). On by default. */
    static boolean effects(Context c) {
        return prefs(c).getBoolean("effects", true);
    }

    static void setEffects(Context c, boolean on) {
        prefs(c).edit().putBoolean("effects", on).commit();
    }

    /** "Don't forget to wear a mask" while the PM2.5 is past the green band. On by default. */
    static boolean maskReminder(Context c) {
        return prefs(c).getBoolean("mask", true);
    }

    static void setMaskReminder(Context c, boolean on) {
        prefs(c).edit().putBoolean("mask", on).commit();
    }
}
