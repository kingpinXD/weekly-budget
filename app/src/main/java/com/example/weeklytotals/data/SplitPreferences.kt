package com.example.weeklytotals.data

import android.content.Context

class SplitPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("split_prefs", Context.MODE_PRIVATE)

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "SplitPreferences"
    }
}
