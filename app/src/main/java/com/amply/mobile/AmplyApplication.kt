package com.amply.mobile

import android.app.Application
import com.amply.mobile.pro.limitSettings
import com.amply.mobile.worker.LibraryScanWorker
import com.amply.mobile.worker.MetadataEnrichmentWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AmplyApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        LibraryScanWorker.enqueue(this)
        MetadataEnrichmentWorker.enqueuePeriodic(this)
        container.proStore.start()
        // Keep what the playback service plays with in step with the plan: chosen settings stay in
        // Room, the service reads the effective ones (see pro/ProFeatures.kt limitSettings).
        appScope.launch {
            combine(container.settingsRepository.settings, container.proStore.state) { settings, pro ->
                limitSettings(settings, isPro(pro.owned, settings.debugPro))
            }.collect(container.settingsRepository::mirrorEffective)
        }
    }

    companion object {
        /** Pro is owned through Play, or faked in a debug build to test gated screens. */
        fun isPro(owned: Boolean, debugPro: Boolean): Boolean = owned || (BuildConfig.DEBUG && debugPro)
    }
}
