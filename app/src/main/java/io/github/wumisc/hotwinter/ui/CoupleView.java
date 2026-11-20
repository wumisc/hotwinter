package io.github.wumisc.hotwinter.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 坐在悬浮导航栏上的情侣挂件。
 *
 * 手绘矢量小人：毛衣、围巾、头发、眼睛高光、腮红、悬空的小腿和鞋；
 * 待机时轻轻呼吸晃动，发热时两人靠在一起、手臂内收、头顶冒爱心。
 */
public final class CoupleView extends View {

    private static final float DESIGN_W = 240f;
    private static final float DESIGN_H = 170f;
    private static final float BASELINE = 96f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final RectF rect2 = new RectF();

    private float warm;
    private float time;
    private long lastFrame;

    public CoupleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setWarm(boolean isWarm, float intensity) {
        warm = isWarm ? Math.max(0.3f, Math.min(1f, intensity)) : 0f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        time += dt;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float scale = Math.min(w / DESIGN_W, h / DESIGN_H);
        float offsetX = (w - DESIGN_W * scale) * 0.5f;
        float offsetY = (h - DESIGN_H * scale) * 0.5f;
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        drawShadow(canvas);
        drawPerson(canvas, 96f, true);
        drawPerson(canvas, 144f, false);
        drawHearts(canvas);

        canvas.restore();

        if (isAttachedToWindow() && getVisibility() == VISIBLE) {
            postInvalidateDelayed(33L);
        }
    }

