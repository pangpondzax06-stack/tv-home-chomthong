package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

import java.util.Calendar;
import java.util.Random;

/**
 * The weather behind Home, from the weather_provider app (config.xml) while its "Home sky & haze" switch is on: a sky
 * for the hour (night, dawn, day, sunset, dusk, stretched onto today's sunrise and sunset) mixed toward grey by the
 * clouds, with drifting clouds, stars on a clear night, rain and lightning. Drawn 25 times a second only while
 * Home is resumed with animations on; otherwise one still frame. Off, it draws nothing and never schedules a frame.
 *
 * One beat for both weather views: a Choreographer callback invalidates this view and the haze in the same vsync,
 * every {@link #FRAME_NS}. Each scheduling its own frames put them out of step, and Home redrew at 60 a second.
 */
public final class WeatherSceneView extends View {
    private static final long FRAME_NS = 40_000_000L;
    private static final int[] NIGHT = {0xFF060B1C, 0xFF0E1A36};
    private static final int[] DAWN = {0xFF1F2752, 0xFF8C5470};
    private static final int[] DAY = {0xFF1A4C8A, 0xFF3C7DBD};
    private static final int[] SUNSET = {0xFF2A1E4C, 0xFFB45A38};
    private static final int[] DUSK = {0xFF141B3E, 0xFF372A56};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(7);
    private final RectF rect = new RectF();
    private boolean on;
    private boolean running;
    private int code = 3;
    private boolean isDay = true;
    private long sunrise, sunset;
    private long lastFrame;
    private long skyMinute = -1;
    private int skyW, skyH;
    private LinearGradient skyShader;
    /** Rebuilt with the sky once a minute, never per frame. */
    private RadialGradient glowShader;
    private PorterDuffColorFilter cloudTint;
    private int[] sky = NIGHT;

