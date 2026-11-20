package io.github.wumisc.hotwinter.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import io.github.wumisc.hotwinter.R;

/** 实时温度曲线：最近 60 个采样点，能直观看到「到底在不在升温」。 */
public final class SparkView extends View {

    private static final int CAPACITY = 60;

    private final float[] values = new float[CAPACITY];
    private int count;
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public SparkView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(1.6f));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void push(float value) {
        if (Float.isNaN(value) || value <= 0f) return;
        if (count == CAPACITY) {
            System.arraycopy(values, 1, values, 0, CAPACITY - 1);
            values[CAPACITY - 1] = value;
        } else {
            values[count++] = value;
        }
        invalidate();
    }

    public void reset() {
        count = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || count < 2) {
            if (count == 1) {
                dotPaint.setColor(ContextCompat.getColor(getContext(), R.color.accent));
                canvas.drawCircle(dp(4), h - dp(4), dp(2.4f), dotPaint);
            }
            return;
        }

        float min = values[0];
        float max = values[0];
        for (int i = 1; i < count; i++) {
            min = Math.min(min, values[i]);
            max = Math.max(max, values[i]);
        }
        if (max - min < 0.4f) {
            float mid = (max + min) * 0.5f;
            min = mid - 0.2f;
            max = mid + 0.2f;
        }

        float pad = dp(4);
        float usableW = w - pad * 2;
        float usableH = h - pad * 2;
        path.reset();
        for (int i = 0; i < count; i++) {
            float x = pad + usableW * i / (float) (CAPACITY - 1);
            float y = pad + usableH * (1f - (values[i] - min) / (max - min));
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }

        int accent = ContextCompat.getColor(getContext(), R.color.accent);
        fillPaint.setShader(new LinearGradient(0, 0, 0, h,
                withAlpha(accent, 0x33), withAlpha(accent, 0x05), Shader.TileMode.CLAMP));
        canvas.drawPath(path, fillPaint);
        fillPaint.setShader(null);

        linePaint.setColor(accent);
        canvas.drawPath(path, linePaint);

        float lastX = pad + usableW * (count - 1) / (float) (CAPACITY - 1);
        float lastY = pad + usableH * (1f - (values[count - 1] - min) / (max - min));
        dotPaint.setColor(accent);
        canvas.drawCircle(lastX, lastY, dp(3), dotPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }
}
