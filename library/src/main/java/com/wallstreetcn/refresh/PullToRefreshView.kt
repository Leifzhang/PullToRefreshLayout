package com.wallstreetcn.refresh

import android.content.Context
import android.support.v4.view.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.Transformation
import android.widget.ImageView
import com.wallstreetcn.refresh.refresh_view.BaseRefreshView
import com.wallstreetcn.refresh.refresh_view.SunRefreshView
import com.wallstreetcn.refresh.util.Utils
import java.security.InvalidParameterException

class PullToRefreshView constructor(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs), NestedScrollingParent, NestedScrollingChild {

    private var mTarget: View? = null
    private val mRefreshView: ImageView
    private val mDecelerateInterpolator: Interpolator
    private val mTouchSlop: Int
    val totalDragDistance: Int = Utils.convertDpToPixel(context, DRAG_MAX_DISTANCE)
    private var mBaseRefreshView: BaseRefreshView? = null
    private var mCurrentDragPercent: Float = 0.toFloat()
    private var mCurrentOffsetTop: Int = 0
    private var mRefreshing: Boolean = false
    private var mActivePointerId: Int = 0
    private var mIsBeingDragged: Boolean = false
    private var mInitialMotionY: Float = 0.toFloat()
    private var mFrom: Int = 0
    private var mFromDragPercent: Float = 0.toFloat()
    private var mNotify: Boolean = false
    private var mListener: OnRefreshListener? = null

    private var mTargetPaddingTop: Int = 0
    private var mTargetPaddingBottom: Int = 0
    private var mTargetPaddingRight: Int = 0
    private var mTargetPaddingLeft: Int = 0


    private val mNestedScrollingParentHelper: NestedScrollingParentHelper
    private val mNestedScrollingChildHelper: NestedScrollingChildHelper

