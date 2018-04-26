package com.mcivicm.media.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

/**
 * 矩阵视图
 */

public class MatrixView extends View {

    private RectF src = new RectF(0, 0, 0, 0);
    private RectF dst = new RectF(0, 0, 0, 0);
    private Paint paint = new Paint();

    public MatrixView(Context context) {
        super(context);
    }

    public MatrixView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MatrixView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MatrixView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.RED);
        canvas.drawRect(src, paint);
        paint.setColor(Color.GREEN);
        canvas.drawRect(dst, paint);
    }

    public void setSrc(float left, float top, float right, float bottom) {
        src.set(left, top, right, bottom);
        invalidate();
    }

    public void setSrc(RectF s) {
        src.set(s.left, s.top, s.right, s.bottom);
        invalidate();
    }

    public void setDst(float left, float top, float right, float bottom) {
        dst.set(left, top, right, bottom);
        invalidate();
    }

    public void setDst(RectF d) {
        dst.set(d.left, d.top, d.right, d.bottom);
        invalidate();
    }


}
