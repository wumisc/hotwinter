package io.github.wumisc.hotwinter.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import io.github.wumisc.hotwinter.R;

/**
 * 首页背景容器：待机是雪山，发热是火山，两套独立绘制的场景交叉淡入淡出，
 * 最后统一盖一层渐变蒙版，保证正文在任何背景下都读得清。
 */
public final class SceneryView extends FrameLayout {

    private final SnowSceneView snow;
    private final VolcanoSceneView volcano;
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader scrimShader;
    private ValueAnimator animator;
    private float progress;

    public SceneryView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        snow = new SnowSceneView(context);
        volcano = new VolcanoSceneView(context);
        volcano.setAlpha(0f);
        addView(snow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(volcano, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setWarm(boolean warm, boolean animate) {
        float target = warm ? 1f : 0f;
        if (animator != null) animator.cancel();
        if (!animate) {
            apply(target);
            return;
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(1000L);
        animator.addUpdateListener(a -> apply((float) a.getAnimatedValue()));
        animator.start();
    }

    public void setGlow(float value) {
        volcano.setIntensity(value);
        snow.setWind(value);
    }

    private void apply(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        snow.setAlpha(1f - progress);
        volcano.setAlpha(progress);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int base = ContextCompat.getColor(getContext(), R.color.bg);
        scrimShader = new LinearGradient(0, 0, 0, h,
                new int[]{withAlpha(base, 0x2E), withAlpha(base, 0x62), withAlpha(base, 0xB4)},
                new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (scrimShader == null) return;
        scrimPaint.setShader(scrimShader);
        canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
        scrimPaint.setShader(null);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }
}
