package com.wallstreetcn.refresh.util

import android.text.TextUtils

object Logger {

    private val TAG = "Phoenix"

    /**
     * Set true or false if you want read logs or not
     */
    private val logEnabled_d = false
    private val logEnabled_i = false
    private val logEnabled_e = false

    private val location: String
        get() {
            val className = Logger::class.java.name
            val traces = Thread.currentThread()
                    .stackTrace
            var found = false

            for (trace in traces) {
                try {
                    if (found) {
                        if (!trace.className.startsWith(className)) {
                            val clazz = Class.forName(trace.className)
                            return ("[" + getClassName(clazz) + ":"
                                    + trace.methodName + ":"
                                    + trace.lineNumber + "]: ")
                        }
                    } else if (trace.className.startsWith(className)) {
                        found = true
                    }
                } catch (ignored: ClassNotFoundException) {
                }

            }

            return "[]: "
        }

    fun d() {
        if (logEnabled_d) {
            android.util.Log.v(TAG, location)
        }
    }

    fun d(msg: String) {
        if (logEnabled_d) {
            android.util.Log.v(TAG, location + msg)
        }
    }

    fun i(msg: String) {
        if (logEnabled_i) {
            android.util.Log.i(TAG, location + msg)
        }
    }

    fun i() {
        if (logEnabled_i) {
            android.util.Log.i(TAG, location)
        }
    }

    fun e(msg: String) {
        if (logEnabled_e) {
            android.util.Log.e(TAG, location + msg)
        }
    }

    fun e(msg: String, e: Throwable) {
        if (logEnabled_e) {
            android.util.Log.e(TAG, location + msg, e)
        }
    }

    fun e(e: Throwable) {
        if (logEnabled_e) {
            android.util.Log.e(TAG, location, e)
        }
    }

    fun e() {
        if (logEnabled_e) {
            android.util.Log.e(TAG, location)
        }
    }

    private fun getClassName(clazz: Class<*>?): String {
        return if (clazz != null) {
            if (!TextUtils.isEmpty(clazz.simpleName)) {
                clazz.simpleName
            } else getClassName(clazz.enclosingClass)

        } else ""

    }

}
