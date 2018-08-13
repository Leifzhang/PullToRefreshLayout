package com.wallstreetcn.refresh.refresh_view

import android.content.Context
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable

import com.wallstreetcn.refresh.PullToRefreshView

abstract class BaseRefreshView(context: Context, val refreshLayout: PullToRefreshView?) : Drawable(), Drawable.Callback, Animatable {
    private var mEndOfRefreshing: Boolean = false

    val context: Context?
        get() = refreshLayout?.context

    abstract fun setPercent(percent: Float, invalidate: Boolean)

    abstract fun offsetTopAndBottom(offset: Int)

    override fun invalidateDrawable(who: Drawable) {
        val callback = callback
        callback?.invalidateDrawable(this)
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        val callback = callback
        callback?.scheduleDrawable(this, what, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        val callback = callback
        callback?.unscheduleDrawable(this, what)
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setAlpha(alpha: Int) {

    }

    override fun setColorFilter(cf: ColorFilter?) {

    }

    /**
     * Our animation depend on type of current work of refreshing.
     * We should to do different things when it's end of refreshing
     *
     * @param endOfRefreshing - we will check current state of refresh with this
     */
    fun setEndOfRefreshing(endOfRefreshing: Boolean) {
        mEndOfRefreshing = endOfRefreshing
    }
}
