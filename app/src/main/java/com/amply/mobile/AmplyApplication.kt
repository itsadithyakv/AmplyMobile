package com.amply.mobile

import android.app.Application
import com.amply.mobile.worker.LibraryScanWorker

class AmplyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        LibraryScanWorker.enqueue(this)
    }
}
