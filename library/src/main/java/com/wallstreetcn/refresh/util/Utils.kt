package com.wallstreetcn.refresh.util

import android.content.Context

object Utils {

    fun convertDpToPixel(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return Math.round(dp.toFloat() * density)
    }

}
