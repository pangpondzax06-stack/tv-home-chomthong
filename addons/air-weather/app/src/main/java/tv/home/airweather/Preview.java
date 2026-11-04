package tv.home.airweather;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Ten minutes of other weather on Home, set over ADB through NowProvider.call: "pm=95,code=95,phase=sunset".
 * pm is the PM2.5, code a WMO weather code (61 rain, 95 thunderstorm, 3 cloudy, 0 clear), phase one of dawn, day,
 * sunset, dusk or night, which moves today's sunrise and sunset around the current time. Only what launcher reads
 * changes; the stored snapshot and the page stay real.
 */
final class Preview {
    private static final long LASTS_MS = 10 * 60_000L;
    private static final long MIN = 60_000L;

    private Preview() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("preview", Context.MODE_PRIVATE);
    }

    /** null or empty ends the preview. */
    static void set(Context c, String arg) {
        if (arg == null || arg.trim().isEmpty()) {
            prefs(c).edit().clear().commit();
        } else {
            prefs(c).edit().putString("arg", arg.trim()).putLong("until", System.currentTimeMillis() + LASTS_MS)
                    .commit();
        }
    }

    static Snapshot apply(Context c, Snapshot s) {
        SharedPreferences p = prefs(c);
        String arg = p.getString("arg", null);
        if (arg == null || System.currentTimeMillis() > p.getLong("until", 0)) return s;
        double pm = s.pm25;
        int code = s.code;
        long sunrise = s.sunrise, sunset = s.sunset;
        long now = System.currentTimeMillis();
        for (String part : arg.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            try {
                switch (kv[0].trim()) {
                    case "pm": pm = Double.parseDouble(kv[1].trim()); break;
                    case "code": code = Integer.parseInt(kv[1].trim()); break;
                    case "phase":
                        long[] times = phase(kv[1].trim(), now);
                        if (times != null) {
                            sunrise = times[0];
                            sunset = times[1];
                        }
                        break;
                    default: break;
                }
            } catch (NumberFormatException e) {
                // a typo in the preview leaves that value real
            }
        }
        boolean day = sunrise > 0 && sunset > 0 ? now >= sunrise && now < sunset : s.day;
        return new Snapshot(s.place, s.manual, s.at, s.tempC, s.feelsC, code, day, s.humidity, s.rainNow, pm,
                s.measured, s.station, s.stationTime, s.hours, s.adjusted, s.days, sunrise, sunset);
    }

    /** Sunrise and sunset that put now in the phase the launcher's sky draws for that name. */
    private static long[] phase(String name, long now) {
        long dayLength = 11 * 60 * MIN + 45 * MIN;
        switch (name) {
            case "dawn": return new long[]{now + 20 * MIN, now + 20 * MIN + dayLength};
            case "day": return new long[]{now - 5 * 60 * MIN, now - 5 * 60 * MIN + dayLength};
            case "sunset": return new long[]{now - 10 * MIN - dayLength, now - 10 * MIN};
            case "dusk": return new long[]{now - 70 * MIN - dayLength, now - 70 * MIN};
            case "night": return new long[]{now - 4 * 60 * MIN - dayLength, now - 4 * 60 * MIN};
            default: return null;
        }
    }
}
