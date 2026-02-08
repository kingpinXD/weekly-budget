package com.example.weeklytotals

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class WeeklyTotalsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Must be called before any other Firebase Database usage
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
}
