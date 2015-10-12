//TO DO:
//
// - improve timer performance (especially on Eee Pad)
// - improve child rearranging

package com.animoto.android.views;

import java.util.Collections;
import java.util.ArrayList;


import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ScrollView;

public class DraggableGridView extends ViewGroup implements View.OnTouchListener, View.OnClickListener, View.OnLongClickListener {


    protected int colCount = 2; // 每行个数
    protected int childSize; // 每行元素的高度必须一致，为该值

    public static int animT = 150;
    //dragging vars
    protected int dragged = -1;         // 拖动的是哪个view
    protected int lastTarget = -1;      // 拖动到了哪个view的位置
    protected int lastX = -1;           // 拖动的手指位置
    protected int lastY = -1;           // 拖动的手指位置
    protected ArrayList<Integer> newPositions = new ArrayList<Integer>();
    //listeners
    protected OnRearrangeListener onRearrangeListener;
    protected OnClickListener secondaryOnClickListener;
    private OnItemClickListener onItemClickListener;
    private OnDragScrollListener onDragScrollListener;

    private ScrollView parentScrollView;
    private ScrollState scrollState = ScrollState.idle;

    private enum ScrollState {
        top, bottom, idle
    }

    public interface OnRearrangeListener {
        void onRearrange(int dragged, int lastTarget);
    }

    public interface OnDragScrollListener {
        void onDragScrollTop();
        void onDragScrollBottom();
    }

