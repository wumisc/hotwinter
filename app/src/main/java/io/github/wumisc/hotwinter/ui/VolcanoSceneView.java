package io.github.wumisc.hotwinter.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

import io.github.wumisc.hotwinter.R;

/**
 * 火山实景层：透明背景的活火山照片作为主体，暗红天空、火山灰、口部辉光、
 * 熔岩地光与火星都是静态氛围层。发热强度变化时只重绘一帧，不持续刷屏，
 * 避免首页背景来回闪烁。
 */
public final class VolcanoSceneView extends View {

    private static final int EMBERS = 34;
    private static final int SMOKE = 7;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final RectF dst = new RectF();
    private final float[] emberX = new float[EMBERS];
    private final float[] emberY = new float[EMBERS];
    private final float[] emberS = new float[EMBERS];
    private final float[] emberA = new float[EMBERS];
    private final float[] smokeX = new float[SMOKE];
    private final float[] smokeY = new float[SMOKE];
    private final float[] smokeR = new float[SMOKE];
    private final float[] smokeA = new float[SMOKE];

    private Bitmap mountain;
    private float intensity = 0.7f;

    public VolcanoSceneView(Context context) {
        this(context, null);
    }

    public VolcanoSceneView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        Random random = new Random(9127L);
        for (int i = 0; i < EMBERS; i++) {
            emberX[i] = 0.34f + random.nextFloat() * 0.32f;
            emberY[i] = 0.16f + random.nextFloat() * 0.36f;
            emberS[i] = 0.9f + random.nextFloat() * 2.2f;
            emberA[i] = 0.35f + random.nextFloat() * 0.62f;
        }
        for (int i = 0; i < SMOKE; i++) {
            smokeX[i] = 0.43f + random.nextFloat() * 0.14f;
            smokeY[i] = 0.10f + random.nextFloat() * 0.20f;
            smokeR[i] = 0.16f + random.nextFloat() * 0.11f;
            smokeA[i] = 0.22f + random.nextFloat() * 0.20f;
        }
        mountain = decodeMountain();
    }

    public void setIntensity(float value) {
        float next = Math.max(0.35f, Math.min(1f, value));
        if (Math.abs(next - intensity) < 0.04f) return;
        intensity = next;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mountain == null) mountain = decodeMountain();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mountain != null) {
            mountain.recycle();
            mountain = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        drawSky(canvas, w, h);
        drawAsh(canvas, w, h);
        drawCraterHalo(canvas, w, h);
        drawMountain(canvas, w, h);
        drawSmoke(canvas, w, h);
        drawEmbers(canvas, w, h);
        drawGroundGlow(canvas, w, h);
    }

    private Bitmap decodeMountain() {
        return BitmapFactory.decodeResource(getResources(), R.drawable.scene_volcano);
    }

    private void drawSky(Canvas canvas, int w, int h) {
        paint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF0A0403, 0xFF1C0806, 0xFF3B110A, 0xFF64210E},
                new float[]{0f, 0.40f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // 火山口上方的热辉，给暗天一点方向感，但保持静态。
        RadialGradient skyGlow = new RadialGradient(w * 0.5f, h * 0.30f, w * 0.55f,
                new int[]{0x33FF6A2B, 0x12FF4A18, 0x00FF4A18},
                new float[]{0f, 0.46f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(skyGlow);
        canvas.drawCircle(w * 0.5f, h * 0.30f, w * 0.55f, paint);
        paint.setShader(null);
    }

    private void drawAsh(Canvas canvas, int w, int h) {
        for (int i = 0; i < 7; i++) {
            float cx = w * (0.12f + i * 0.13f);
            float cy = h * (0.08f + (i % 3) * 0.085f);
            float radius = w * (0.18f + (i % 4) * 0.055f);
            RadialGradient gradient = new RadialGradient(cx, cy, radius,
                    new int[]{0xAA1C0C08, 0x331C0C08, 0x001C0C08},
                    new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP);
            paint.setShader(gradient);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setShader(null);
        }
    }

    private void drawCraterHalo(Canvas canvas, int w, int h) {
        float cx = w * 0.5f;
        float cy = h * 0.30f;
        float radius = w * 0.34f;
        RadialGradient halo = new RadialGradient(cx, cy, radius,
                new int[]{withAlpha(0xFFFF5A1E, (int) (142 * intensity)),
                        withAlpha(0xFFFF3C12, 34), 0x00FF3C12},
                new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(halo);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);
    }

    private void drawMountain(Canvas canvas, int w, int h) {
        if (mountain == null || mountain.isRecycled()) return;
        float drawH = h * 0.72f;
        float aspect = mountain.getWidth() / (float) mountain.getHeight();
        float drawW = drawH * aspect;
        float left = (w - drawW) * 0.5f;
        dst.set(left, h - drawH, left + drawW, h);
        paint.setAlpha(255);
        canvas.drawBitmap(mountain, null, dst, bitmapPaint);

        // 口部熔岩的局部高光，叠在照片上让火山口真的“亮起来”。
        float cx = w * 0.5f;
        float cy = h - drawH * 0.68f;
        RadialGradient crater = new RadialGradient(cx, cy, w * 0.105f,
                new int[]{withAlpha(0xFFFFE6A8, (int) (118 * intensity)),
                        withAlpha(0xFFFF7A2C, 44), 0x00FF5A20},
                new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(crater);
        canvas.drawCircle(cx, cy, w * 0.105f, paint);
        paint.setShader(null);
    }

    private void drawSmoke(Canvas canvas, int w, int h) {
        for (int i = 0; i < SMOKE; i++) {
            float cx = w * smokeX[i];
            float cy = h * smokeY[i];
            float radius = w * smokeR[i];
            int alpha = (int) (smokeA[i] * 255 * (0.55f + intensity * 0.45f));
            RadialGradient gradient = new RadialGradient(cx, cy, radius,
                    new int[]{withAlpha(0xFF3A211A, alpha), withAlpha(0xFF3A211A, 0)},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP);
            paint.setShader(gradient);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setShader(null);
        }
    }

    private void drawEmbers(Canvas canvas, int w, int h) {
        for (int i = 0; i < EMBERS; i++) {
            float x = emberX[i] * w;
            float y = emberY[i] * h;
            int alpha = (int) (emberA[i] * 255 * intensity);
            paint.setColor(withAlpha(0xFFFFB05E, alpha));
            canvas.drawCircle(x, y, emberS[i], paint);
            paint.setColor(withAlpha(0xFFFFF0C4, alpha / 2));
            canvas.drawCircle(x, y, emberS[i] * 0.52f, paint);
        }
        paint.setAlpha(255);
    }

    private void drawGroundGlow(Canvas canvas, int w, int h) {
        RadialGradient ground = new RadialGradient(w * 0.5f, h * 0.93f, w * 0.52f,
                new int[]{withAlpha(0xFFFF6A26, (int) (56 * intensity)), 0x00FF4A18},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(ground);
        canvas.drawRect(0, h * 0.76f, w, h, paint);
        paint.setShader(null);
    }

    private static int withAlpha(int color, int alpha) {
        return android.graphics.Color.argb(Math.max(0, Math.min(255, alpha)),
                android.graphics.Color.red(color),
                android.graphics.Color.green(color),
                android.graphics.Color.blue(color));
    }
}
