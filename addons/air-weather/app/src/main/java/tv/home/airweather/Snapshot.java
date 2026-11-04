package tv.home.airweather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One refresh: the place, the weather now, the PM2.5 now, the next hours and days, today's sunrise and sunset. Kept
 * as JSON in the preferences; fields added after the first version read with a default, so an old snapshot still
 * shows until the next refresh replaces it.
 */
final class Snapshot {
    final String place;
    final boolean manual;
    /** When it was fetched, epoch ms. */
    final long at;
    final int tempC;
    final int feelsC;
    final int code;
    final boolean day;
    final int humidity;
    /** Rain chance in % for the hour under way. */
    final int rainNow;
    /** PM2.5 now in µg/m³, or -1 when neither source answered. */
    final double pm25;
    /** true: read at an Air4Thai station. false: the CAMS model estimate. */
    final boolean measured;
    /** The station's name and the hour of its reading ("01:00"); empty for the model. */
    final String station;
    final String stationTime;
    final List<Hour> hours;
    /** true: the hours' and days' PM2.5 are the model scaled to the station (Air.adjust). false: the model as it is. */
    final boolean adjusted;
    /** Today first. */
    final List<Day> days;
    /** Today's sunrise and sunset, epoch ms; 0 when unknown. */
    final long sunrise;
    final long sunset;

    /** One hour ahead: "2026-10-25T02:00", whole degrees, the WMO code, rain chance in %, and PM2.5 (-1 unknown). */
    static final class Hour {
        final String time;
        final int tempC;
        final int code;
        final boolean day;
        final int rain;
        final double pm25;

        Hour(String time, int tempC, int code, boolean day, int rain, double pm25) {
            this.time = time;
            this.tempC = tempC;
            this.code = code;
            this.day = day;
            this.rain = rain;
            this.pm25 = pm25;
        }
    }

    /** One day: "2026-10-25", the WMO code, high and low, the highest rain chance, the mean PM2.5 (-1 unknown). */
    static final class Day {
        final String date;
        final int code;
        final int maxC;
        final int minC;
        final int rain;
        final double pm25;

        Day(String date, int code, int maxC, int minC, int rain, double pm25) {
            this.date = date;
            this.code = code;
            this.maxC = maxC;
            this.minC = minC;
            this.rain = rain;
            this.pm25 = pm25;
        }
    }

    Snapshot(String place, boolean manual, long at, int tempC, int feelsC, int code, boolean day, int humidity,
             int rainNow, double pm25, boolean measured, String station, String stationTime, List<Hour> hours,
             boolean adjusted, List<Day> days, long sunrise, long sunset) {
        this.place = place;
        this.manual = manual;
        this.at = at;
        this.tempC = tempC;
        this.feelsC = feelsC;
        this.code = code;
        this.day = day;
        this.humidity = humidity;
        this.rainNow = rainNow;
        this.pm25 = pm25;
        this.measured = measured;
        this.station = station;
        this.stationTime = stationTime;
        this.hours = Collections.unmodifiableList(hours);
        this.adjusted = adjusted;
        this.days = Collections.unmodifiableList(days);
        this.sunrise = sunrise;
        this.sunset = sunset;
    }

    String toJson() {
        try {
            JSONArray h = new JSONArray();
            for (Hour x : hours) {
                h.put(new JSONObject().put("t", x.time).put("c", x.tempC).put("w", x.code).put("d", x.day)
                        .put("r", x.rain).put("p", x.pm25));
            }
            JSONArray d = new JSONArray();
            for (Day x : days) {
                d.put(new JSONObject().put("t", x.date).put("w", x.code).put("hi", x.maxC).put("lo", x.minC)
                        .put("r", x.rain).put("p", x.pm25));
            }
            return new JSONObject().put("place", place).put("manual", manual).put("at", at).put("temp", tempC)
                    .put("feels", feelsC).put("code", code).put("day", day).put("rh", humidity).put("rainNow", rainNow)
                    .put("pm", pm25).put("measured", measured).put("station", station).put("stationTime", stationTime)
                    .put("hours", h).put("adjusted", adjusted).put("days", d).put("sunrise", sunrise)
                    .put("sunset", sunset).toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);   // put() only throws for NaN and infinite numbers, never stored
        }
    }

    /** null when nothing was stored yet or the stored text does not parse. */
    static Snapshot fromJson(String text) {
        if (text == null) return null;
        try {
            JSONObject o = new JSONObject(text);
            JSONArray h = o.getJSONArray("hours");
            List<Hour> hours = new ArrayList<>();
            for (int i = 0; i < h.length(); i++) {
                JSONObject x = h.getJSONObject(i);
                hours.add(new Hour(x.getString("t"), x.getInt("c"), x.getInt("w"), x.getBoolean("d"), x.getInt("r"),
                        x.getDouble("p")));
            }
            List<Day> days = new ArrayList<>();
            JSONArray d = o.optJSONArray("days");
            for (int i = 0; d != null && i < d.length(); i++) {
                JSONObject x = d.getJSONObject(i);
                days.add(new Day(x.getString("t"), x.getInt("w"), x.getInt("hi"), x.getInt("lo"), x.getInt("r"),
                        x.getDouble("p")));
            }
            return new Snapshot(o.getString("place"), o.getBoolean("manual"), o.getLong("at"), o.getInt("temp"),
                    o.getInt("feels"), o.getInt("code"), o.getBoolean("day"), o.getInt("rh"), o.optInt("rainNow", 0),
                    o.getDouble("pm"), o.getBoolean("measured"), o.getString("station"), o.getString("stationTime"),
                    hours, o.optBoolean("adjusted", false), days, o.optLong("sunrise", 0), o.optLong("sunset", 0));
        } catch (JSONException e) {
            return null;
        }
    }
}
