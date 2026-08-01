package com.moodtree.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** HSV 取色器：色相条（横向渐变 0-360°）+ 饱和度/明度 2D 面 + 预览色块 + HEX 显示。
 *  触摸交互：拖拽色相条取色 → SV 面同步更新；拖拽 SV 面调整饱和度/明度。
 *  回调接口 OnColorChangeListener。 */
public class ColorPickerView extends View {

    public interface OnColorChangeListener {
        void onColorChanged(int color, String hex);
    }

    // ---- 布局参数 ----
    private static final float HUE_BAR_HEIGHT_DP = 36f;
    private static final float SV_SQUARE_SIZE_DP = 240f;
    private static final float PREVIEW_SIZE_DP = 48f;
    private static final float PADDING_DP = 16f;
    private static final float HANDLE_RADIUS_DP = 8f;
    private static final float CORNER_RADIUS_DP = 10f;

    // ---- 当前颜色 ----
    private float hue = 0f;           // 0-360
    private float saturation = 1f;    // 0-1
    private float brightness = 1f;    // 0-1
    private int currentColor = Color.RED;
    private String currentHex = "#FF0000";

    private OnColorChangeListener listener;

    // ---- 绘制区域 ----
    private RectF hueBarRect = new RectF();
    private RectF svSquareRect = new RectF();
    private RectF previewRect = new RectF();
    private float density;
    private float padding, hueBarHeight, svSquareSize, previewSize, handleRadius, cornerRadius;

    // ---- 交互状态 ----
    private enum DragTarget { NONE, HUE_BAR, SV_SQUARE }
    private DragTarget dragTarget = DragTarget.NONE;

    // ---- 画笔 ----
    private Paint hueBarPaint;
    private Paint svSquarePaint;
    private Paint svWhitePaint;
    private Paint svBlackPaint;
    private Paint handlePaint;
    private Paint previewPaint;
    private Paint previewBorderPaint;
    private Paint hexPaint;
    private Paint bgPaint;

    // 缓存 sv 面的 bitmap shader
    private android.graphics.Bitmap svBitmap;
    private boolean svDirty = true;

    public ColorPickerView(Context context) {
        super(context);
        init();
    }

    public ColorPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ColorPickerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        padding = dp(PADDING_DP);
        hueBarHeight = dp(HUE_BAR_HEIGHT_DP);
        svSquareSize = dp(SV_SQUARE_SIZE_DP);
        previewSize = dp(PREVIEW_SIZE_DP);
        handleRadius = dp(HANDLE_RADIUS_DP);
        cornerRadius = dp(CORNER_RADIUS_DP);

        hueBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        svWhitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        svBlackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeWidth(dp(2.5f));
        handlePaint.setColor(0xFFFFFFFF);

        previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        previewBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        previewBorderPaint.setStyle(Paint.Style.STROKE);
        previewBorderPaint.setStrokeWidth(dp(1));
        previewBorderPaint.setColor(0x33000000);

        hexPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hexPaint.setColor(Color.parseColor("#666666"));
        hexPaint.setTextSize(dp(14));
        hexPaint.setTextAlign(Paint.Align.CENTER);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0x0A000000);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) (padding * 2 + cornerRadius + hueBarHeight + dp(12)
                + svSquareSize + dp(12) + previewSize + dp(20));
        setMeasuredDimension(width, Math.max(height, getSuggestedMinimumHeight()));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float contentWidth = w - padding * 2;

        // 色相条：顶部
        float hueTop = padding + cornerRadius;
        hueBarRect.set(padding, hueTop, padding + contentWidth, hueTop + hueBarHeight);

        // SV 面：色相条下方
        float svTop = hueBarRect.bottom + dp(12);
        float svSize = Math.min(contentWidth, svSquareSize);
        float svLeft = (w - svSize) / 2f;
        svSquareRect.set(svLeft, svTop, svLeft + svSize, svTop + svSize);

        // 预览色块：SV 面下方
        float previewTop = svSquareRect.bottom + dp(12);
        previewRect.set(padding, previewTop, padding + previewSize, previewTop + previewSize);

        svDirty = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 背景圆角矩形
        RectF bgRect = new RectF(padding, padding, getWidth() - padding, getHeight() - padding);
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint);

        // 1. 色相条
        drawHueBar(canvas);

        // 2. SV 面
        drawSVDiamond(canvas);

        // 3. 预览色块 + HEX
        drawPreview(canvas);
    }

    private void drawHueBar(Canvas canvas) {
        int[] colors = new int[360];
        for (int i = 0; i < 360; i++) {
            colors[i] = Color.HSVToColor(new float[]{i, 1f, 1f});
        }
        float[] positions = new float[360];
        for (int i = 0; i < 360; i++) {
            positions[i] = (float) i / 359f;
        }
        hueBarPaint.setShader(new LinearGradient(
                hueBarRect.left, 0, hueBarRect.right, 0,
                colors, positions, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(hueBarRect, cornerRadius, cornerRadius, hueBarPaint);
        hueBarPaint.setShader(null);

        // 色相条手柄
        float handleX = hueBarRect.left + (hue / 360f) * hueBarRect.width();
        float handleY = hueBarRect.centerY();
        handlePaint.setColor(0xFFFFFFFF);
        canvas.drawCircle(handleX, handleY, handleRadius + dp(1), handlePaint);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(currentColor);
        canvas.drawCircle(handleX, handleY, handleRadius, handlePaint);
        handlePaint.setStyle(Paint.Style.STROKE);
    }

    private void drawSVDiamond(Canvas canvas) {
        if (svDirty) {
            buildSVBitmap();
            svDirty = false;
        }
        if (svBitmap != null) {
            canvas.drawRoundRect(svSquareRect, cornerRadius, cornerRadius, svSquarePaint);
            canvas.drawBitmap(svBitmap, null, svSquareRect, null);
        }

        // 边框
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1));
        borderPaint.setColor(0x22000000);
        canvas.drawRoundRect(svSquareRect, cornerRadius, cornerRadius, borderPaint);

        // 手柄
        float sx = svSquareRect.left + saturation * svSquareRect.width();
        float sy = svSquareRect.top + (1f - brightness) * svSquareRect.height();
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(0x00000000);
        canvas.drawCircle(sx, sy, handleRadius + dp(2), handlePaint);
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setColor(0xFFFFFFFF);
        canvas.drawCircle(sx, sy, handleRadius + dp(1), handlePaint);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(currentColor);
        canvas.drawCircle(sx, sy, handleRadius, handlePaint);
        handlePaint.setStyle(Paint.Style.STROKE);
    }

    private void buildSVBitmap() {
        if (svSquareRect.width() <= 0 || svSquareRect.height() <= 0) return;
        int w = (int) svSquareRect.width();
        int h = (int) svSquareRect.height();
        svBitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);

        int[] pixels = new int[w * h];
        float hueBase = hue;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float s = (float) x / w;
                float v = 1f - (float) y / h;
                pixels[y * w + x] = Color.HSVToColor(new float[]{hueBase, s, v});
            }
        }
        svBitmap.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    private void drawPreview(Canvas canvas) {
        // 预览色块
        canvas.drawRoundRect(previewRect, cornerRadius, cornerRadius, previewPaint);
        canvas.drawRoundRect(previewRect, cornerRadius, cornerRadius, previewBorderPaint);

        // HEX 文本
        float hexX = previewRect.right + dp(12);
        float hexY = previewRect.centerY() + dp(5);
        canvas.drawText(currentHex, hexX, hexY, hexPaint);
    }

    // ========== 触摸交互 ==========

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (hueBarRect.contains(x, y)) {
                    dragTarget = DragTarget.HUE_BAR;
                    updateHue(x);
                    return true;
                } else if (svSquareRect.contains(x, y)) {
                    dragTarget = DragTarget.SV_SQUARE;
                    updateSV(x, y);
                    return true;
                }
                return false;

            case MotionEvent.ACTION_MOVE:
                if (dragTarget == DragTarget.HUE_BAR) {
                    updateHue(x);
                    return true;
                } else if (dragTarget == DragTarget.SV_SQUARE) {
                    updateSV(x, y);
                    return true;
                }
                return false;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragTarget = DragTarget.NONE;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateHue(float x) {
        float relX = Math.max(0, Math.min(x - hueBarRect.left, hueBarRect.width()));
        hue = (relX / hueBarRect.width()) * 360f;
        if (hue >= 360f) hue = 359f;
        onColorUpdated();
        svDirty = true;
        invalidate();
    }

    private void updateSV(float x, float y) {
        float relX = Math.max(0, Math.min(x - svSquareRect.left, svSquareRect.width()));
        float relY = Math.max(0, Math.min(y - svSquareRect.top, svSquareRect.height()));
        saturation = relX / svSquareRect.width();
        brightness = 1f - relY / svSquareRect.height();
        onColorUpdated();
        invalidate();
    }

    private void onColorUpdated() {
        currentColor = Color.HSVToColor(new float[]{hue, saturation, brightness});
        currentHex = String.format("#%06X", currentColor & 0xFFFFFF);
        previewPaint.setColor(currentColor);
        if (listener != null) {
            listener.onColorChanged(currentColor, currentHex);
        }
    }

    // ========== 公开 API ==========

    /** 设置颜色变化监听 */
    public void setOnColorChangeListener(OnColorChangeListener l) {
        this.listener = l;
    }

    /** 设置当前颜色（从外部初始化用） */
    public void setColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        this.hue = hsv[0];
        this.saturation = hsv[1];
        this.brightness = hsv[2];
        onColorUpdated();
        svDirty = true;
        invalidate();
    }

    /** 从 HEX 字符串设置颜色 */
    public void setColor(String hex) {
        try {
            setColor(Color.parseColor(hex));
        } catch (Exception ignored) { }
    }

    /** 获取当前颜色 int */
    public int getColor() { return currentColor; }

    /** 获取当前颜色 HEX */
    public String getHex() { return currentHex; }

    /** 获取当前色相 */
    public float getHue() { return hue; }

    /** 获取当前饱和度 */
    public float getSaturation() { return saturation; }

    /** 获取当前明度 */
    public float getBrightness() { return brightness; }

    private float dp(float v) {
        return v * density;
    }
}