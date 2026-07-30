package com.moodtree.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/** 正方形 FrameLayout：onMeasure 强制宽高相等（1:1），用于日历格子。 */
public class SquareFrameLayout extends FrameLayout {
    public SquareFrameLayout(Context context) { super(context); }
    public SquareFrameLayout(Context context, AttributeSet attrs) { super(context, attrs); }
    public SquareFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }
}