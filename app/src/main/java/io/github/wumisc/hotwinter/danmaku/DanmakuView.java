package io.github.wumisc.hotwinter.danmaku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** 弹幕画布：按车道从右往左滚，字号/速度/透明度/车道数都来自设置。 */
public final class DanmakuView extends View {

    private static final int MAX_ITEMS = 40;
    private static final int[] PALETTE = {
            0xFFFFF3E8, 0xFFFFC1A8, 0xFFFFE0A3, 0xFFB7E3FF,
            0xFFC9F4D9, 0xFFE7D4FF, 0xFFFFD0E2
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Item> items = new ArrayList<>();
    private final DmSettings settings;
    private long lastFrame;
    private int cursor;

    private static final class Item {
        final String text;
        final String nick;
        final int color;
        final float speed;
        final int lane;
        float x;
        float width;

        Item(String text, String nick, int color, float speed, int lane) {
            this.text = text;
            this.nick = nick;
            this.color = color;
            this.speed = speed;
            this.lane = lane;
        }
    }

    public DanmakuView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        settings = new DmSettings(context);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setStyle(Paint.Style.FILL);
        paint.setShadowLayer(6f, 0f, 2f, 0xCC000000);
        setWillNotDraw(false);
    }

    public void push(DmMessage message) {
        if (message == null) return;
        float textSize = settings.fontSizeSp() * getResources().getDisplayMetrics().scaledDensity;
        paint.setTextSize(textSize);
        String label = settings.showNick() && !message.nick.isEmpty()
                ? message.nick + "：" + message.text
                : message.text;
        Item item = new Item(label, message.nick, PALETTE[(cursor++) % PALETTE.length],
                textSize * 1.6f * settings.speed(),
                cursor % Math.max(2, settings.lanes()));
        item.width = paint.measureText(item.text);
        item.x = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        items.add(item);
        if (items.size() > MAX_ITEMS) items.remove(0);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;

        float density = getResources().getDisplayMetrics().density;
        float textSize = settings.fontSizeSp() * getResources().getDisplayMetrics().scaledDensity;
        paint.setTextSize(textSize);
        paint.setAlpha(Math.round(settings.alpha() * 255));

        int lanes = Math.max(2, settings.lanes());
        float laneHeight = Math.max(textSize * 1.55f, 18f * density);
        float top = 8f * density;
        float width = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;

        Iterator<Item> iterator = items.iterator();
        while (iterator.hasNext()) {
            Item item = iterator.next();
            item.x -= item.speed * dt;
            if (item.x + item.width < -8f) {
                iterator.remove();
            }
        }

        for (Item item : items) {
            float baseline = top + (item.lane % lanes) * laneHeight + textSize;
            paint.setColor(item.color);
            canvas.drawText(item.text, item.x, baseline, paint);
        }

        if (!items.isEmpty() && isAttachedToWindow()) {
            postInvalidateDelayed(33L);
        }
    }

    public void clear() {
        items.clear();
        invalidate();
    }
}
