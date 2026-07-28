package com.hemanth.vaani

import android.app.Application
import com.hemanth.vaani.call.CallNotificationHelper

class VaaniApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CallNotificationHelper.ensureChannel(this)
    }
}
