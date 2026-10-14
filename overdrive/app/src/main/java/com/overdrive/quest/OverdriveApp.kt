package com.overdrive.quest

import android.app.Application
import com.overdrive.quest.core.AppGraph

class OverdriveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
