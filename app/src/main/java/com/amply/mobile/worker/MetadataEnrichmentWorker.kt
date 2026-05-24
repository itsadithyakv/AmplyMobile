package com.amply.mobile.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.amply.mobile.AmplyApplication
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class MetadataEnrichmentWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AmplyApplication).container
        return runCatching {
            val paused = container.settingsRepository.metadataFetchPaused()
            if (paused) return Result.success()
            val songs = container.libraryRepository.songs.first()
                .sortedWith(
                    compareBy(
                        { it.lastPlayedAtSec ?: Long.MAX_VALUE },
                        { it.addedAtSec },
                    ),
                )
                .take(MAX_SONGS_PER_RUN)
            songs.forEach { song ->
                container.lyricsRepository.loadOrFetch(song)
                container.metadataRepository.enrichGenre(song)
                container.metadataRepository.artistInfo(song)
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val PERIODIC_NAME = "amply_periodic_metadata_enrichment"
        private const val ONE_TIME_NAME = "amply_manual_metadata_enrichment"
        private const val MAX_SONGS_PER_RUN = 24

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<MetadataEnrichmentWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<MetadataEnrichmentWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
