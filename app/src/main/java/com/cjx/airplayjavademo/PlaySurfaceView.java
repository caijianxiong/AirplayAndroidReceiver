package com.cjx.airplayjavademo;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;

public class PlaySurfaceView extends SurfaceView {

    private float mWidth;
    private float mHeight;

    public PlaySurfaceView(Context context) {
        super(context);
    }

    public PlaySurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PlaySurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public PlaySurfaceView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * 设置视频源的原始宽高
     */
    public void setMeasure(float width, float height) {
        this.mWidth = width;
        this.mHeight = height;
        post(() -> {
            // 强制父布局重新测量和布局
            requestLayout();
            invalidate();
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 获取父容器推荐的宽高（通常是全屏）
        int specWidth = MeasureSpec.getSize(widthMeasureSpec);
        int specHeight = MeasureSpec.getSize(heightMeasureSpec);

        if (mWidth > 0 && mHeight > 0) {
            float videoRatio = mWidth / mHeight;
            float screenRatio = (float) specWidth / specHeight;

            int finalWidth;
            int finalHeight;

            if (videoRatio > screenRatio) {
                // 视频比较宽（横屏内容），以屏幕宽度为基准缩放
                finalWidth = specWidth;
                finalHeight = (int) (specWidth / videoRatio);
            } else {
                // 视频比较窄（竖屏内容），以屏幕高度为基准缩放
                finalHeight = specHeight;
                finalWidth = (int) (specHeight * videoRatio);
            }
            setMeasuredDimension(finalWidth, finalHeight);
        } else {
            // 如果没有视频尺寸信息，默认填充可用空间
            setMeasuredDimension(specWidth, specHeight);
        }
    }
}