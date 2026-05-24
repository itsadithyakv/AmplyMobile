package com.amply.mobile

import android.app.Application
import com.amply.mobile.worker.LibraryScanWorker
import com.amply.mobile.worker.MetadataEnrichmentWorker

class AmplyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        LibraryScanWorker.enqueue(this)
        MetadataEnrichmentWorker.enqueuePeriodic(this)
    }
}
