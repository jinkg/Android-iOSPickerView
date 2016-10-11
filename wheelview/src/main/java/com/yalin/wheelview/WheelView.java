package com.yalin.wheelview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.ParcelableCompat;
import android.support.v4.os.ParcelableCompatCreatorCallbacks;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;

/**
 * 仿ios选择控件
 * todo 当滚动过每一个item时，添加声音反馈
 * 作者：YaLin
 * 日期：2016/10/10.
 */

public class WheelView extends View {
    private static final String TAG = "WheelView";

    public interface OnItemSelectedListener {
        void onItemSelected(WheelAdapter adapter, int position);
    }

    public enum ACTION {
        CLICK, FLING, DRAGGLE
    }

    private static final float LINE_SPACING_MULTIPLIER = 1.4f;

    private static final float SCALE_CONTENT = 0.8f;

    private static final float CENTER_CONTENT_OFFSET = 6;

    private static final int VELOCITY_FLING = 5;

    private static final int GRAVITY_CENTER = 0;
    private static final int GRAVITY_LEFT = -1;
    private static final int GRAVITY_RIGHT = 1;

    private GestureDetector mGestureDetector;

    private ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> mFuture;

    private Paint mPaintOuterText;
    private Paint mPaintCenterText;
    private Paint mPaintIndicator;

    private WheelAdapter mAdapter;

    private int mTextColorOut;
    private int mTextColorCenter;
    private int mDividerColor;

    private int mTextSize;
    private boolean mTextSizeCustom;

    private int mGravity = GRAVITY_CENTER;

    private InternalHandler mHandler;

    private boolean mIsLoop = true;
    private int mTotalScrollY;
    private int mInitPosition;

    private int mItemsVisible = 11;
    private float mItemHeight;
    private int mPreCurrentIndex;

    private float mFirstLineY;
    private float mSecondLineY;
    private float mCenterY;

    private String mLabel;

    private int mMaxTextWidth;
    private int mMaxTextHeight;

    private int mHalfCircumference;
    private int mRadius;

    private int mDrawOutContentStart = 0;
    private int mDrawCenterContentStart = 0;

    private List<Integer> mVisibleItems;

    private int mSelectedItem;

    private int mWidthMeasureSpec;
    private int mMeasuredWidth;
    private int mMeasuredHeight;

    private int mOffset = 0;
    private float mVelocityY = 0;
    private float mPreviousY = 0;

    private long mStartTime = 0;

    private OnItemSelectedListener mOnItemSelectedListener;

