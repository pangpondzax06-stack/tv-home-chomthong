package tv.home.airweather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/**
 * One read-only row for the launcher (launcher config.xml, weather_provider): content://tv.home.airweather.now
 *
 * <pre>
 *   weather     "28° Cloudy"                       the short line, as the first version had it
 *   air         "PM2.5 19 Good"
 *   air_color   the PM2.5 band's colour as ARGB, 0 without a reading
 *   place       "Bangkok"
 *   updated     when it was fetched, epoch ms
 *   temp        28                                  whole °C
 *   emoji       "☁️"
 *   sky         "Cloudy"
 *   feels_text  "Feels like 34°"
 *   rain_text   "Rain 10% · likely at 13:00 (63%)"
 *   rain_alert  1 when it rains now or is likely within six hours
 *   air_text    "PM2.5 19.0 µg/m³ · Good", empty without a reading
 *   pm25        µg/m³, -1 without a reading
 *   pm_band     0 very good … 4 affects health, -1 without a reading
 *   mask_text   "😷 Don't forget to wear a mask" past the green band with the reminder on, else empty
 *   code        the WMO weather code;  is_day 1 or 0
 *   sunrise, sunset   today's, epoch ms (0 unknown)
 *   effects     1 when the owner wants the sky and haze behind Home (the page's switch)
 * </pre>
 *
 * Texts follow the TV's language. No row until the first refresh has finished. A read also makes sure the hourly
 * job exists, and asks for a refresh now when the row is older than {@link Store#STALE_MS} (at most every five
 * minutes), which is how Home catches up after the TV has been asleep.
 */
public final class NowProvider extends ContentProvider {
    static final String[] COLUMNS = {"weather", "air", "air_color", "place", "updated", "temp", "emoji", "sky",
            "feels_text", "rain_text", "rain_alert", "air_text", "pm25", "pm_band", "mask_text", "code", "is_day",
            "sunrise", "sunset", "effects"};
    private static final long KICK_MS = 5 * 60_000L;
    private static volatile long lastKick;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context c = getContext();
        RefreshJob.schedule(c);
        Snapshot s = Store.last(c);
        long now = System.currentTimeMillis();
        if (Store.stale(s) && now - lastKick > KICK_MS) {
            lastKick = now;
            RefreshJob.now(c);
        }
        MatrixCursor row = new MatrixCursor(COLUMNS, 1);
        if (s != null) {
            s = Preview.apply(c, s);
            boolean pm = s.pm25 >= 0;
            row.addRow(new Object[]{Words.weatherLine(c, s), Words.airLine(c, s), pm ? Air.colour(s.pm25) : 0,
                    s.place, s.at, s.tempC, Words.emoji(s.code, s.day), c.getString(Words.sky(s.code, s.day)),
                    Words.feelsText(c, s), Words.rainText(c, s), Words.rainAlert(s) ? 1 : 0, Words.airText(c, s),
                    s.pm25, pm ? Air.band(s.pm25) : -1, Words.maskText(c, s), s.code, s.day ? 1 : 0, s.sunrise,
                    s.sunset, Store.effects(c) ? 1 : 0});
        }
        return row;
    }

    /**
     * ADB only: show Home under other weather for ten minutes, to see how a storm or a smoggy day looks.
     * <pre>
     *   content call --uri content://tv.home.airweather.now --method preview --arg "pm=95,code=95,phase=sunset"
     *   content call --uri content://tv.home.airweather.now --method preview          (back to the real weather)
     * </pre>
     * Any other caller is refused: an app on the TV cannot dress Home up.
     */
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!"preview".equals(method)) return null;
        if (Binder.getCallingUid() != Process.SHELL_UID) throw new SecurityException("preview is for adb only");
        Preview.set(getContext(), arg);
        Bundle out = new Bundle();
        out.putString("preview", arg == null ? "off" : arg);
        return out;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd.tv.home.airweather.now";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }
}
