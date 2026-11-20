package io.github.wumisc.hotwinter.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

import io.github.wumisc.hotwinter.R;

/**
 * 雪山实景层：一张透明背景的雪山照片作为主体，天空、月色、极光、星点与薄雾
 * 都是静态氛围层。只在尺寸变化、状态切换时重绘，不再持续 30fps 刷全屏，
 * 从根上避免首页背景闪烁。
 */
public final class SnowSceneView extends View {

    private static final int STARS = 54;
    private static final int FLAKES = 26;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Path path = new Path();
    private final RectF dst = new RectF();
    private final float[] starX = new float[STARS];
    private final float[] starY = new float[STARS];
    private final float[] starA = new float[STARS];
    private final float[] flakeX = new float[FLAKES];
    private final float[] flakeY = new float[FLAKES];
    private final float[] flakeA = new float[FLAKES];

    private Bitmap mountain;
    private float wind = 0.4f;

    public SnowSceneView(Context context) {
        this(context, null);
    }

    public SnowSceneView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        // 照片是白天实拍，压暗红绿通道并轻微偏冷，放进夜色里才不像贴纸。
        bitmapPaint.setColorFilter(new ColorMatrixColorFilter(new float[]{
                0.78f, 0f, 0f, 0f, 0f,
                0f, 0.86f, 0f, 0f, 0f,
                0f, 0f, 0.98f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
        }));
        bitmapPaint.setAlpha(238);
        Random random = new Random(773L);
        for (int i = 0; i < STARS; i++) {
            starX[i] = random.nextFloat();
            starY[i] = random.nextFloat() * 0.46f;
            starA[i] = 0.28f + random.nextFloat() * 0.6f;
        }
        for (int i = 0; i < FLAKES; i++) {
            flakeX[i] = random.nextFloat();
            flakeY[i] = 0.58f + random.nextFloat() * 0.36f;
            flakeA[i] = 0.22f + random.nextFloat() * 0.5f;
        }
        mountain = decodeMountain();
    }

    public void setWind(float value) {
        float next = Math.max(0.2f, Math.min(1f, value));
        if (Math.abs(next - wind) < 0.02f) return;
        wind = next;
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
        drawMoon(canvas, w, h);
        drawStars(canvas, w, h);
        drawAurora(canvas, w, h);
        drawMountain(canvas, w, h);
        drawFrost(canvas, w, h);
        drawSnow(canvas, w, h);
    }

    private Bitmap decodeMountain() {
        return BitmapFactory.decodeResource(getResources(), R.drawable.scene_snow);
    }

    private void drawSky(Canvas canvas, int w, int h) {
        paint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF030A12, 0xFF071A28, 0xFF0D2C3E, 0xFF173F53},
                new float[]{0f, 0.38f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }

    private void drawMoon(Canvas canvas, int w, int h) {
        float cx = w * 0.79f;
        float cy = h * 0.13f;
        float radius = w * 0.22f;
        RadialGradient glow = new RadialGradient(cx, cy, radius,
                new int[]{0x66CFEAFF, 0x1FD6F2FF, 0x00D6F2FF},
                new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(glow);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);

        paint.setColor(0xEAF4FCFF);
        canvas.drawCircle(cx, cy, w * 0.030f, paint);
        paint.setColor(0x8AFFFFFF);
        canvas.drawCircle(cx - w * 0.007f, cy - w * 0.007f, w * 0.014f, paint);
    }

    private void drawStars(Canvas canvas, int w, int h) {
        for (int i = 0; i < STARS; i++) {
            paint.setColor(0xFFE9F7FF);
            paint.setAlpha((int) (starA[i] * 210));
            float radius = i % 7 == 0 ? 1.7f : 1.05f;
            canvas.drawCircle(starX[i] * w, starY[i] * h, radius, paint);
        }
        paint.setAlpha(255);
    }

    private void drawAurora(Canvas canvas, int w, int h) {
        for (int band = 0; band < 2; band++) {
            path.reset();
            float baseY = h * (0.17f + band * 0.08f);
            path.moveTo(-w * 0.1f, baseY);
            path.cubicTo(w * 0.22f, baseY - h * 0.075f,
                    w * 0.48f, baseY + h * 0.045f,
                    w * 1.1f, baseY - h * 0.035f);
            paint.setShader(new LinearGradient(0, baseY - h * 0.1f, 0, baseY + h * 0.08f,
                    new int[]{0x0029C8A8, 0x3329C8A8, 0x1F55E4FF, 0x0000E5FF},
                    new float[]{0f, 0.36f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(h * (band == 0 ? 0.042f : 0.028f));
            canvas.drawPath(path, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawMountain(Canvas canvas, int w, int h) {
        if (mountain == null || mountain.isRecycled()) return;
        float drawH = h * 0.66f;
        float aspect = mountain.getWidth() / (float) mountain.getHeight();
        float drawW = drawH * aspect;
        float left = (w - drawW) * 0.5f;
        dst.set(left, h - drawH, left + drawW, h);
        paint.setAlpha(255);
        canvas.drawBitmap(mountain, null, dst, bitmapPaint);
    }

    private void drawFrost(Canvas canvas, int w, int h) {
        // 山脚薄雾：只盖住下半部，避免把雪山照片洗成灰蒙一片。
        paint.setShader(new LinearGradient(0, h * 0.62f, 0, h,
                new int[]{0x00CFEFFF, 0x22CFEFFF, 0x6A0A1D28},
                new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, h * 0.62f, w, h, paint);
        paint.setShader(null);

        // 左侧冷色边缘光，让照片和夜色更贴。
        paint.setShader(new LinearGradient(0, 0, w * 0.38f, 0,
                new int[]{0x40152936, 0x00152936},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w * 0.38f, h, paint);
        paint.setShader(null);
    }

    private void drawSnow(Canvas canvas, int w, int h) {
        for (int i = 0; i < FLAKES; i++) {
            paint.setColor(0xFFF3FAFF);
            paint.setAlpha((int) (flakeA[i] * (0.72f + wind * 0.28f) * 255));
            float radius = i % 5 == 0 ? 2.2f : 1.15f;
            canvas.drawCircle(flakeX[i] * w, flakeY[i] * h, radius, paint);
        }
        paint.setAlpha(255);
    }
}