    public DraggableGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setListeners();
        setChildrenDrawingOrderEnabled(true);
    }

    protected void setListeners() {
        setOnTouchListener(this);
        super.setOnClickListener(this);
        setOnLongClickListener(this);
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        secondaryOnClickListener = l;
    }

    //OVERRIDES
    @Override
    public void addView(View child) {
        super.addView(child);
        newPositions.add(-1);
    }

    @Override
    public void removeViewAt(int index) {
        super.removeViewAt(index);
        newPositions.remove(index);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getParent() instanceof ScrollView) {
             parentScrollView = (ScrollView) getParent();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        childSize = width / colCount;
        int lineCount = (getChildCount() + colCount - 1) / colCount;
        int totalHeight = lineCount * childSize;

        setMeasuredDimension(getChildMeasureSpec(widthMeasureSpec, 0, MeasureSpec.getSize(widthMeasureSpec)),
                getChildMeasureSpec(heightMeasureSpec, 0, totalHeight));
    }

    //LAYOUT
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

        performLayout(l, t, r, b);
    }

    private void performLayout(int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            if (i != dragged) {
                Point xy = getCoorFromIndex(i);
                getChildAt(i).layout(xy.x, xy.y, xy.x + childSize, xy.y + childSize);
            }
        }
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        if (dragged == -1) {
            if (lastTarget == -1) {
                return i;
            } else if (i == childCount - 1) {
                return lastTarget;
            } else if (i >= lastTarget) {
                return i + 1;
            }
        } else if (i == childCount - 1) {
            return dragged;
        } else if (i >= dragged) {
            return i + 1;
        }
        return i;
    }

    public int getIndexFromCoor(int x, int y) {
        int col = getColOrRowFromCoor(x), row = getColOrRowFromCoor(y/* + scroll*/);
        if (col == -1 || row == -1) //touch is between columns or rows
            return -1;
        int index = row * colCount + col;
        if (index >= getChildCount())
            return -1;
        return index;
    }

    protected int getColOrRowFromCoor(int coor) {
        for (int i = 0; coor > 0; i++) {
            if (coor < childSize)
                return i;
            coor -= childSize;
        }
        return -1;
    }

    protected int getTargetFromCoor(int x, int y) {
        if (getColOrRowFromCoor(y) == -1) //touch is between rows
            return -1;

        return getIndexFromCoor(x, y);
    }

    protected Point getCoorFromIndex(int index) {
        int col = index % colCount;
        int row = index / colCount;
        return new Point(childSize * col, childSize * row);
    }

    //EVENT HANDLERS
    public void onClick(View view) {
        if (secondaryOnClickListener != null)
            secondaryOnClickListener.onClick(view);
        if (onItemClickListener != null && getLastIndex() != -1)
            onItemClickListener.onItemClick(null, getChildAt(getLastIndex()), getLastIndex(), getLastIndex() / colCount);
    }

    public boolean onLongClick(View view) {
        getParent().requestDisallowInterceptTouchEvent(true);
        int index = getLastIndex();
        if (index != -1) {
            dragged = index;
            animateDragged();
            return true;
        }
        return false;
    }

    public boolean onTouch(View view, MotionEvent event) {
        int action = event.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                lastX = (int) event.getX();
                lastY = (int) event.getY();
                scrollState = ScrollState.idle;
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragged != -1) {
                    int x = (int) event.getX(), y = (int) event.getY();
                    float px = getResources().getDisplayMetrics().density * 20 + 0.5f;
                    if (onDragScrollListener != null && parentScrollView != null) {
                        if (y > parentScrollView.getHeight() + parentScrollView.getScrollY() - px) {
                            scrollState = ScrollState.bottom;
                            post(scrollRunnable);
                        } else if (y - parentScrollView.getScrollY() < px) {
                            scrollState = ScrollState.top;
                            post(scrollRunnable);
                        } else {
                            scrollState = ScrollState.idle;
                            removeCallbacks(scrollRunnable);
                        }
                    }
                    int l = x - childSize / 2, t = y - childSize / 2;
                    getChildAt(dragged).layout(l, t, l + childSize, t + childSize);

                    //check for new target hover
                    int target = getTargetFromCoor(x, y);
                    if (lastTarget != target) {
                        if (target != -1) {
                            animateGap(target);
                            lastTarget = target;
                        }
                    }
                } else {
                    performLayout(getLeft(), getTop(), getRight(), getBottom());
                }
                lastX = (int) event.getX();
                lastY = (int) event.getY();
                break;
            case MotionEvent.ACTION_UP:
                scrollState = ScrollState.idle;
                if (dragged != -1) {
                    View v = getChildAt(dragged);
                    reorderChildren();
                    v.clearAnimation();
                    dragged = -1;
                }
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }
        if (dragged != -1)
            return true;
        return false;
    }

    //EVENT HELPERS
    protected void animateDragged() {
        final View v = getChildAt(dragged);


        final int targetX = lastX - childSize / 2;
        final int targetY = lastY - childSize / 2;

        final float originX = v.getX();
        final float originY = v.getY();

        final float deltaX = targetX - v.getX();
        final float deltaY = targetY - v.getY();

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatedValue = (Float)animation.getAnimatedValue();
                int currentX = (int) (originX + deltaX * animatedValue);
                int currentY = (int) (originY + deltaY * animatedValue);
                v.layout(currentX, currentY, currentX + childSize, currentY + childSize);
            }
        });
        animator.setDuration(100).start();
    }

    protected void animateGap(int target) {
        for (int i = 0; i < getChildCount(); i++) {
            if (i == dragged) {
                continue;
            }
            View v = getChildAt(i);
            int newPos = i;
            if (dragged < target && i >= dragged + 1 && i <= target) {
                newPos--;
            } else if (target < dragged && i >= target && i < dragged) {
                newPos++;
            }

            //animate
            int oldPos = i;
            if (newPositions.get(i) != -1)
                oldPos = newPositions.get(i);
            if (oldPos == newPos)
                continue;

            Point oldXY = getCoorFromIndex(oldPos);
            Point newXY = getCoorFromIndex(newPos);
            Point oldOffset = new Point(oldXY.x - v.getLeft(), oldXY.y - v.getTop());
            Point newOffset = new Point(newXY.x - v.getLeft(), newXY.y - v.getTop());

            TranslateAnimation translate = new TranslateAnimation(Animation.ABSOLUTE, oldOffset.x,
                    Animation.ABSOLUTE, newOffset.x,
                    Animation.ABSOLUTE, oldOffset.y,
                    Animation.ABSOLUTE, newOffset.y);
            translate.setDuration(animT);
            translate.setFillEnabled(true);
            translate.setFillAfter(true);
            v.clearAnimation();
            v.startAnimation(translate);

            newPositions.set(i, newPos);
        }
    }

    protected void reorderChildren() {
        if (onRearrangeListener != null) {
            onRearrangeListener.onRearrange(dragged, lastTarget);
        }
        ArrayList<View> children = new ArrayList<View>();
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).clearAnimation();
            children.add(getChildAt(i));
        }
        removeAllViews();
        while (dragged != lastTarget) {
            if (lastTarget == children.size()) {
                // dragged and dropped to the right of the last element
                children.add(children.remove(dragged));
                dragged = lastTarget;
            } else if (dragged < lastTarget) {
                // shift to the right
                Collections.swap(children, dragged, dragged + 1);
                dragged++;
            } else if (dragged > lastTarget) {
                // shift to the left
                Collections.swap(children, dragged, dragged - 1);
                dragged--;
            }
        }
        for (int i = 0; i < children.size(); i++) {
            newPositions.set(i, -1);
            final View view = children.get(i);
            addView(view);
            if (i != lastTarget) {
                continue;
            }
            // animate dragged view
            final float draggingX = view.getX();
            final float draggingY = view.getY();
            getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    getViewTreeObserver().removeOnPreDrawListener(this);
                    float droppedX = view.getX();
                    float droppedY = view.getY();
                    final float deltaX = droppedX - draggingX;
                    final float deltaY = droppedY - draggingY;
                    view.layout((int)draggingX, (int)draggingY, (int)(draggingX + childSize), (int)(draggingY + childSize));
                    ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            float animatedValue = (Float)animation.getAnimatedValue();
                            if (animatedValue == 1f) {
                                lastTarget = -1;
                            }
                            int currentX = (int) (draggingX + deltaX * animatedValue);
                            int currentY = (int) (draggingY + deltaY * animatedValue);
                            view.layout(currentX, currentY, currentX + childSize, currentY + childSize);
                        }
                    });
                    animator.setDuration(300).start();
                    return false;
                }
            });
        }
    }

    public int getLastIndex() {
        return getIndexFromCoor(lastX, lastY);
    }

    //OTHER METHODS
    public void setOnRearrangeListener(OnRearrangeListener l) {
        this.onRearrangeListener = l;
    }

    public void setOnDragScrollListener(OnDragScrollListener l) {
        onDragScrollListener = l;
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.onItemClickListener = l;
    }

    private Runnable scrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (scrollState != ScrollState.idle) {
                if (scrollState == ScrollState.top) {
                    onDragScrollListener.onDragScrollTop();
                } else if (scrollState == ScrollState.bottom){
                    onDragScrollListener.onDragScrollBottom();
                }
                postDelayed(this, 20);
            }
        }
    };
}