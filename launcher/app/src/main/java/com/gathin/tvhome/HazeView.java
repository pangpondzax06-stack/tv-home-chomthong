package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * The PM2.5 over Home, in front of everything but the focus highlight: a dusty fog that thickens toward the bottom,
 * slow smog banks and drifting specks, all scaled by the reading. Clean air (about 12 µg/m³ and below) draws
 * nothing; it is thickest from about 120 up, and even then stays light enough to read the dock through. Drawn only
 * while the weather_provider app's "Home sky & haze" switch is on, animated only while Home is resumed, on
 * WeatherSceneView's beat (it invalidates this view in the same frame as itself).
 */
public final class HazeView extends View {
    /** Readings at or below this draw nothing; at CLEAR + SPAN the haze is at its thickest. */
    private static final float CLEAR = 12f, SPAN = 110f;
    private static final int DUST = 0xFFB9A88C;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(11);
    private final RectF rect = new RectF();
    private boolean on;
    private boolean running;
    private float pm25 = -1;
    private int bandColour = DUST;
    private float density;
    private long lastFrame;
    private LinearGradient fog;
    private int fogH;
    private PorterDuffColorFilter tint;
    private Bitmap blob;
    private float[] bankX, bankY, bankW, bankSpeed;
    private float[] speckX, speckY, speckR, speckVx, speckPhase;

    public HazeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setFocusable(false);
    }

    void setOn(boolean on) {
        if (this.on == on) return;
        this.on = on;
        if (!on) speckX = null;
        invalidate();
    }

    void setRunning(boolean running) {
        if (this.running == running) return;
        this.running = running;
        lastFrame = SystemClock.uptimeMillis();
        invalidate();
    }

    /** The reading (-1 for none) and its band's colour from the provider. */
    void setPm(float pm25, int bandColour) {
        if (pm25 == this.pm25 && bandColour == this.bandColour) return;
        this.pm25 = pm25;
        this.bandColour = bandColour == 0 ? DUST : bandColour;
        density = pm25 < 0 ? 0f : Math.max(0f, Math.min(1f, (pm25 - CLEAR) / SPAN));
        fog = null;
        speckX = null;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fog = null;
        speckX = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!on || density <= 0f || getWidth() == 0) return;
        int w = getWidth(), h = getHeight();
        long now = SystemClock.uptimeMillis();
        float dt = running ? Math.min(0.15f, (now - lastFrame) / 1000f) : 0f;
        lastFrame = now;
        // The haze takes the dust's beige, pulled a third of the way toward the band's colour.
        int colour = WeatherSceneView.mix(DUST, bandColour, 0.35f);
        if (fog == null || fogH != h) build(w, h, colour);

        paint.setShader(fog);
        paint.setAlpha(255);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        paint.setColorFilter(tint);
        for (int i = 0; i < bankX.length; i++) {
            bankX[i] += bankSpeed[i] * dt;
            if (bankX[i] > w) bankX[i] = -bankW[i];
            paint.setAlpha(Math.round(110 * density));
            rect.set(bankX[i], bankY[i], bankX[i] + bankW[i], bankY[i] + bankW[i] * 0.35f);
            canvas.drawBitmap(blob, null, rect, paint);
        }
        paint.setColorFilter(null);

        paint.setColor(colour);
        float t = now / 1000f;
        for (int i = 0; i < speckX.length; i++) {
            speckX[i] += speckVx[i] * dt;
            if (speckX[i] > w + 4) speckX[i] = -4;
            float y = speckY[i] + (float) Math.sin(t * 0.6f + speckPhase[i]) * h * 0.01f;
            paint.setAlpha(Math.round((60 + 70 * density) * (0.6f + 0.4f * (float) Math.sin(t + speckPhase[i]))));
            canvas.drawCircle(speckX[i], y, speckR[i], paint);
        }
    }

    private void build(int w, int h, int colour) {
        fogH = h;
        int top = (Math.round(255 * 0.12f * density) << 24) | (colour & 0xFFFFFF);
        int bottom = (Math.round(255 * 0.42f * density) << 24) | (colour & 0xFFFFFF);
        fog = new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP);
        tint = new PorterDuffColorFilter(colour, PorterDuff.Mode.SRC_IN);
        if (blob == null) blob = WeatherSceneView.softBlob(128);
        int banks = density < 0.2f ? 1 : density < 0.5f ? 2 : 3;
        bankX = new float[banks];
        bankY = new float[banks];
        bankW = new float[banks];
        bankSpeed = new float[banks];
        for (int i = 0; i < banks; i++) {
            bankW[i] = w * (0.7f + random.nextFloat() * 0.5f);
            bankX[i] = random.nextFloat() * w - bankW[i] * 0.5f;
            bankY[i] = h * (0.45f + random.nextFloat() * 0.35f);
            bankSpeed[i] = w * (0.003f + random.nextFloat() * 0.004f);
        }
        int specks = Math.round(140 * density) + 6;
        speckX = new float[specks];
        speckY = new float[specks];
        speckR = new float[specks];
        speckVx = new float[specks];
        speckPhase = new float[specks];
        float dp = getResources().getDisplayMetrics().density;
        for (int i = 0; i < specks; i++) {
            speckX[i] = random.nextFloat() * w;
            speckY[i] = random.nextFloat() * h;
            speckR[i] = dp * (0.8f + random.nextFloat() * 1.6f);
            speckVx[i] = w * (0.004f + random.nextFloat() * 0.01f);
            speckPhase[i] = random.nextFloat() * 6.3f;
        }
    }
}
