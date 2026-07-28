package com.moodtree.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** 情绪叠色层：用心情色绘制径向渐变（顶部居中），透明度由强度控制。鼠标穿透，不影响点击。 */
public class MoodOverlayView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int moodColor = Color.TRANSPARENT;
    private float opacity;

    public MoodOverlayView(Context context) { super(context); }
    public MoodOverlayView(Context context, AttributeSet attrs) { super(context, attrs); }
    public MoodOverlayView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    public void setMoodColor(int color, float opacity) {
        this.moodColor = color;
        this.opacity = opacity;
        invalidate();
    }

    public void clear() {
        moodColor = Color.TRANSPARENT;
        opacity = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (moodColor == Color.TRANSPARENT || opacity <= 0) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        int alpha = Math.min(255, Math.max(0, (int) (opacity * 255)));
        int c = Color.argb(alpha, Color.red(moodColor), Color.green(moodColor), Color.blue(moodColor));
        int t = Color.argb(0, Color.red(moodColor), Color.green(moodColor), Color.blue(moodColor));

        RadialGradient g = new RadialGradient(w / 2f, 0, w * 0.6f,
                new int[]{c, t}, new float[]{0f, 0.7f}, Shader.TileMode.CLAMP);
        paint.setShader(g);
        canvas.drawRect(0, 0, w, h, paint);
    }
}