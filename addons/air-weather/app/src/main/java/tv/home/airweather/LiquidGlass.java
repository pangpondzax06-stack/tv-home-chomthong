package tv.home.airweather;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Liquid glass in the iOS manner, as far as a TV can afford it: a copy of the launcher's LiquidGlass, so the page's
 * cards and buttons match Home. Android draws no live backdrop blur inside a window, so the glass is built from light
 * alone: a frosted tint that fades downwards, a
 * specular sheen across the upper part, a rim lit from the top left and catching again at the bottom right, and a
 * darker lip along the bottom edge that gives it thickness. Every shader is made once per size and drawn in the
 * shape's own coordinates, so a shape that moves reuses them; a frame costs four rounded
 * rectangles from the display list and no blur, shadow layer or offscreen buffer.
 */
final class LiquidGlass {
    private final float density;
    /** 1 for the buttons; less for larger areas, where the same alphas would look milky. */
    private final float strength;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lip = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF local = new RectF();
    private float builtW = -1, builtH = -1;

    LiquidGlass(float density, float strength) {
        this.density = density;
        this.strength = strength;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.3f * density);
    }

    /** Draws the glass over r (in the canvas's coordinates) with corner radius radius. */
    void draw(Canvas canvas, RectF r, float radius) {
        float w = r.width(), h = r.height();
        if (w <= 0 || h <= 0) return;
        if (w != builtW || h != builtH) build(w, h);
        radius = Math.min(radius, h / 2f);
        int save = canvas.save();
        canvas.translate(r.left, r.top);
        local.set(0, 0, w, h);
        canvas.drawRoundRect(local, radius, radius, fill);
        canvas.drawRoundRect(local, radius, radius, sheen);
        canvas.drawRoundRect(local, radius, radius, lip);
        float half = rim.getStrokeWidth() / 2f;
        local.inset(half, half);
        canvas.drawRoundRect(local, radius - half, radius - half, rim);
        canvas.restoreToCount(save);
    }

    private void build(float w, float h) {
        builtW = w;
        builtH = h;
        fill.setShader(new LinearGradient(0, 0, 0, h, white(0.17f), white(0.06f), Shader.TileMode.CLAMP));
        // The sheen: bright at the very top, gone by just past the middle, like light pooling in a curved lens.
        sheen.setShader(new LinearGradient(0, 0, 0, h * 0.6f, new int[]{white(0.24f), white(0.07f), 0},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        lip.setShader(new LinearGradient(0, h * 0.62f, 0, h, 0, 0x2E000000, Shader.TileMode.CLAMP));
        // The rim follows a light from the top left: bright there, faint along the sides, catching again bottom right.
        rim.setShader(new LinearGradient(0, 0, w, h, new int[]{white(0.85f), white(0.16f), white(0.08f), white(0.42f)},
                new float[]{0f, 0.32f, 0.7f, 1f}, Shader.TileMode.CLAMP));
    }

    private int white(float alpha) {
        return Math.round(255 * Math.min(1f, alpha * strength)) << 24 | 0xFFFFFF;
    }

    /**
     * A view background in the glass, for the page's buttons and hour cards. Focused, a button becomes a bright
     * solid, as tvOS does.
     */
    static final class Background extends Drawable {
        private final LiquidGlass glass;
        private final float radius;
        private final Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private boolean focused, activated;

        Background(float density, float radiusDp, float strength) {
            glass = new LiquidGlass(density, strength);
            radius = radiusDp * density;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            rect.set(b);
            if (focused) {
                float r = Math.min(radius, rect.height() / 2f);
                solid.setColor(0xF5FFFFFF);
                canvas.drawRoundRect(rect, r, r, solid);
                return;
            }
            glass.draw(canvas, rect, radius);
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean f = false, a = false;
            for (int s : state) {
                if (s == android.R.attr.state_focused) f = true;
                if (s == android.R.attr.state_activated) a = true;
            }
            if (f == focused && a == activated) return false;
            focused = f;
            activated = a;
            invalidateSelf();
            return true;
        }

        @Override
        public void setAlpha(int alpha) {
            solid.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            solid.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