    private GestureDetector.SimpleOnGestureListener mInternalGestureListener
            = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            scrollBy(velocityY);
            return true;
        }
    };

    private int mRealTotalOffset = Integer.MAX_VALUE;
    private int mRealOffset = 0;
    private TimerTask mSmoothScrollTimerTask = new TimerTask() {
        @Override
        public void run() {
            Log.d(TAG, "mSmoothScrollTimerTask run: ");
            if (mRealTotalOffset == Integer.MAX_VALUE) {
                mRealTotalOffset = mOffset;
            }
            mRealOffset = (int) (mRealTotalOffset * 0.1f);
            if (mRealOffset == 0) {
                if (mRealTotalOffset < 0) {
                    mRealOffset = -1;
                } else {
                    mRealOffset = 1;
                }
            }

            if (Math.abs(mRealTotalOffset) <= 1) {
                cancelFuture();
                mHandler.obtainMessage(InternalHandler.WHAT_ITEM_SELECTED, WheelView.this).sendToTarget();
            } else {
                mTotalScrollY += mRealOffset;

                if (!mIsLoop) {
                    float itemHeight = mItemHeight;
                    float top = -mInitPosition * itemHeight;
                    float bottom = (mAdapter.getItemsCount() - 1 - mInitPosition) * itemHeight;
                    if (mTotalScrollY <= top || mTotalScrollY >= bottom) {
                        mTotalScrollY -= mRealOffset;
                        cancelFuture();
                        mHandler.obtainMessage(InternalHandler.WHAT_ITEM_SELECTED, WheelView.this).sendToTarget();
                        return;
                    }
                }
                mHandler.obtainMessage(InternalHandler.WHAT_INVALIDATE_LOOP_VIEW, WheelView.this).sendToTarget();
                mRealTotalOffset -= mRealOffset;
            }
        }
    };

    private float mRealVelocityY = Integer.MAX_VALUE;
    private TimerTask mInertiaTimerTask = new TimerTask() {
        private static final float MAX_VELOCITY_THRESHOLD = 3000f;

        @Override
        public void run() {
            Log.d(TAG, "mInertiaTimerTask run: ");
            if (mRealVelocityY == Integer.MAX_VALUE) {
                if (Math.abs(mVelocityY) > MAX_VELOCITY_THRESHOLD) {
                    if (mVelocityY > 0.0f) {
                        mRealVelocityY = MAX_VELOCITY_THRESHOLD;
                    } else {
                        mRealVelocityY = -MAX_VELOCITY_THRESHOLD;
                    }
                } else {
                    mRealVelocityY = mVelocityY;
                }
            }
            if (Math.abs(mRealVelocityY) >= 0f && Math.abs(mRealVelocityY) <= 20f) {
                cancelFuture();
                mHandler.obtainMessage(InternalHandler.WHAT_SMOOTH_SCROLL, WheelView.this).sendToTarget();
                return;
            }
            int i = (int) ((mRealVelocityY * 10f) / 1000);
            mTotalScrollY -= i;
            if (!mIsLoop) {
                float itemHeight = mItemHeight;
                float top = -mInitPosition * itemHeight;
                float bottom = (mAdapter.getItemsCount() - 1 - mInitPosition) * itemHeight;
                if (mTotalScrollY - itemHeight * 0.3 < top) {
                    top = mTotalScrollY + i;
                } else if (mTotalScrollY + itemHeight * 0.3 > bottom) {
                    bottom = mTotalScrollY + i;
                }

                if (mTotalScrollY <= top) {
                    mRealVelocityY = 40f;
                    mTotalScrollY = (int) top;
                } else if (mTotalScrollY >= bottom) {
                    mRealVelocityY = -40f;
                    mTotalScrollY = (int) bottom;
                }
            }
            if (mRealVelocityY < 0f) {
                mRealVelocityY += 20f;
            } else {
                mRealVelocityY -= 20f;
            }
            mHandler.obtainMessage(InternalHandler.WHAT_INVALIDATE_LOOP_VIEW, WheelView.this).sendToTarget();
        }
    };

    public WheelView(Context context) {
        this(context, null);
    }

    public WheelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WheelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mTextColorOut = ContextCompat.getColor(context, R.color.wheel_view_text_color_out);
        mTextColorCenter = ContextCompat.getColor(context, R.color.wheel_view_text_color_center);
        mDividerColor = ContextCompat.getColor(context, R.color.wheel_view_text_color_divider);
        mTextSize = getResources().getDimensionPixelSize(R.dimen.wheel_view_text_size);
        mTextSizeCustom = getResources().getBoolean(R.bool.wheel_view_text_size_custom);
        if (attrs != null) {
            TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.WheelView);
            try {
                mGravity = array.getInt(R.styleable.WheelView_gravity, GRAVITY_CENTER);
                mTextColorOut = array.getColor(R.styleable.WheelView_textColorOut, mTextColorOut);
                mTextColorCenter = array.getColor(R.styleable.WheelView_textColorCenter, mTextColorCenter);
                mDividerColor = array.getColor(R.styleable.WheelView_dividerColor, mDividerColor);
                mTextSize = array.getDimensionPixelOffset(R.styleable.WheelView_textSize, mTextSize);
            } finally {
                array.recycle();
            }
        }
        initLoopView(context);
        initPaints();
        mVisibleItems = new ArrayList<>(mItemsVisible);
    }

    private void initLoopView(Context context) {
        mHandler = new InternalHandler();
        mGestureDetector = new GestureDetector(context, mInternalGestureListener);
        mGestureDetector.setIsLongpressEnabled(false);

        mIsLoop = true;
        mTotalScrollY = 0;
        mInitPosition = -1;
    }

    private void initPaints() {
        mPaintOuterText = new Paint();
        mPaintOuterText.setColor(mTextColorOut);
        mPaintOuterText.setAntiAlias(true);
        mPaintOuterText.setTypeface(Typeface.MONOSPACE);
        mPaintOuterText.setTextSize(mTextSize);

        mPaintCenterText = new Paint();
        mPaintCenterText.setColor(mTextColorCenter);
        mPaintCenterText.setAntiAlias(true);
        mPaintCenterText.setTextScaleX(1.1f);
        mPaintCenterText.setTypeface(Typeface.MONOSPACE);
        mPaintCenterText.setTextSize(mTextSize);

        mPaintIndicator = new Paint();
        mPaintIndicator.setColor(mDividerColor);
        mPaintIndicator.setAntiAlias(true);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setLoopable(boolean loopable) {
        mIsLoop = loopable;
    }

    public void setLabel(String label) {
        mLabel = label;
    }

    public void setTextSize(float size) {
        if (size > 0f && !mTextSizeCustom) {
            mTextSize = (int) (getResources().getDisplayMetrics().density * size);
            mPaintOuterText.setTextSize(mTextSize);
            mPaintCenterText.setTextSize(mTextSize);
        }
    }

    public void setCurrentItem(int currentItemIndex) {
        mInitPosition = currentItemIndex;
        mTotalScrollY = 0;
        invalidate();
    }

    public void setAdapter(WheelAdapter adapter) {
        mAdapter = adapter;
        remeasure();
        invalidate();
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        mOnItemSelectedListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mAdapter == null) {
            return;
        }
        mVisibleItems.clear();
        int mChange = (int) (mTotalScrollY / mItemHeight);
        try {
            mPreCurrentIndex = mInitPosition + mChange % mAdapter.getItemsCount();
        } catch (ArithmeticException ignored) {
        }
        if (!mIsLoop) {
            if (mPreCurrentIndex < 0) {
                mPreCurrentIndex = 0;
            }
            if (mPreCurrentIndex > mAdapter.getItemsCount() - 1) {
                mPreCurrentIndex = mAdapter.getItemsCount() - 1;
            }
        } else {
            if (mPreCurrentIndex < 0) {
                mPreCurrentIndex = mAdapter.getItemsCount() + mPreCurrentIndex;
            }
            if (mPreCurrentIndex > mAdapter.getItemsCount() - 1) {
                mPreCurrentIndex = mPreCurrentIndex - mAdapter.getItemsCount();
            }
        }
        int itemHeightOffset = (int) (mTotalScrollY % mItemHeight);
        int counter = 0;
        while (counter < mItemsVisible) {
            int index = mPreCurrentIndex - (mItemsVisible / 2 - counter);
            if (mIsLoop) {
                index = getLoopMappingIndex(index);
                mVisibleItems.add(counter, index);
            } else if (index < 0 || (index > mAdapter.getItemsCount() - 1)) {
                mVisibleItems.add(counter, -1);
            } else {
                mVisibleItems.add(counter, index);
            }
            counter++;
        }

        canvas.drawLine(0.0f, mFirstLineY, mMeasuredWidth, mFirstLineY, mPaintIndicator);
        canvas.drawLine(0.0f, mSecondLineY, mMeasuredWidth, mSecondLineY, mPaintIndicator);

        if (!TextUtils.isEmpty(mLabel)) {
            int drawRightContentStart = mMeasuredWidth - getTextWidth(mPaintCenterText, mLabel);
            canvas.drawText(mLabel, drawRightContentStart - CENTER_CONTENT_OFFSET, mCenterY, mPaintCenterText);
        }

        counter = 0;
        while (counter < mItemsVisible) {
            canvas.save();
            float itemHeight = mMaxTextHeight * LINE_SPACING_MULTIPLIER;
            double radian = ((itemHeight * counter - itemHeightOffset) * Math.PI) / mHalfCircumference;

            float angle = (float) (90d - (radian / Math.PI) * 180d);

            int index = mVisibleItems.get(counter);
            if (angle >= 90f || angle <= -90f) {
                canvas.restore();
            } else if (index >= 0) {
                String contentText = mAdapter.getItemLabel(mVisibleItems.get(counter));

                measuredCenterContentStart(contentText);
                measuredOutContentStart(contentText);
                float translateY = (float) (mRadius - Math.cos(radian) * mRadius
                        - (Math.sin(radian) * mMaxTextHeight / 2d));
                canvas.translate(0.0f, translateY);
                canvas.scale(1.0f, (float) Math.sin(radian));
                if (translateY <= mFirstLineY && mMaxTextHeight + translateY >= mFirstLineY) {
                    canvas.save();
                    canvas.clipRect(0, 0, mMeasuredWidth, mFirstLineY - translateY);
                    canvas.scale(1.0f, (float) (Math.sin(radian) * SCALE_CONTENT));
                    canvas.drawText(contentText, mDrawOutContentStart, mMaxTextHeight, mPaintOuterText);
                    canvas.restore();
                    canvas.save();
                    canvas.clipRect(0, mFirstLineY - translateY, mMeasuredWidth, (int) itemHeight);
                    canvas.scale(1.0f, (float) (Math.sin(radian) * 1f));
                    canvas.drawText(contentText, mDrawCenterContentStart,
                            mMaxTextHeight - CENTER_CONTENT_OFFSET, mPaintCenterText);
                    canvas.restore();
                } else if (translateY <= mSecondLineY && mMaxTextHeight + translateY >= mSecondLineY) {
                    canvas.save();
                    canvas.clipRect(0, 0, mMeasuredWidth, mSecondLineY - translateY);
                    canvas.scale(1.0f, (float) (Math.sin(radian) * 1.0f));
                    canvas.drawText(contentText, mDrawCenterContentStart,
                            mMaxTextHeight - CENTER_CONTENT_OFFSET, mPaintCenterText);
                    canvas.restore();
                    canvas.save();
                    canvas.clipRect(0, mSecondLineY - translateY, mMeasuredWidth, (int) itemHeight);
                    canvas.scale(1.0f, (float) (Math.sin(radian) * SCALE_CONTENT));
                    canvas.drawText(contentText, mDrawOutContentStart, mMaxTextHeight, mPaintOuterText);
                    canvas.restore();
                } else if (translateY >= mFirstLineY && mMaxTextHeight + translateY <= mSecondLineY) {
                    canvas.clipRect(0, 0, mMeasuredWidth, (int) itemHeight);
                    canvas.drawText(contentText, mDrawCenterContentStart,
                            mMaxTextHeight - CENTER_CONTENT_OFFSET, mPaintCenterText);
                    int preSelectedItem = mVisibleItems.get(counter);
                    if (preSelectedItem != -1) {
                        mSelectedItem = preSelectedItem;
                    }
                } else {
                    canvas.save();
                    canvas.clipRect(0, 0, mMeasuredWidth, (int) itemHeight);
                    canvas.scale(1.0f, (float) (Math.sin(radian) * SCALE_CONTENT));
                    canvas.drawText(contentText, mDrawOutContentStart, mMaxTextHeight, mPaintOuterText);
                    canvas.restore();
                }
                canvas.restore();
            }
            counter++;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mWidthMeasureSpec = widthMeasureSpec;
        remeasure();
        setMeasuredDimension(mMeasuredWidth, mMeasuredHeight);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean eventConsumed = mGestureDetector.onTouchEvent(event);
        Log.d(TAG, "onTouchEvent consumed event: " + eventConsumed + " event : " + event.getAction());
        switch (event.getAction()) {
            case ACTION_DOWN:
                mStartTime = System.currentTimeMillis();
                cancelFuture();
                mPreviousY = event.getRawY();
                break;
            case ACTION_MOVE:
                float dy = mPreviousY - event.getRawY();
                mPreviousY = event.getRawY();
                mTotalScrollY += dy;
                if (!mIsLoop) {
                    float top = -mInitPosition * mItemHeight;
                    float bottom = (mAdapter.getItemsCount() - 1 - mInitPosition) * mItemHeight;
                    if (mTotalScrollY - mItemHeight * 0.3 < top) {
                        top = mTotalScrollY - dy;
                    } else if (mTotalScrollY + mItemHeight * 0.3 > bottom) {
                        bottom = mTotalScrollY - dy;
                    }
                    if (mTotalScrollY < top) {
                        mTotalScrollY = (int) top;
                    } else if (mTotalScrollY > bottom) {
                        mTotalScrollY = (int) bottom;
                    }
                }
                break;
            case ACTION_UP:
            default:
                if (!eventConsumed) {
                    float y = event.getY();
                    double l = Math.acos((mRadius - y) / mRadius) * mRadius;
                    int circlePosition = (int) ((l + mItemHeight / 2) / mItemHeight);

                    float extraOffset = (mTotalScrollY % mItemHeight + mItemHeight) % mItemHeight;
                    mOffset = (int) ((circlePosition - mItemsVisible / 2) * mItemHeight - extraOffset);
                    if ((System.currentTimeMillis() - mStartTime) > 120) {
                        smoothScroll(ACTION.DRAGGLE);
                    } else {
                        smoothScroll(ACTION.CLICK);
                    }
                }
                break;
        }
        invalidate();
        return true;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        state.loopable = mIsLoop;
        state.textSize = mTextSize;
        state.currentIndex = mInitPosition;
        state.label = mLabel;
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        mIsLoop = savedState.loopable;
        mTextSize = savedState.textSize;
        mInitPosition = savedState.currentIndex;
        mLabel = savedState.label;
    }

    void smoothScroll(ACTION action) {
        cancelFuture();
        if (action == ACTION.FLING || action == ACTION.DRAGGLE) {
            mOffset = (int) ((mTotalScrollY % mItemHeight + mItemHeight) % mItemHeight);
            if (mOffset > mItemHeight / 2.0f) {
                mOffset = (int) (mItemHeight - mOffset);
            } else {
                mOffset = -mOffset;
            }
        }
        mRealTotalOffset = Integer.MAX_VALUE;
        mRealOffset = 0;
        mFuture = mExecutor.scheduleWithFixedDelay(mSmoothScrollTimerTask, 0, 10, TimeUnit.MILLISECONDS);
    }

    protected final void scrollBy(float velocityY) {
        cancelFuture();
        mVelocityY = velocityY;
        mRealVelocityY = Integer.MAX_VALUE;
        mFuture = mExecutor.scheduleWithFixedDelay(mInertiaTimerTask, 0, VELOCITY_FLING, TimeUnit.MILLISECONDS);
    }

    public void cancelFuture() {
        if (mFuture != null && !mFuture.isCancelled()) {
            mFuture.cancel(true);
            mFuture = null;
        }
    }

    private int getLoopMappingIndex(int index) {
        if (index < 0) {
            index += mAdapter.getItemsCount();
            index = getLoopMappingIndex(index);
        } else if (index > mAdapter.getItemsCount() - 1) {
            index -= mAdapter.getItemsCount();
            index = getLoopMappingIndex(index);
        }
        return index;
    }

    private int getTextWidth(Paint paint, String str) {
        int ret = 0;
        if (!TextUtils.isEmpty(str)) {
            int len = str.length();
            float[] widths = new float[len];
            paint.getTextWidths(str, widths);
            for (int i = 0; i < len; i++) {
                ret += (int) Math.ceil(widths[i]);
            }
        }
        return ret;
    }

    private void measuredCenterContentStart(String content) {
        Rect rect = new Rect();
        mPaintCenterText.getTextBounds(content, 0, content.length(), rect);
        switch (mGravity) {
            case GRAVITY_CENTER:
                mDrawCenterContentStart = (int) ((mMeasuredWidth - rect.width()) * 0.5);
                break;
            case GRAVITY_LEFT:
                mDrawCenterContentStart = 0;
                break;
            case GRAVITY_RIGHT:
                mDrawCenterContentStart = mMeasuredWidth - rect.width();
                break;
        }
    }

    private void measuredOutContentStart(String content) {
        Rect rect = new Rect();
        mPaintOuterText.getTextBounds(content, 0, content.length(), rect);
        switch (mGravity) {
            case GRAVITY_CENTER:
                mDrawOutContentStart = (int) ((mMeasuredWidth - rect.width()) * 0.5);
                break;
            case GRAVITY_LEFT:
                mDrawOutContentStart = 0;
                break;
            case GRAVITY_RIGHT:
                mDrawOutContentStart = mMeasuredWidth - rect.width();
                break;
        }
    }

    private void remeasure() {
        if (mAdapter == null) {
            return;
        }
        measureTextWidthHeight();

        mHalfCircumference = (int) (mItemHeight * (mItemsVisible - 1));
        mMeasuredHeight = (int) (mHalfCircumference * 2 / Math.PI);
        mRadius = (int) (mHalfCircumference / Math.PI);

        mMeasuredWidth = MeasureSpec.getSize(mWidthMeasureSpec);

        mFirstLineY = (mMeasuredHeight - mItemHeight) / 2.0f;
        mSecondLineY = (mMeasuredHeight + mItemHeight) / 2.0f;
        mCenterY = (mMeasuredHeight + mMaxTextHeight) / 2.0f - CENTER_CONTENT_OFFSET;

        if (mInitPosition == -1) {
            if (mIsLoop) {
                mInitPosition = (mAdapter.getItemsCount() + 1) / 2;
            } else {
                mInitPosition = 0;
            }
        }

        mPreCurrentIndex = mInitPosition;
    }

    protected void onItemSelected() {
        if (mOnItemSelectedListener != null) {
            mOnItemSelectedListener.onItemSelected(mAdapter, mSelectedItem);
        }
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void measureTextWidthHeight() {
        Rect rect = new Rect();
        for (int i = 0, count = mAdapter.getItemsCount(); i < count; i++) {
            String str = mAdapter.getItemLabel(i);
            mPaintCenterText.getTextBounds(str, 0, str.length(), rect);
            int textWidth = rect.width();
            if (textWidth > mMaxTextWidth) {
                mMaxTextWidth = textWidth;
            }
            mPaintCenterText.getTextBounds("\u661F\u671F", 0, 2, rect);
            int textHeight = rect.height();
            if (textHeight > mMaxTextHeight) {
                mMaxTextHeight = textHeight;
            }
        }
        mItemHeight = LINE_SPACING_MULTIPLIER * mMaxTextHeight;
    }

    private static class InternalHandler extends Handler {
        static final int WHAT_INVALIDATE_LOOP_VIEW = 1000;
        static final int WHAT_SMOOTH_SCROLL = 2000;
        static final int WHAT_ITEM_SELECTED = 3000;

        @Override
        public void handleMessage(Message msg) {
            WheelView wheelView = (WheelView) msg.obj;
            switch (msg.what) {
                case WHAT_INVALIDATE_LOOP_VIEW:
                    wheelView.invalidate();
                    break;
                case WHAT_SMOOTH_SCROLL:
                    wheelView.smoothScroll(ACTION.FLING);
                    break;
                case WHAT_ITEM_SELECTED:
                    wheelView.onItemSelected();
                    break;
            }
        }
    }

    private static class SavedState extends BaseSavedState {
        boolean loopable;

        int textSize;

        int currentIndex;

        String label;

        SavedState(Parcelable superState) {
            super(superState);
        }

        SavedState(Parcel source) {
            super(source);
            loopable = source.readByte() != 0;
            textSize = source.readInt();
            currentIndex = source.readInt();
            label = source.readString();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeByte((byte) (loopable ? 1 : 0));
            out.writeInt(textSize);
            out.writeInt(currentIndex);
            out.writeString(label);
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = ParcelableCompat.newCreator(new ParcelableCompatCreatorCallbacks<SavedState>() {

            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }

        });
    }

}