    private val mAnimateToStartPosition = object : Animation() {
        public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            moveToStart(interpolatedTime)
        }
    }

    private val mAnimateToCorrectPosition = object : Animation() {
        public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            val targetTop: Int
            val endTarget = totalDragDistance
            targetTop = mFrom + ((endTarget - mFrom) * interpolatedTime).toInt()
            val offset = targetTop - mTarget!!.top

            mCurrentDragPercent = mFromDragPercent - (mFromDragPercent - 1.0f) * interpolatedTime
            mBaseRefreshView!!.setPercent(mCurrentDragPercent, false)

            setTargetOffsetTop(offset  /* requires update */)
        }
    }

    private val mToStartListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {}

        override fun onAnimationRepeat(animation: Animation) {}

        override fun onAnimationEnd(animation: Animation) {
            mBaseRefreshView!!.stop()
            mCurrentOffsetTop = mTarget!!.top
        }
    }


    // NestedScrollingParent


    private var mTotalUnconsumed: Float = 0.toFloat()
    private val mParentScrollConsumed = IntArray(2)
    private val mParentOffsetInWindow = IntArray(2)

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.RefreshView)
        val type = a.getInteger(R.styleable.RefreshView_type, STYLE_SUN)
        a.recycle()

        mDecelerateInterpolator = DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR)
        mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop

        mRefreshView = ImageView(context)

        mNestedScrollingParentHelper = NestedScrollingParentHelper(this)

        mNestedScrollingChildHelper = NestedScrollingChildHelper(this)
        isNestedScrollingEnabled = true

        setRefreshStyle(type)

        addView(mRefreshView)

        setWillNotDraw(false)
        isChildrenDrawingOrderEnabled = true
    }

    fun setRefreshStyle(type: Int) {
        setRefreshing(false)
        when (type) {
            STYLE_SUN -> mBaseRefreshView = SunRefreshView(context, this)
            else -> throw InvalidParameterException("Type does not exist")
        }
        mRefreshView.setImageDrawable(mBaseRefreshView)
    }

    /**
     * This method sets padding for the refresh (progress) view.
     */
    fun setRefreshViewPadding(left: Int, top: Int, right: Int, bottom: Int) {
        mRefreshView.setPadding(left, top, right, bottom)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var widthMeasureSpec = widthMeasureSpec
        var heightMeasureSpec = heightMeasureSpec
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        ensureTarget()
        if (mTarget == null)
            return

        widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(measuredWidth - paddingRight - paddingLeft, View.MeasureSpec.EXACTLY)
        heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(measuredHeight - paddingTop - paddingBottom, View.MeasureSpec.EXACTLY)
        mTarget!!.measure(widthMeasureSpec, heightMeasureSpec)
        mRefreshView.measure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun ensureTarget() {
        if (mTarget != null)
            return
        if (childCount > 0) {
            for (i in 0..childCount) {
                val child = getChildAt(i) ?: break
                if (child !== mRefreshView) {
                    mTarget = child
                    mTargetPaddingBottom = mTarget!!.paddingBottom
                    mTargetPaddingLeft = mTarget!!.paddingLeft
                    mTargetPaddingRight = mTarget!!.paddingRight
                    mTargetPaddingTop = mTarget!!.paddingTop
                }
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {

        if (!isEnabled || canChildScrollUp() || mRefreshing) {
            return false
        }

        val action = ev.action

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                setTargetOffsetTop(0)
                mActivePointerId = ev.getPointerId(0)
                mIsBeingDragged = false
                val initialMotionY = getMotionEventY(ev, mActivePointerId)
                if (initialMotionY == -1f) {
                    return false
                }
                mInitialMotionY = initialMotionY
            }
            MotionEvent.ACTION_MOVE -> {
                if (mActivePointerId == INVALID_POINTER) {
                    return false
                }
                val y = getMotionEventY(ev, mActivePointerId)
                if (y == -1f) {
                    return false
                }
                val yDiff = y - mInitialMotionY
                if (yDiff > mTouchSlop && !mIsBeingDragged) {
                    mIsBeingDragged = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mIsBeingDragged = false
                mActivePointerId = INVALID_POINTER
            }
            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
        }

        return mIsBeingDragged
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {

        if (!mIsBeingDragged) {
            return super.onTouchEvent(ev)
        }

        val action = ev.action

        when (action) {
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(mActivePointerId)
                if (pointerIndex < 0) {
                    return false
                }

                val y = ev.getY(pointerIndex)
                return moveSpinner(y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = ev.actionIndex
                mActivePointerId = ev.getPointerId(index)
            }
            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mActivePointerId == INVALID_POINTER) {
                    return false
                }
                val pointerIndex = ev.findPointerIndex(mActivePointerId)
                val y = ev.getY(pointerIndex)
                val overScrollTop = (y - mInitialMotionY) * DRAG_RATE
                mIsBeingDragged = false
                if (overScrollTop > totalDragDistance) {
                    setRefreshing(true, true)
                } else {
                    mRefreshing = false
                    animateOffsetToStartPosition()
                }
                mActivePointerId = INVALID_POINTER
                return false
            }
        }

        return true
    }

    private fun moveSpinner(overScrollTop: Float): Boolean {
        val yDiff = overScrollTop - mInitialMotionY
        val scrollTop = yDiff * DRAG_RATE
        mCurrentDragPercent = scrollTop / totalDragDistance
        if (mCurrentDragPercent < 0) {
            return false
        }
        val boundedDragPercent = Math.min(1f, Math.abs(mCurrentDragPercent))
        val extraOS = Math.abs(scrollTop) - totalDragDistance
        val slingshotDist = totalDragDistance.toFloat()
        val tensionSlingshotPercent = Math.max(0f,
                Math.min(extraOS, slingshotDist * 2) / slingshotDist)
        val tensionPercent = (tensionSlingshotPercent / 4 - Math.pow(
                (tensionSlingshotPercent / 4).toDouble(), 2.0)).toFloat() * 2f
        val extraMove = slingshotDist * tensionPercent / 2
        val targetY = (slingshotDist * boundedDragPercent + extraMove).toInt()

        mBaseRefreshView!!.setPercent(mCurrentDragPercent, true)
        setTargetOffsetTop(targetY - mCurrentOffsetTop)
        return true
    }

    private fun animateOffsetToStartPosition() {
        mFrom = mCurrentOffsetTop
        mFromDragPercent = mCurrentDragPercent
        val animationDuration = Math.abs((MAX_OFFSET_ANIMATION_DURATION * mFromDragPercent).toLong())

        mAnimateToStartPosition.reset()
        mAnimateToStartPosition.duration = animationDuration
        mAnimateToStartPosition.interpolator = mDecelerateInterpolator
        mAnimateToStartPosition.setAnimationListener(mToStartListener)
        mRefreshView.clearAnimation()
        mRefreshView.startAnimation(mAnimateToStartPosition)
    }

    private fun animateOffsetToCorrectPosition() {
        mFrom = mCurrentOffsetTop
        mFromDragPercent = mCurrentDragPercent

        mAnimateToCorrectPosition.reset()
        mAnimateToCorrectPosition.duration = MAX_OFFSET_ANIMATION_DURATION.toLong()
        mAnimateToCorrectPosition.interpolator = mDecelerateInterpolator
        mRefreshView.clearAnimation()
        mRefreshView.startAnimation(mAnimateToCorrectPosition)

        if (mRefreshing) {
            mBaseRefreshView!!.start()
            if (mNotify) {
                if (mListener != null) {
                    mListener!!.onRefresh()
                }
            }
        } else {
            mBaseRefreshView!!.stop()
            animateOffsetToStartPosition()
        }
        mCurrentOffsetTop = mTarget!!.top
        mTarget!!.setPadding(mTargetPaddingLeft, mTargetPaddingTop, mTargetPaddingRight, totalDragDistance)
    }

    private fun moveToStart(interpolatedTime: Float) {
        val targetTop = mFrom - (mFrom * interpolatedTime).toInt()
        val targetPercent = mFromDragPercent * (1.0f - interpolatedTime)
        val offset = targetTop - mTarget!!.top

        mCurrentDragPercent = targetPercent
        mBaseRefreshView!!.setPercent(mCurrentDragPercent, true)
        mTarget!!.setPadding(mTargetPaddingLeft, mTargetPaddingTop, mTargetPaddingRight, mTargetPaddingBottom + targetTop)
        setTargetOffsetTop(offset)
    }

    fun setRefreshing(refreshing: Boolean) {
        if (mRefreshing != refreshing) {
            setRefreshing(refreshing, false /* notify */)
        }
    }

    private fun setRefreshing(refreshing: Boolean, notify: Boolean) {
        if (mRefreshing != refreshing) {
            mNotify = notify
            ensureTarget()
            mRefreshing = refreshing
            if (mRefreshing) {
                mBaseRefreshView!!.setPercent(1f, true)
                animateOffsetToCorrectPosition()
            } else {
                animateOffsetToStartPosition()
            }
        }
    }

    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val pointerIndex = ev.actionIndex
        val pointerId = ev.getPointerId(pointerIndex)
        if (pointerId == mActivePointerId) {
            val newPointerIndex = if (pointerIndex == 0) 1 else 0
            mActivePointerId = ev.getPointerId(newPointerIndex)
        }
    }

    private fun getMotionEventY(ev: MotionEvent, activePointerId: Int): Float {
        val index = ev.findPointerIndex(activePointerId)
        return if (index < 0) {
            -1f
        } else ev.getY(index)
    }

    private fun setTargetOffsetTop(offset: Int) {
        Log.i("TargetOffset", offset.toString())
        if (mRefreshView.visibility != View.VISIBLE) {
            mRefreshView.visibility = View.VISIBLE
        }
        mTarget!!.offsetTopAndBottom(offset)
        mBaseRefreshView!!.offsetTopAndBottom(offset)
        mCurrentOffsetTop = mTarget!!.top
    }

    private fun canChildScrollUp(): Boolean {
        return mTarget!!.canScrollVertically(-1)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {

        ensureTarget()
        if (mTarget == null)
            return

        val height = measuredHeight
        val width = measuredWidth
        val left = paddingLeft
        val top = paddingTop
        val right = paddingRight
        val bottom = paddingBottom

        mTarget!!.layout(left, top + mCurrentOffsetTop, left + width - right, top + height - bottom + mCurrentOffsetTop)
        mRefreshView.layout(left, top, left + width - right, top + height - bottom)
    }

    fun setOnRefreshListener(listener: OnRefreshListener) {
        mListener = listener
    }

    interface OnRefreshListener {
        fun onRefresh()
    }

    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int): Boolean {
        return (isEnabled && !mRefreshing
                && nestedScrollAxes and ViewCompat.SCROLL_AXIS_VERTICAL != 0)
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int) {
        // Reset the counter of how much leftover scroll needs to be consumed.
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes)
        // Dispatch up to the nested parent
        startNestedScroll(axes and ViewCompat.SCROLL_AXIS_VERTICAL)
        mTotalUnconsumed = 0f
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        // If we are in the middle of consuming, a scroll, then we want to move the spinner back up
        // before allowing the list to scroll
        if (dy > 0 && mTotalUnconsumed > 0) {
            if (dy > mTotalUnconsumed) {
                consumed[1] = dy - mTotalUnconsumed.toInt()
                mTotalUnconsumed = 0f
            } else {
                mTotalUnconsumed -= dy.toFloat()
                consumed[1] = dy
            }
            moveSpinner(mTotalUnconsumed)
        }
        Log.i("NestedPreScroll", mTotalUnconsumed.toString())
        // If a client layout is using a custom start position for the circle
        // view, they mean to hide it again before scrolling the child view
        // If we get back to mTotalUnconsumed == 0 and there is more to go, hide
        // the circle so it isn't exposed if its blocking content is moved
        if (dy > 0 && mTotalUnconsumed == 0f && Math.abs(dy - consumed[1]) > 0) {
            mRefreshView.visibility = View.GONE
        }

        // Now let our nested parent consume the leftovers
        val parentConsumed = mParentScrollConsumed
        if (dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], parentConsumed, null)) {
            consumed[0] += parentConsumed[0]
            consumed[1] += parentConsumed[1]
        }
    }

    override fun getNestedScrollAxes(): Int {
        return mNestedScrollingParentHelper.nestedScrollAxes
    }

    override fun onStopNestedScroll(target: View) {
        mNestedScrollingParentHelper.onStopNestedScroll(target)
        // Finish the spinner for nested scrolling if we ever consumed any
        // unconsumed nested scroll
        if (mTotalUnconsumed > 0) {
            animateOffsetToStartPosition()
            mTotalUnconsumed = 0f
        }
        Log.i("NestedPreScroll", mTotalUnconsumed.toString())
        // Dispatch up our nested parent
        stopNestedScroll()
    }

    override fun onNestedScroll(target: View, dxConsumed: Int, dyConsumed: Int,
                                dxUnconsumed: Int, dyUnconsumed: Int) {
        // Dispatch up to the nested parent first
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
                mParentOffsetInWindow)

        // This is a bit of a hack. Nested scrolling works from the bottom up, and as we are
        // sometimes between two nested scrolling views, we need a way to be able to know when any
        // nested scrolling parent has stopped handling events. We do that by using the
        // 'offset in window 'functionality to see if we have been moved from the event.
        // This is a decent indication of whether we should take over the event stream or not.
        val dy = dyUnconsumed + mParentOffsetInWindow[1]
        if (dy < 0 && !canChildScrollUp()) {
            mTotalUnconsumed += Math.abs(dy).toFloat()
            Log.i("NestedPreScroll", mTotalUnconsumed.toString())
            moveSpinner(mTotalUnconsumed)
        }
    }

    // NestedScrollingChild

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        mNestedScrollingChildHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return mNestedScrollingChildHelper.isNestedScrollingEnabled
    }

    override fun startNestedScroll(axes: Int): Boolean {
        return mNestedScrollingChildHelper.startNestedScroll(axes)
    }

    override fun stopNestedScroll() {
        mNestedScrollingChildHelper.stopNestedScroll()
    }

    override fun hasNestedScrollingParent(): Boolean {
        return mNestedScrollingChildHelper.hasNestedScrollingParent()
    }

    override fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int,
                                      dyUnconsumed: Int, offsetInWindow: IntArray?): Boolean {
        return mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed, offsetInWindow)
    }

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?): Boolean {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(
                dx, dy, consumed, offsetInWindow)
    }

    override fun onNestedPreFling(target: View, velocityX: Float,
                                  velocityY: Float): Boolean {
        return dispatchNestedPreFling(velocityX, velocityY)
    }

    override fun onNestedFling(target: View, velocityX: Float, velocityY: Float,
                               consumed: Boolean): Boolean {
        return dispatchNestedFling(velocityX, velocityY, consumed)
    }

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY)
    }

    companion object {

        private val DRAG_MAX_DISTANCE = 120
        private val DRAG_RATE = .5f
        private val DECELERATE_INTERPOLATION_FACTOR = 2f

        val STYLE_SUN = 0
        val MAX_OFFSET_ANIMATION_DURATION = 700

        private val INVALID_POINTER = -1
    }
}