    private Bitmap blob;
    private float[] cloudX, cloudY, cloudW, cloudSpeed;
    private float[] dropX, dropY, dropLen, dropSpeed;
    private float[] starX, starY, starPhase;
    private float flash;
    private long nextFlash;
    /** The haze, invalidated on this view's beat. */
    private View partner;
    private long lastTick;
    private boolean ticking;
    private final Choreographer.FrameCallback tick = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!on || !running) {
                ticking = false;
                return;
            }
            if (frameTimeNanos - lastTick >= FRAME_NS) {
                lastTick = frameTimeNanos;
                invalidate();
                if (partner != null) partner.invalidate();
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public WeatherSceneView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
    }

    void setPartner(View partner) {
        this.partner = partner;
    }

    boolean isOn() {
        return on;
    }

    /** The provider's switch (and whether it answered at all). Off frees the particles and stops drawing. */
    void setOn(boolean on) {
        if (this.on == on) return;
        this.on = on;
        if (!on) {
            cloudX = null;
            dropX = null;
        }
        invalidate();
        startTicking();
    }

    /** Home resumed with animations on: frames keep coming. Paused or reduced motion: one still frame. */
    void setRunning(boolean running) {
        if (this.running == running) return;
        this.running = running;
        lastFrame = SystemClock.uptimeMillis();
        invalidate();
        if (partner != null) partner.invalidate();
        startTicking();
    }

    private void startTicking() {
        if (ticking || !on || !running) return;
        ticking = true;
        Choreographer.getInstance().postFrameCallback(tick);
    }

    void setWeather(int code, boolean isDay, long sunrise, long sunset) {
        boolean particles = this.code != code || cloudX == null;
        this.code = code;
        this.isDay = isDay;
        this.sunrise = sunrise;
        this.sunset = sunset;
        skyMinute = -1;
        if (particles) cloudX = null;   // rebuilt for the new weather at the next frame
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        skyMinute = -1;
        cloudX = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!on || getWidth() == 0) return;
        int w = getWidth(), h = getHeight();
        long now = SystemClock.uptimeMillis();
        float dt = running ? Math.min(0.1f, (now - lastFrame) / 1000f) : 0f;
        lastFrame = now;
        if (cloudX == null) build(w, h);

        drawSky(canvas, w, h);
        if (code <= 2 && !isNight()) drawSunGlow(canvas, w, h);
        if (code <= 1 && isNight()) drawStars(canvas, now);
        drawClouds(canvas, w, dt);
        if (dropX != null) drawRain(canvas, w, h, dt);
        if (code >= 95) drawLightning(canvas, w, h, now);
    }

    // ---- the sky -----------------------------------------------------------------------------------

    private void drawSky(Canvas canvas, int w, int h) {
        long minute = System.currentTimeMillis() / 60_000L;
        if (minute != skyMinute || w != skyW || h != skyH || skyShader == null) {
            skyMinute = minute;
            skyW = w;
            skyH = h;
            int[] clear = clearSky();
            int[] grey = isNight() ? new int[]{0xFF0D1015, 0xFF1B2028} : new int[]{0xFF46515F, 0xFF667180};
            if (rainy()) grey = isNight() ? new int[]{0xFF0B0F15, 0xFF161D27} : new int[]{0xFF2D3946, 0xFF44525F};
            if (code >= 95) grey = new int[]{0xFF17142A, 0xFF2B2540};
            float cloudiness = cloudiness();
            sky = new int[]{mix(clear[0], grey[0], cloudiness), mix(clear[1], grey[1], cloudiness)};
            skyShader = new LinearGradient(0, 0, 0, h, sky[0], sky[1], Shader.TileMode.CLAMP);
            glowShader = null;
            cloudTint = new PorterDuffColorFilter(rainy() ? 0xFFA3ADBA : isNight() ? 0xFFC6CEDD : 0xFFFFFFFF,
                    PorterDuff.Mode.SRC_IN);
        }
        paint.setShader(skyShader);
        paint.setAlpha(255);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }

    /** 0 clear to 1 overcast: how far the sky is pulled toward grey. */
    private float cloudiness() {
        if (code == 0) return 0f;
        if (code == 1) return 0.15f;
        if (code == 2) return 0.35f;
        if (code == 3) return 0.65f;
        if (code == 45 || code == 48) return 0.75f;
        if (code >= 95) return 0.9f;
        return 0.8f;
    }

    private boolean rainy() {
        return (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
    }

    private static final long DAY_MS = 24 * 60 * 60_000L, HOUR_MS = 60 * 60_000L;

    /**
     * Minutes from sunrise to now (negative before it) and from sunrise to sunset. In real time, not minutes of the
     * day, so nothing breaks at midnight; and the provider's sunrise is moved by whole days to the one nearest now
     * (from six hours before it to eighteen after), so a row from yesterday still lights today. Without a sunrise
     * and sunset, 06:15 and 18:00 today.
     */
    private long[] clock() {
        long now = System.currentTimeMillis();
        long sr = sunrise > 0 ? sunrise : todayAt(6, 15);
        long ss = sunset > sr ? sunset : sr + (sunset > 0 ? 0 : 11 * HOUR_MS + 45 * 60_000L);
        long shift = Math.floorDiv(now - sr + 6 * HOUR_MS, DAY_MS) * DAY_MS;
        sr += shift;
        ss += shift;
        return new long[]{(now - sr) / 60_000L, (ss - sr) / 60_000L};
    }

    private static long todayAt(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        return c.getTimeInMillis();
    }

    private boolean isNight() {
        long[] t = clock();
        return t[0] < -30 || t[0] > t[1] + 60;
    }

    /** The clear sky for this minute, between anchors set around sunrise (0) and sunset. */
    private int[] clearSky() {
        long[] t = clock();
        long now = t[0], sr = 0, ss = t[1];
        long[] at = {sr - 90, sr - 20, sr + 60, ss - 60, ss + 10, ss + 70, ss + 150};
        int[][] look = {NIGHT, DAWN, DAY, DAY, SUNSET, DUSK, NIGHT};
        if (now <= at[0] || now >= at[at.length - 1]) return NIGHT;
        for (int i = 1; i < at.length; i++) {
            if (now <= at[i]) {
                float f = (now - at[i - 1]) / (float) Math.max(1, at[i] - at[i - 1]);
                return new int[]{mix(look[i - 1][0], look[i][0], f), mix(look[i - 1][1], look[i][1], f)};
            }
        }
        return NIGHT;
    }

    private void drawSunGlow(Canvas canvas, int w, int h) {
        long[] t = clock();
        boolean low = Math.abs(t[0]) < 70 || Math.abs(t[0] - t[1]) < 70;   // near sunrise or sunset
        float cx = w * 0.8f, cy = low ? h * 0.72f : h * 0.16f, r = h * (low ? 0.55f : 0.42f);
        if (glowShader == null) {
            int core = low ? 0x66FF9C5C : 0x40FFF2D2;
            glowShader = new RadialGradient(cx, cy, r, core, core & 0x00FFFFFF, Shader.TileMode.CLAMP);
        }
        paint.setShader(glowShader);
        paint.setAlpha(Math.round(255 * (1 - cloudiness())));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setShader(null);
    }

    private void drawStars(Canvas canvas, long now) {
        paint.setColor(0xFFFFFFFF);
        for (int i = 0; i < starX.length; i++) {
            float twinkle = 0.55f + 0.45f * (float) Math.sin(now / 900.0 + starPhase[i]);
            paint.setAlpha(Math.round(150 * twinkle * (1 - cloudiness())));
            canvas.drawCircle(starX[i], starY[i], i % 7 == 0 ? 2.2f : 1.3f, paint);
        }
    }

    // ---- clouds, rain and lightning ----------------------------------------------------------------

    private void build(int w, int h) {
        if (blob == null) blob = softBlob(128);
        int clouds = code == 0 ? 0 : code == 1 ? 2 : code == 2 ? 4 : code == 45 || code == 48 ? 3 : 7;
        cloudX = new float[clouds];
        cloudY = new float[clouds];
        cloudW = new float[clouds];
        cloudSpeed = new float[clouds];
        for (int i = 0; i < clouds; i++) {
            cloudW[i] = w * (0.35f + random.nextFloat() * 0.35f);
            cloudX[i] = random.nextFloat() * (w + cloudW[i]) - cloudW[i];
            cloudY[i] = h * (0.02f + random.nextFloat() * 0.45f);
            cloudSpeed[i] = w * (0.004f + random.nextFloat() * 0.008f);
        }
        int drops = !rainy() ? 0 : code >= 95 || code == 65 || code == 82 ? 170 : code == 63 || code == 81 ? 120 : 70;
        if (drops == 0) {
            dropX = null;
        } else {
            dropX = new float[drops];
            dropY = new float[drops];
            dropLen = new float[drops];
            dropSpeed = new float[drops];
            for (int i = 0; i < drops; i++) {
                dropX[i] = random.nextFloat() * w * 1.2f;
                dropY[i] = random.nextFloat() * h;
                dropLen[i] = h * (0.018f + random.nextFloat() * 0.022f);
                dropSpeed[i] = h * (0.9f + random.nextFloat() * 0.5f);
            }
        }
        if (starX == null) {
            starX = new float[70];
            starY = new float[70];
            starPhase = new float[70];
            for (int i = 0; i < 70; i++) {
                starX[i] = random.nextFloat() * w;
                starY[i] = random.nextFloat() * h * 0.6f;
                starPhase[i] = random.nextFloat() * 6.3f;
            }
        }
        nextFlash = SystemClock.uptimeMillis() + 4000;
    }

    private void drawClouds(Canvas canvas, int w, float dt) {
        int alpha = rainy() ? 70 : isNight() ? 45 : 80;
        paint.setColorFilter(cloudTint);
        for (int i = 0; i < cloudX.length; i++) {
            cloudX[i] += cloudSpeed[i] * dt;
            if (cloudX[i] > w) cloudX[i] = -cloudW[i];
            float cw = cloudW[i], ch = cw * 0.42f;
            paint.setAlpha(alpha);
            // Three overlapping soft blobs make one cloud.
            rect.set(cloudX[i], cloudY[i] + ch * 0.25f, cloudX[i] + cw * 0.6f, cloudY[i] + ch);
            canvas.drawBitmap(blob, null, rect, paint);
            rect.set(cloudX[i] + cw * 0.25f, cloudY[i], cloudX[i] + cw * 0.85f, cloudY[i] + ch * 0.9f);
            canvas.drawBitmap(blob, null, rect, paint);
            rect.set(cloudX[i] + cw * 0.45f, cloudY[i] + ch * 0.3f, cloudX[i] + cw, cloudY[i] + ch);
            canvas.drawBitmap(blob, null, rect, paint);
        }
        paint.setColorFilter(null);
    }

    private void drawRain(Canvas canvas, int w, int h, float dt) {
        paint.setColor(0xFFC8DCF0);
        paint.setAlpha(code >= 95 ? 110 : 90);
        paint.setStrokeWidth(Math.max(1.5f, h / 540f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < dropX.length; i++) {
            dropY[i] += dropSpeed[i] * dt;
            dropX[i] -= dropSpeed[i] * dt * 0.22f;
            if (dropY[i] > h) {
                dropY[i] = -dropLen[i];
                dropX[i] = random.nextFloat() * w * 1.2f;
            }
            canvas.drawLine(dropX[i], dropY[i], dropX[i] - dropLen[i] * 0.22f, dropY[i] + dropLen[i], paint);
        }
    }

    /** A double flicker every 5 to 13 seconds, fading in a quarter of a second. Still frames never flash. */
    private void drawLightning(Canvas canvas, int w, int h, long now) {
        if (!running) return;
        if (now >= nextFlash) {
            flash = 1f;
            nextFlash = now + 5000 + random.nextInt(8000);
        }
        if (flash <= 0f) return;
        float a = flash > 0.6f ? flash : flash > 0.45f ? 0.15f : flash * 1.4f;   // the flicker
        paint.setColor(0xFFE8ECFF);
        paint.setAlpha(Math.round(120 * a));
        canvas.drawRect(0, 0, w, h, paint);
        flash -= 0.12f;
    }

    // ---- shared with HazeView ----------------------------------------------------------------------

    /** A white disc that fades to nothing at its edge: drawn once, scaled for every cloud and smog bank. */
    static Bitmap softBlob(int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = size / 2f;
        p.setShader(new RadialGradient(r, r, r, new int[]{0xFFFFFFFF, 0x99FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        new Canvas(b).drawCircle(r, r, r, p);
        return b;
    }

    static int mix(int a, int b, float f) {
        f = Math.max(0f, Math.min(1f, f));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | Math.round(ar + (br - ar) * f) << 16 | Math.round(ag + (bg - ag) * f) << 8
                | Math.round(ab + (bb - ab) * f);
    }
}
