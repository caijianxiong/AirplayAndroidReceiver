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


    public void setMeasure(float width, float height) {
        this.mWidth = width;
        this.mHeight = height;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int hight = MeasureSpec.getSize(heightMeasureSpec);
        if (this.mWidth > 0) {
            width = (int) mWidth;
        }
        if (this.mHeight > 0) {
            hight = (int) mHeight;
        }
        setMeasuredDimension(width, hight);
    }
}
