package com.moodtree.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.GridView;

/** GridView 重写 onMeasure 以支持 wrap_content：测量所有行，不用滚动。 */
public class SquareGridGridView extends GridView {
    public SquareGridGridView(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int expandSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 2, MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, expandSpec);
    }
}