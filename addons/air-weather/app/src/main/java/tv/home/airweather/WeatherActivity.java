package tv.home.airweather;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The full page: the weather now, the PM2.5 (measured at the nearest Air4Thai station, or the model's estimate) and
 * the next twelve hours. It shows the last snapshot at once and refreshes when that is older than an hour, then every
 * hour while it stays on screen (5 minutes after a failure). The place is automatic until one is chosen.
 */
public final class WeatherActivity extends Activity {
    private static final long RETRY_MS = 5 * 60_000L;
    private static final long TICK_MS = 60 * 60_000L;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = () -> refresh(true);
    private View root;
    private View content;
    private TextView message;
    private View useAuto;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_air);
        root = findViewById(R.id.root);
        content = findViewById(R.id.content);
        message = findViewById(R.id.message);
        View change = findViewById(R.id.changePlace);
        change.setOnClickListener(v -> pickPlace());
        change.requestFocus();
        useAuto = findViewById(R.id.useAuto);
        useAuto.setOnClickListener(v -> {
            io.execute(() -> Store.useAutomatic(this));
            loading();
            refresh(true);
        });
        // The two switches are read by the launcher through NowProvider the next time Home comes up.
        TextView effects = findViewById(R.id.effects);
        effects.setText(Store.effects(this) ? R.string.effects_on : R.string.effects_off);
        effects.setOnClickListener(v -> {
            boolean on = !Store.effects(this);
            Store.setEffects(this, on);
            effects.setText(on ? R.string.effects_on : R.string.effects_off);
        });
        TextView mask = findViewById(R.id.maskSwitch);
        mask.setText(Store.maskReminder(this) ? R.string.mask_on : R.string.mask_off);
        mask.setOnClickListener(v -> {
            boolean on = !Store.maskReminder(this);
            Store.setMaskReminder(this, on);
            mask.setText(on ? R.string.mask_on : R.string.mask_off);
            Snapshot last = Store.last(this);
            if (last != null) show(last);
        });
        float density = getResources().getDisplayMetrics().density;
        for (int id : new int[]{R.id.changePlace, R.id.useAuto, R.id.effects, R.id.maskSwitch}) {
            findViewById(id).setBackground(new LiquidGlass.Background(density, 22, 1f));
        }
        RefreshJob.schedule(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Snapshot last = Store.last(this);
        if (last != null) show(last);
        refresh(Store.stale(last));
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);   // nothing of the page runs while it is not on screen; the hourly job carries on
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    /** Fetch now (or only schedule the next tick when the snapshot is still fresh). */
    private void refresh(boolean fetch) {
        main.removeCallbacks(tick);
        if (!fetch) {
            main.postDelayed(tick, TICK_MS);
            return;
        }
        io.execute(() -> {
            Snapshot s;
            try {
                s = Store.refresh(this);
            } catch (Exception e) {
                s = null;
            }
            Snapshot result = s;
            main.post(() -> {
                if (isDestroyed()) return;
                if (result != null) {
                    show(result);
                } else if (content.getVisibility() != View.VISIBLE) {
                    // Nothing to show at all: without an automatic place, ask for one; otherwise the network is down.
                    if (!Store.manual(this)) {
                        message.setText(R.string.no_auto);
                        pickPlace();
                    } else {
                        message.setText(R.string.offline);
                    }
                }
                main.postDelayed(tick, result != null ? TICK_MS : RETRY_MS);
            });
        });
    }

    private void show(Snapshot s) {
        float dp = getResources().getDisplayMetrics().density;
        root.setBackground(Words.backdrop(s.code, s.day));
        ((TextView) findViewById(R.id.place)).setText(s.place);
        ((TextView) findViewById(R.id.placeMode)).setText(s.manual ? R.string.place_manual : R.string.place_auto);
        useAuto.setVisibility(s.manual ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.temp)).setText(s.tempC + "°");
        ((TextView) findViewById(R.id.sky)).setText(Words.emoji(s.code, s.day) + " " + getString(Words.sky(s.code, s.day)));
        ((TextView) findViewById(R.id.feels)).setText(getString(R.string.feels_like, s.feelsC, s.humidity));
        TextView rain = findViewById(R.id.rain);
        rain.setText("☔ " + Words.rainText(this, s));                       // ☔
        rain.setTextColor(Words.rainAlert(s) ? 0xFF6EC1FF : 0xB39FD3FF);

        TextView mask = findViewById(R.id.mask);
        String maskText = Words.maskText(this, s);
        mask.setText(maskText);
        mask.setTextColor(s.pm25 >= 0 ? Air.colour(s.pm25) : 0xFFFFFFFF);
        mask.setVisibility(maskText.isEmpty() ? View.GONE : View.VISIBLE);

        TextView pill = findViewById(R.id.pmPill);
        TextView source = findViewById(R.id.pmSource);
        if (s.pm25 >= 0) {
            pill.setText(getString(R.string.pm_pill, Words.pm(s.pm25), getString(Words.pmLabel(s.pm25))));
            pill.setTextColor(Air.textColour(s.pm25));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Air.colour(s.pm25));
            bg.setCornerRadius(100 * dp);
            pill.setBackground(bg);
            source.setText(s.measured ? getString(R.string.pm_station, s.station, s.stationTime)
                    : getString(R.string.pm_model));
        } else {
            pill.setText(R.string.pm_none);
            pill.setTextColor(0xFFFFFFFF);
            pill.setBackgroundResource(R.drawable.day_card);
            source.setText("");
        }

        ((TextView) findViewById(R.id.hoursTitle))
                .setText(s.adjusted ? R.string.hours_title_adjusted : R.string.hours_title);
        LinearLayout hours = findViewById(R.id.hours);
        hours.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        List<Snapshot.Hour> list = s.hours;
        for (int i = 0; i < list.size(); i++) {
            Snapshot.Hour h = list.get(i);
            View cell = inflater.inflate(R.layout.item_hour, hours, false);
            cell.setBackground(new LiquidGlass.Background(dp, 20, 0.85f));
            ((TextView) cell.findViewById(R.id.hour)).setText(Words.hourOf(h.time));
            ((TextView) cell.findViewById(R.id.hourIcon)).setText(Words.emoji(h.code, h.day));
            ((TextView) cell.findViewById(R.id.hourTemp)).setText(h.tempC + "°");
            TextView hourRain = cell.findViewById(R.id.hourRain);
            hourRain.setText(h.rain + "%");
            if (h.rain >= Words.RAIN_LIKELY) hourRain.setTextColor(0xFF6EC1FF);
            ((TextView) cell.findViewById(R.id.hourPm)).setText(pmDot(h.pm25));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) cell.getLayoutParams();
            lp.setMarginEnd(i < list.size() - 1 ? Math.round(6 * dp) : 0);
            hours.addView(cell, lp);
        }

        LinearLayout days = findViewById(R.id.days);
        days.removeAllViews();
        for (int i = 0; i < s.days.size(); i++) {
            Snapshot.Day d = s.days.get(i);
            View row = inflater.inflate(R.layout.item_day, days, false);
            String name;
            try {
                name = i == 0 ? getString(R.string.today) : LocalDate.parse(d.date).getDayOfWeek()
                        .getDisplayName(TextStyle.SHORT, Locale.getDefault());
            } catch (DateTimeParseException e) {
                name = d.date;
            }
            ((TextView) row.findViewById(R.id.dayName)).setText(name);
            ((TextView) row.findViewById(R.id.dayIcon)).setText(Words.emoji(d.code, true));
            ((TextView) row.findViewById(R.id.dayTemps)).setText(d.maxC + "° / " + d.minC + "°");
            TextView dayRain = row.findViewById(R.id.dayRain);
            dayRain.setText("☔ " + d.rain + "%");
            if (d.rain >= Words.RAIN_LIKELY) dayRain.setTextColor(0xFF6EC1FF);
            ((TextView) row.findViewById(R.id.dayPm)).setText(pmDot(d.pm25));
            days.addView(row);
        }

        ((TextView) findViewById(R.id.updated))
                .setText(getString(R.string.updated, DateFormat.getTimeFormat(this).format(new Date(s.at))));
        message.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
    }

    /** Type a place, pick it from the matches, keep it. Back leaves the current place as it was. */
    private void pickPlace() {
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint(R.string.pick_hint);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(name, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.pick_title)
                .setView(box)
                .setPositiveButton(R.string.pick_search, (d, w) -> search(name.getText().toString()))
                .create();
        name.setOnEditorActionListener((v, action, event) -> {
            // The on-screen keyboard's search key, or Enter from a remote or a USB keyboard.
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (action != EditorInfo.IME_ACTION_SEARCH && !enter) return false;
            dialog.dismiss();
            search(name.getText().toString());
            return true;
        });
        dialog.show();
        name.requestFocus();
    }

    private void search(String typed) {
        if (typed.trim().isEmpty()) {
            pickPlace();
            return;
        }
        io.execute(() -> {
            List<Sources.Place> found;
            try {
                found = Sources.search(typed);
            } catch (Exception e) {
                found = null;
            }
            List<Sources.Place> places = found;
            main.post(() -> {
                if (isDestroyed()) return;
                if (places == null) {
                    Toast.makeText(this, R.string.offline, Toast.LENGTH_LONG).show();
                } else if (places.isEmpty()) {
                    Toast.makeText(this, getString(R.string.pick_none, typed.trim()), Toast.LENGTH_LONG).show();
                    pickPlace();
                } else {
                    choose(places);
                }
            });
        });
    }

    private void choose(List<Sources.Place> places) {
        CharSequence[] rows = new CharSequence[places.size()];
        for (int i = 0; i < rows.length; i++) {
            Sources.Place p = places.get(i);
            rows[i] = p.detail.isEmpty() ? p.name : p.name + " · " + p.detail;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.pick_title)
                .setItems(rows, (d, which) -> {
                    Sources.Place p = places.get(which);
                    io.execute(() -> Store.choose(this, p));
                    loading();
                    refresh(true);
                })
                .show();
    }

    /** "● 19" with the dot in the band's colour, or a dash without a value. */
    private static CharSequence pmDot(double pm25) {
        if (pm25 < 0) return "–";
        SpannableString dot = new SpannableString("● " + Math.round(pm25));
        dot.setSpan(new ForegroundColorSpan(Air.colour(pm25)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return dot;
    }

    /** A new place is on its way: until it arrives, the old place's numbers would be wrong, so they go. */
    private void loading() {
        content.setVisibility(View.INVISIBLE);
        message.setText(R.string.loading);
        message.setVisibility(View.VISIBLE);
    }
}
