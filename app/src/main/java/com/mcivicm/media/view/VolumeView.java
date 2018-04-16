package com.mcivicm.media.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import org.apache.commons.math3.util.MathUtils;

/**
 * 音量视图
 */

public class VolumeView extends RelativeLayout {

    private Paint paint = new Paint();
    private int orientation = 0;
    private RectF rect = new RectF();
    private int edgeColor = Color.parseColor("#40ffffff");
    private int orientationColor = Color.parseColor("#80c0c0c0");

    public VolumeView(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);
    }

    public VolumeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
    }

    public VolumeView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public VolumeView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.TRANSPARENT);
        paint.setColor(edgeColor);
        paint.setAntiAlias(true);
        canvas.drawCircle(getWidth() / 2, getHeight() / 2, Math.min(getWidth() / 2, getHeight() / 2), paint);
        rect.set(0, 0, getWidth(), getHeight());
        paint.setColor(orientationColor);
        canvas.drawArc(rect, -90, orientation, true, paint);
    }

    /**
     * 设置边缘颜色
     *
     * @param edgeColor
     */
    public void setEdgeColor(int edgeColor) {
        this.edgeColor = edgeColor;
    }

    /**
     * 设置方位角颜色
     *
     * @param orientationColor
     */
    public void setOrientationColor(int orientationColor) {
        this.orientationColor = orientationColor;
    }

    /**
     * 设置方位角
     *
     * @param degree
     */
    public void setOrientation(int degree) {
        this.orientation = degree;
        invalidate();
    }

    /**
     * 显示边缘
     */
    public void showEdge() {
        View firstView = getChildAt(0);
        if (firstView != null && getWidth() != firstView.getWidth() + firstView.getWidth() / 10) {
            animateWidth(firstView.getWidth() + firstView.getWidth() / 10, 100);//十分之一的边缘
        }
    }

    /**
     * 隐藏边缘
     */
    public void hideEdge() {
        View firstView = getChildAt(0);
        if (firstView != null) {
            animateWidth(firstView.getWidth(), 100);
        }
    }

    /**
     * 添加宽度
     *
     * @param width
     */
    public void addWidth(int width) {
        View firstView = getChildAt(0);
        if (firstView != null) {
            int startWidth = firstView.getWidth() + firstView.getWidth() / 10;//加上十分之一的边缘
            ViewGroup.LayoutParams layoutParams = getLayoutParams();
            layoutParams.width = startWidth + width;
            layoutParams.height = startWidth + width;
            setLayoutParams(layoutParams);
        }
    }

    /**
     * 添加高度
     *
     * @param height
     */
    public void addHeight(int height) {
        View firstView = getChildAt(0);
        if (firstView != null) {
            int startHeight = firstView.getHeight() + firstView.getHeight() / 10;//加上十分之一的边缘
            ViewGroup.LayoutParams layoutParams = getLayoutParams();
            layoutParams.width = startHeight + height;
            layoutParams.height = startHeight + height;
            setLayoutParams(layoutParams);
        }
    }

    /**
     * 缩放宽
     *
     * @param width
     * @param duration
     */
    public void animateWidth(int width, int duration) {
        final int startWidth = getWidth();
        ValueAnimator valueAnimator = ValueAnimator.ofInt(startWidth, width).setDuration(duration);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int curWidth = (int) animation.getAnimatedValue();
                ViewGroup.LayoutParams layoutParams = getLayoutParams();
                layoutParams.width = curWidth;
                layoutParams.height = curWidth;//等长度缩放
                setLayoutParams(layoutParams);
            }
        });
        valueAnimator.start();
    }

    /**
     * 缩放宽
     *
     * @param width 宽度
     */
    public void animateWidth(int width) {
        animateWidth(width, 500);
    }

    /**
     * 缩放高
     *
     * @param height 高度
     */
    public void animateHeight(int height, int duration) {
        final int startHeight = getHeight();
        ValueAnimator valueAnimator = ValueAnimator.ofInt(startHeight, height).setDuration(duration);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int curHeight = (int) animation.getAnimatedValue();
                ViewGroup.LayoutParams layoutParams = getLayoutParams();
                layoutParams.width = curHeight;//等长度缩放
                layoutParams.height = curHeight;
                setLayoutParams(layoutParams);
            }
        });
        valueAnimator.start();
    }

    /**
     * 缩放高
     *
     * @param height 高度
     */
    public void animateHeight(int height) {
        animateHeight(height, 500);
    }
}