    private void drawShadow(Canvas canvas) {
        float breathe = 1f + (float) Math.sin(time * 1.2f) * 0.04f;
        rect.set(52f, BASELINE + 24f, 188f, BASELINE + 40f);
        paint.setShader(new RadialGradient(120f, BASELINE + 32f, 66f * breathe,
                0x33000000, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawOval(rect, paint);
        paint.setShader(null);
    }

    /** origin 是屁股落点；左右两人镜像摆放。 */
    private void drawPerson(Canvas canvas, float cx, boolean isGirl) {
        float breathe = (float) Math.sin(time * 1.3f + (isGirl ? 0f : 1.6f)) * 1.2f;
        float lean = isGirl ? warm * 6f : -warm * 6f;

        int sweater = isGirl ? 0xFFF27A5A : 0xFF5E8FD2;
        int sweaterDark = isGirl ? 0xFFC85B3F : 0xFF41689E;
        int pants = 0xFF3A4052;
        int shoe = 0xFF241D1B;
        int skin = 0xFFFFD8BE;
        int skinShade = 0xFFEFBE9E;
        int hair = isGirl ? 0xFF452921 : 0xFF241A15;

        canvas.save();
        canvas.translate(cx, BASELINE + breathe);

        // 小腿 + 鞋（悬空，垂在导航栏前面）
        drawRoundRect(canvas, -19f, 4f, -2f, 32f, 8f, pants);
        drawRoundRect(canvas, 2f, 4f, 19f, 32f, 8f, pants);
        drawRoundRect(canvas, -20f, 26f, -3f, 39f, 6f, shoe);
        drawRoundRect(canvas, 3f, 26f, 20f, 39f, 6f, shoe);
        paint.setColor(0x22000000);
        rect.set(-21f, 35f, -2f, 41f);
        canvas.drawOval(rect, paint);
        rect.set(2f, 35f, 21f, 41f);
        canvas.drawOval(rect, paint);

        // 毛衣
        path.reset();
        path.moveTo(-25f, -36f);
        path.quadTo(-30f, -8f, -24f, 4f);
        path.quadTo(0f, 12f, 24f, 4f);
        path.quadTo(30f, -8f, 25f, -36f);
        path.quadTo(12f, -46f, 0f, -46f);
        path.quadTo(-12f, -46f, -25f, -36f);
        path.close();
        paint.setShader(new LinearGradient(-26f, -46f, 26f, 6f,
                lighten(sweater, 0.12f), sweaterDark, Shader.TileMode.CLAMP));
        canvas.drawPath(path, paint);
        paint.setShader(null);

        drawRoundRect(canvas, -24f, -3f, 24f, 5f, 2.5f, sweaterDark);
        paint.setColor(0x22000000);
        rect.set(-13f, -20f, 13f, -9f);
        canvas.drawOval(rect, paint);

        // 手臂：发热时往中间收
        float armIn = warm * 9f;
        if (isGirl) {
            drawLimb(canvas, -24f, -34f, -31f, -12f, 8f, sweater);
            drawCircle(canvas, -31f, -10f, 5f, skin);
            drawLimb(canvas, 24f, -34f, 27f + armIn * 0.5f, -13f, 8f, sweater);
            drawCircle(canvas, 27f + armIn * 0.5f, -11f, 5f, skin);
        } else {
            drawLimb(canvas, -24f, -34f, -27f - armIn * 0.5f, -13f, 8f, sweater);
            drawCircle(canvas, -27f - armIn * 0.5f, -11f, 5f, skin);
            drawLimb(canvas, 24f, -34f, 31f, -12f, 8f, sweater);
            drawCircle(canvas, 31f, -10f, 5f, skin);
        }

        // 头（含倾斜）
        float headY = -74f;
        canvas.save();
        canvas.translate(lean, 0f);
        canvas.rotate(isGirl ? warm * 4f : -warm * 4f, 0f, headY);

        drawRoundRect(canvas, -7f, headY + 18f, 7f, headY + 30f, 4f, skinShade);

        if (isGirl) {
            path.reset();
            path.moveTo(-24f, headY + 2f);
            path.quadTo(-31f, headY + 28f, -22f, headY + 35f);
            path.quadTo(0f, headY + 39f, 22f, headY + 35f);
            path.quadTo(31f, headY + 28f, 24f, headY + 2f);
            path.close();
            paint.setColor(hair);
            canvas.drawPath(path, paint);
        }

        paint.setShader(new RadialGradient(-6f, headY - 8f, 30f,
                lighten(skin, 0.10f), skinShade, Shader.TileMode.CLAMP));
        canvas.drawCircle(0f, headY, 23f, paint);
        paint.setShader(null);
        drawCircle(canvas, -21f, headY + 2f, 4.5f, skin);
        drawCircle(canvas, 21f, headY + 2f, 4.5f, skin);

        // 刘海
        path.reset();
        if (isGirl) {
            path.moveTo(-24f, headY - 6f);
            path.quadTo(-18f, headY - 24f, 0f, headY - 24f);
            path.quadTo(18f, headY - 24f, 24f, headY - 6f);
            path.quadTo(14f, headY - 14f, 6f, headY - 10f);
            path.quadTo(0f, headY - 16f, -6f, headY - 10f);
            path.quadTo(-14f, headY - 14f, -24f, headY - 6f);
        } else {
            path.moveTo(-24f, headY - 5f);
            path.quadTo(-20f, headY - 26f, 0f, headY - 25f);
            path.quadTo(20f, headY - 24f, 24f, headY - 4f);
            path.quadTo(16f, headY - 16f, 8f, headY - 13f);
            path.quadTo(-2f, headY - 22f, -10f, headY - 12f);
            path.quadTo(-18f, headY - 8f, -24f, headY - 5f);
        }
        path.close();
        paint.setColor(hair);
        canvas.drawPath(path, paint);

        if (isGirl) {
            drawRoundRect(canvas, 18f, headY - 6f, 24f, headY + 22f, 4f, hair);
            drawCircle(canvas, -16f, headY - 18f, 4f, 0xFFFFC9B0);
        }

        // 眼睛：每 4 秒眨一下
        boolean blink = (time % 4f) > 3.72f;
        if (blink) {
            paint.setColor(0xFF3A2418);
            paint.setStrokeWidth(1.6f);
            canvas.drawLine(-13f, headY + 1f, -7f, headY + 1f, paint);
            canvas.drawLine(7f, headY + 1f, 13f, headY + 1f, paint);
        } else {
            paint.setColor(0xFF3A2418);
            rect.set(-13f, headY - 4f, -6f, headY + 5f);
            canvas.drawOval(rect, paint);
            rect.set(6f, headY - 4f, 13f, headY + 5f);
            canvas.drawOval(rect, paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(-10f, headY - 2f, 1.6f, paint);
            canvas.drawCircle(9f, headY - 2f, 1.6f, paint);
            paint.setColor(0xFFFFC8C8);
            canvas.drawCircle(-8.5f, headY + 3f, 0.9f, paint);
            canvas.drawCircle(10.5f, headY + 3f, 0.9f, paint);
        }

        paint.setColor(0xCC5A3A28);
        paint.setStrokeWidth(1.2f);
        canvas.drawLine(-13f, headY - 8f, -7f, headY - 9.5f, paint);
        canvas.drawLine(7f, headY - 9.5f, 13f, headY - 8f, paint);

        paint.setColor(0x66C08060);
        canvas.drawCircle(0f, headY + 5f, 0.9f, paint);
        paint.setColor(0xFFB25C55);
        paint.setStrokeWidth(1.3f);
        rect2.set(-4f, headY + 6f, 4f, headY + 12f);
        canvas.drawArc(rect2, 15f, 150f, false, paint);

        paint.setColor(withAlpha(0xFFFF9A9E, 0x66 + (int) (warm * 0x55)));
        rect.set(-19f, headY + 5f, -9f, headY + 11f);
        canvas.drawOval(rect, paint);
        rect.set(9f, headY + 5f, 19f, headY + 11f);
        canvas.drawOval(rect, paint);

        canvas.restore();

        if (isGirl) {
            drawRoundRect(canvas, -13f, headY + 20f, 13f, headY + 30f, 4f, 0xFFFFB6A3);
            drawRoundRect(canvas, 8f, headY + 26f, 15f, headY + 38f, 3f, 0xFFFFB6A3);
        }

        canvas.restore();
    }

    private void drawHearts(Canvas canvas) {
        if (warm < 0.2f) return;
        float phase = (float) ((time * 0.4f) % 1f);
        for (int i = 0; i < 3; i++) {
            float p = (phase + i * 0.33f) % 1f;
            float x = 120f + (i - 1) * 6f + (float) Math.sin(time + i) * 3f;
            float y = 44f - p * 28f;
            float size = 3.6f + (i % 2);
            int alpha = (int) (warm * 170 * (1f - p));
            path.reset();
            path.moveTo(x, y + size);
            path.cubicTo(x + size * 1.5f, y - size * 0.4f,
                    x + size * 0.6f, y - size * 1.2f, x, y - size * 0.45f);
            path.cubicTo(x - size * 0.6f, y - size * 1.2f,
                    x - size * 1.5f, y - size * 0.4f, x, y + size);
            paint.setColor(withAlpha(0xFFFF6A8A, alpha));
            canvas.drawPath(path, paint);
            paint.setColor(withAlpha(0xFFFFC2D0, alpha / 2));
            canvas.drawCircle(x - size * 0.4f, y - size * 0.5f, size * 0.35f, paint);
        }
    }

    private void drawLimb(Canvas canvas, float x1, float y1, float x2, float y2, float width, int color) {
        paint.setShader(null);
        paint.setColor(color);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(x1, y1, x2, y2, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawCircle(Canvas canvas, float x, float y, float radius, int color) {
        paint.setShader(null);
        paint.setColor(color);
        canvas.drawCircle(x, y, radius, paint);
    }

    private void drawRoundRect(Canvas canvas, float l, float t, float r, float b, float radius, int color) {
        paint.setShader(null);
        paint.setColor(color);
        rect.set(l, t, r, b);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }

    private static int lighten(int color, float amount) {
        int r = (int) (Color.red(color) + (255 - Color.red(color)) * amount);
        int g = (int) (Color.green(color) + (255 - Color.green(color)) * amount);
        int b = (int) (Color.blue(color) + (255 - Color.blue(color)) * amount);
        return Color.argb(Color.alpha(color), r, g, b);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }
}
