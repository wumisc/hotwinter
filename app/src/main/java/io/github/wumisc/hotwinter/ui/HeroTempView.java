package io.github.wumisc.hotwinter.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 首页大字温度：数字带渐变、外圈有一层随状态呼吸的光晕，
 * 数值变化时做一个短促的爬升动画，比静态 TextView 更有「在发热」的感觉。
 */
public final class HeroTempView extends View {

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint degreePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float targetValue = 36f;
    private float shownValue = 36f;
    private boolean hasValue;
    private boolean running;
    private float phase;
    private long lastFrame;

    public HeroTempView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        numberPaint.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        numberPaint.setTextAlign(Paint.Align.CENTER);
        numberPaint.setStyle(Paint.Style.FILL);
        numberPaint.setShadowLayer(14f, 0f, 6f, 0x33000000);

        degreePaint.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        degreePaint.setTextAlign(Paint.Align.LEFT);
        degreePaint.setStyle(Paint.Style.FILL);

        captionPaint.setTextAlign(Paint.Align.CENTER);
        captionPaint.setTextSize(dp(11));
        captionPaint.setColor(0xAAFFFFFF);
        captionPaint.setShadowLayer(6f, 0f, 2f, 0x66000000);
    }

    public void setTemperature(float celsius, boolean isRunning) {
        running = isRunning;
        if (celsius > 0f && celsius < 80f) {
            targetValue = celsius;
            if (!hasValue) {
                shownValue = celsius;
                hasValue = true;
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        phase += dt;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 数字平滑逼近，避免每次刷新跳一下
        float diff = targetValue - shownValue;
        shownValue += diff * Math.min(1f, dt * 8f);
        if (Math.abs(diff) < 0.05f) shownValue = targetValue;

        boolean warm = running;
        int hot = 0xFFFF7043;
        int cold = 0xFF5C8FD6;

        // 背后的呼吸光晕
        float pulse = 1f + (warm ? (float) Math.sin(phase * 1.8f) * 0.05f : 0f);
        float radius = Math.min(w, h) * 0.62f * pulse;
        int glowColor = warm ? withAlpha(hot, 0x30) : withAlpha(cold, 0x1C);
        glowPaint.setShader(new RadialGradient(w / 2f, h / 2f, radius,
                glowColor, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawCircle(w / 2f, h / 2f, radius, glowPaint);
        glowPaint.setShader(null);

        String number = hasValue ? String.valueOf(Math.round(shownValue)) : "—";
        float size = Math.min(h * 0.66f, w * 0.52f);
        numberPaint.setTextSize(size);
        degreePaint.setTextSize(size * 0.36f);

        float numberWidth = numberPaint.measureText(number);
        float degreeWidth = degreePaint.measureText("°");
        float total = numberWidth + degreeWidth * 0.55f;
        float baseY = h / 2f - (numberPaint.ascent() + numberPaint.descent()) / 2f + size * 0.06f;
        float numberX = w / 2f - total / 2f + numberWidth / 2f;

        numberPaint.setShader(new LinearGradient(0, baseY + numberPaint.ascent(),
                0, baseY + numberPaint.descent(),
                warm ? new int[]{0xFFFFE2B8, 0xFFFF7043} : new int[]{0xFFE8F3FF, 0xFF5C8FD6},
                null, Shader.TileMode.CLAMP));
        canvas.drawText(number, numberX, baseY, numberPaint);
        numberPaint.setShader(null);

        degreePaint.setShader(new LinearGradient(0, baseY - size * 0.6f, 0, baseY,
                warm ? new int[]{0xFFFFC79A, 0xFFFF8A5C} : new int[]{0xFFD7E9FF, 0xFF7FA9D8},
                null, Shader.TileMode.CLAMP));
        canvas.drawText("°", numberX + numberWidth / 2f + degreeWidth * 0.18f, baseY - size * 0.30f, degreePaint);
        degreePaint.setShader(null);

        if (!running) {
            captionPaint.setColor(withAlpha(cold, 0x66));
            canvas.drawText("雪 山 待 机", w / 2f, h - dp(6), captionPaint);
        } else {
            captionPaint.setColor(withAlpha(hot, 0x88));
            canvas.drawText("火 山 加 热 中", w / 2f, h - dp(6), captionPaint);
        }

        if (isAttachedToWindow() && (running || Math.abs(targetValue - shownValue) > 0.05f)) {
            postInvalidateDelayed(33L);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }
}
