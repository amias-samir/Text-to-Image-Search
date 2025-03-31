package com.amias.texttoimagesearch.utils

import android.os.Handler
import android.os.Looper

class Debouncer(
    private val delayInMillis: Long,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    fun debounce(action: () -> Unit) {
        // Cancel any existing tasks
        runnable?.let { handler.removeCallbacks(it) }

        // Schedule a new task
        runnable = Runnable { action() }
        handler.postDelayed(runnable!!, delayInMillis)
    }
}
