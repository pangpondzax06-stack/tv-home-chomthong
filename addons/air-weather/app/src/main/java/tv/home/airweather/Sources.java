package tv.home.airweather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every network read, all keyless: the approximate place from this network's internet address (ipwho.is, then
 * ipapi.co), the weather (Open-Meteo), the measured PM2.5 (Air4Thai, Thailand's Pollution Control Department) and
 * the modelled PM2.5 (Open-Meteo's CAMS air quality). Every call blocks; callers run them off the UI thread.
 */
final class Sources {
    /** Hours shown after the current one. */
    static final int HOURS = 12;
    private static final int TIMEOUT_MS = 10_000;
    /** A station farther than this does not describe the air here. */
    private static final double STATION_KM = 50;
    /** A reading older than this is a station that has gone quiet. */
    private static final long STATION_AGE_MS = 3 * 60 * 60_000L;
    private static final ZoneId THAI_TIME = ZoneId.of("Asia/Bangkok");

    private Sources() { }

    /** A place: its name, region and country for the list, and where it is. */
    static final class Place {
        final String name;
        final String detail;
        final double latitude;
        final double longitude;

        Place(String name, String detail, double latitude, double longitude) {
            this.name = name;
            this.detail = detail;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /** The nearest Air4Thai station with a fresh reading, and its hour ("2026-10-25", "01:00", Thai time). */
    static final class Station {
        final String name;
        final double pm25;
        final String date;
        final String time;
        final double km;

        Station(String name, double pm25, String date, String time, double km) {
            this.name = name;
            this.pm25 = pm25;
            this.date = date;
            this.time = time;
            this.km = km;
        }

        /** The hour as Open-Meteo keys it for a Thai place (timezone=auto): "2026-10-25T01:00". */
        String hourKey() {
            return date + "T" + time;
        }
    }

    /**
     * The weather now, the next {@link #HOURS} hours and {@link #DAYS} days (their pm25 still -1), today's sunrise
     * and sunset as epoch ms, and the place's UTC offset (Open-Meteo's times are its local times).
     */
    static final class Weather {
        final int tempC;
        final int feelsC;
        final int code;
        final boolean day;
        final int humidity;
        /** Rain chance in % for the hour under way. */
        final int rainNow;
        final List<Snapshot.Hour> hours;
        final List<Snapshot.Day> days;
        final long sunrise;
        final long sunset;
        final int utcOffsetSeconds;

        Weather(int tempC, int feelsC, int code, boolean day, int humidity, int rainNow, List<Snapshot.Hour> hours,
                List<Snapshot.Day> days, long sunrise, long sunset, int utcOffsetSeconds) {
            this.tempC = tempC;
            this.feelsC = feelsC;
            this.code = code;
            this.day = day;
            this.humidity = humidity;
            this.rainNow = rainNow;
            this.hours = hours;
            this.days = days;
            this.sunrise = sunrise;
            this.sunset = sunset;
            this.utcOffsetSeconds = utcOffsetSeconds;
        }
    }

    /** Days in the forecast, today included. */
    static final int DAYS = 7;

    /**
     * Where this network meets the internet, to a city: what "the Wi-Fi's location" can mean without the TV's own
     * location setting, which is off on most sets. Mobile and some fibre lines can land a province away; the page's
     * Change location covers that.
     */
    static Place fromInternetAddress() throws IOException, JSONException {
        try {
            JSONObject r = get("https://ipwho.is/?fields=success,city,region,country,latitude,longitude");
            if (r.optBoolean("success") && r.has("latitude")) {
                return new Place(r.optString("city"), r.optString("region"), r.getDouble("latitude"),
                        r.getDouble("longitude"));
            }
        } catch (IOException | JSONException e) {
            // the second service below
        }
        JSONObject r = get("https://ipapi.co/json/");
        if (!r.has("latitude")) throw new IOException("ipapi.co gave no location");
        return new Place(r.optString("city"), r.optString("region"), r.getDouble("latitude"), r.getDouble("longitude"));
    }

    /** Up to five places matching what was typed, best match first. Empty when nothing matches. */
    static List<Place> search(String name) throws IOException, JSONException {
        JSONObject reply = get("https://geocoding-api.open-meteo.com/v1/search?count=5&format=json&name="
                + URLEncoder.encode(name.trim(), "UTF-8") + "&language=" + Locale.getDefault().getLanguage());
        List<Place> out = new ArrayList<>();
        JSONArray results = reply.optJSONArray("results");
        if (results == null) return out;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            StringBuilder detail = new StringBuilder(r.optString("admin1"));
            String country = r.optString("country");
            if (!country.isEmpty()) detail.append(detail.length() > 0 ? ", " : "").append(country);
            out.add(new Place(r.getString("name"), detail.toString(), r.getDouble("latitude"), r.getDouble("longitude")));
        }
        return out;
    }

    static Weather weather(double latitude, double longitude) throws IOException, JSONException {
        JSONObject reply = get("https://api.open-meteo.com/v1/forecast?" + where(latitude, longitude)
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,is_day"
                + "&hourly=temperature_2m,weather_code,precipitation_probability,is_day"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset"
                + "&timezone=auto&forecast_hours=" + (HOURS + 1) + "&forecast_days=" + DAYS);
        int offset = reply.optInt("utc_offset_seconds", 0);
        JSONObject now = reply.getJSONObject("current");
        JSONObject hourly = reply.getJSONObject("hourly");
        JSONArray time = hourly.getJSONArray("time");
        JSONArray temp = hourly.getJSONArray("temperature_2m");
        JSONArray code = hourly.getJSONArray("weather_code");
        JSONArray rain = hourly.getJSONArray("precipitation_probability");
        JSONArray day = hourly.getJSONArray("is_day");
        List<Snapshot.Hour> hours = new ArrayList<>();
        // Index 0 is the hour already under way, which "now" covers.
        for (int i = 1; i < time.length() && hours.size() < HOURS; i++) {
            if (temp.isNull(i) || code.isNull(i)) break;
            hours.add(new Snapshot.Hour(time.getString(i), (int) Math.round(temp.getDouble(i)), code.getInt(i),
                    day.optInt(i, 1) == 1, rain.isNull(i) ? 0 : rain.getInt(i), -1));
        }
        int rainNow = rain.length() > 0 && !rain.isNull(0) ? rain.getInt(0) : 0;

        JSONObject daily = reply.getJSONObject("daily");
        JSONArray date = daily.getJSONArray("time");
        JSONArray dCode = daily.getJSONArray("weather_code");
        JSONArray max = daily.getJSONArray("temperature_2m_max");
        JSONArray min = daily.getJSONArray("temperature_2m_min");
        JSONArray dRain = daily.getJSONArray("precipitation_probability_max");
        List<Snapshot.Day> days = new ArrayList<>();
        for (int i = 0; i < date.length(); i++) {
            // The last day can come back without values: stop there instead of failing the refresh.
            if (dCode.isNull(i) || max.isNull(i) || min.isNull(i)) break;
            days.add(new Snapshot.Day(date.getString(i), dCode.getInt(i), (int) Math.round(max.getDouble(i)),
                    (int) Math.round(min.getDouble(i)), dRain.isNull(i) ? 0 : dRain.getInt(i), -1));
        }
        long sunrise = localTime(daily.getJSONArray("sunrise").optString(0), offset);
        long sunset = localTime(daily.getJSONArray("sunset").optString(0), offset);

        return new Weather((int) Math.round(now.getDouble("temperature_2m")),
                (int) Math.round(now.getDouble("apparent_temperature")), now.getInt("weather_code"),
                now.getInt("is_day") == 1, (int) Math.round(now.getDouble("relative_humidity_2m")), rainNow, hours,
                days, sunrise, sunset, offset);
    }

    /** "2026-10-25T06:10" at the place's UTC offset, as epoch ms; 0 when it does not parse. */
    private static long localTime(String text, int utcOffsetSeconds) {
        try {
            return LocalDateTime.parse(text).toEpochSecond(ZoneOffset.ofTotalSeconds(utcOffsetSeconds)) * 1000L;
        } catch (DateTimeParseException | NullPointerException e) {
            return 0;
        }
    }

    /**
     * The CAMS model's PM2.5 by hour ("2026-10-25T02:00" as the weather's hours are keyed), and "now": yesterday,
     * to line the model up with a station's last reading (an hour or two old, and yesterday just after midnight),
     * and as far ahead as CAMS goes, about four and a half days, for the daily averages. It reads lower than Thai
     * stations, so near a station the hours ahead are scaled to it (Store, Air.correction).
     */
    static Map<String, Double> modelPm25(double latitude, double longitude) throws IOException, JSONException {
        JSONObject reply = get("https://air-quality-api.open-meteo.com/v1/air-quality?" + where(latitude, longitude)
                + "&current=pm2_5&hourly=pm2_5&timezone=auto&past_days=1&forecast_days=5");
        Map<String, Double> out = new HashMap<>();
        JSONObject now = reply.optJSONObject("current");
        if (now != null && !now.isNull("pm2_5")) out.put("now", now.getDouble("pm2_5"));
        JSONObject hourly = reply.getJSONObject("hourly");
        JSONArray time = hourly.getJSONArray("time");
        JSONArray pm = hourly.getJSONArray("pm2_5");
        for (int i = 0; i < time.length(); i++) {
            if (!pm.isNull(i)) out.put(time.getString(i), pm.getDouble(i));
        }
        return out;
    }

    /**
     * The nearest Air4Thai station within {@link #STATION_KM} whose PM2.5 reading is at most
     * {@link #STATION_AGE_MS} old, or null. Names in Thai on a Thai TV, in English otherwise.
     */
    static Station nearestStation(double latitude, double longitude, boolean thai) throws IOException, JSONException {
        JSONArray stations = get("https://air4thai.pcd.go.th/services/getNewAQI_JSON.php").getJSONArray("stations");
        long now = System.currentTimeMillis();
        Station best = null;
        for (int i = 0; i < stations.length(); i++) {
            JSONObject s = stations.getJSONObject(i);
            double lat = Air.reading(s.optString("lat"));
            double lon = Air.reading(s.optString("long"));
            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
            double km = Air.distanceKm(latitude, longitude, lat, lon);
            if (km > STATION_KM || (best != null && km >= best.km)) continue;
            JSONObject last = s.optJSONObject("AQILast");
            JSONObject pm = last == null ? null : last.optJSONObject("PM25");
            if (pm == null) continue;
            double value = Air.reading(pm.optString("value"));
            long at = thaiTime(last.optString("date"), last.optString("time"));
            if (Double.isNaN(value) || at < 0 || now - at > STATION_AGE_MS) continue;
            String name = s.optString(thai ? "nameTH" : "nameEN");
            best = new Station(name.isEmpty() ? s.optString("nameEN") : name, value, last.optString("date"),
                    last.optString("time"), km);
        }
        return best;
    }

    /** Air4Thai's "2026-10-25" and "01:00", Thai time, as epoch ms; -1 when either does not parse. */
    private static long thaiTime(String date, String time) {
        try {
            return LocalDateTime.parse(date + "T" + time).atZone(THAI_TIME).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return -1;
        }
    }

    private static String where(double latitude, double longitude) {
        return String.format(Locale.ROOT, "latitude=%.4f&longitude=%.4f", latitude, longitude);
    }

    private static JSONObject get(String url) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }
}
