package com.shif.shqip.android.webview;

import android.content.Context;
import android.util.AttributeSet;

public class MyWebView extends VideoEnabledWebView {

    private OnScrollListener mOnScrollListener = null;

    public interface OnScrollListener {
        void onScrollChanged(MyWebView myWebView, int x, int y, int xx, int yy);
    }

    public MyWebView(Context context) {
        super(context);
    }

    public MyWebView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public MyWebView(Context context, AttributeSet attributeSet, int defStyle) {
        super(context, attributeSet, defStyle);
    }

    @Override
    protected void onScrollChanged(int x, int y, int xx, int yy) {
        super.onScrollChanged(x, y, xx, yy);
        if (mOnScrollListener != null) {
            mOnScrollListener.onScrollChanged(this, x, y, xx, yy);
        }
    }

    @SuppressWarnings("unused")
    public void setOnScrollListener(OnScrollListener onScrollListener) {
        mOnScrollListener = onScrollListener;
    }

}
