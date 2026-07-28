package com.moodtree.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** 雨滴动画容器：生成向下飘落的细长矩形雨滴，难过/孤独/麻木心情时显示。 */
public class RainView extends View implements Runnable {

    private static final long INTERVAL_MS = 120;
    private static final float DROP_W = 2f;
    private static final float DROP_H = 14f;
    private static final int DROP_ALPHA = 50; // ~0.2 opacity
    private static final int DROP_COLOR = Color.argb(DROP_ALPHA, 150, 160, 200);
    private static final float SPEED_MIN = 0.6f;
    private static final float SPEED_MAX = 1.0f;

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<RainDrop> drops = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean running;

    public RainView(Context context) { super(context); init(); }
    public RainView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public RainView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        paint.setColor(DROP_COLOR);
        setWillNotDraw(false);
    }

    public void startRain() {
        if (running) return;
        running = true;
        setVisibility(VISIBLE);
        handler.post(this);
    }

    public void stopRain() {
        running = false;
        handler.removeCallbacks(this);
        drops.clear();
        setVisibility(GONE);
        invalidate();
    }

    @Override
    public void run() {
        if (!running) return;
        // 生成新雨滴
        float w = getWidth();
        if (w <= 0) w = 800;
        float h = getHeight();
        if (h <= 0) h = 1200;
        drops.add(new RainDrop(random.nextFloat() * w, SPEED_MIN + random.nextFloat() * (SPEED_MAX - SPEED_MIN)));

        // 更新位置并移除出屏的
        float dt = INTERVAL_MS / 1000f;
        float speed = h * 0.7f; // 每秒走过70%屏高
        Iterator<RainDrop> it = drops.iterator();
        while (it.hasNext()) {
            RainDrop d = it.next();
            d.y += speed * d.speed * dt;
            if (d.y > h + DROP_H) it.remove();
        }
        invalidate();
        handler.postDelayed(this, INTERVAL_MS);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (RainDrop d : drops) {
            canvas.drawRect(d.x, d.y, d.x + DROP_W, d.y + DROP_H, paint);
        }
    }

    private static class RainDrop {
        final float x;
        final float speed;
        float y = -DROP_H;

        RainDrop(float x, float speed) {
            this.x = x;
            this.speed = speed;
        }
    }
}