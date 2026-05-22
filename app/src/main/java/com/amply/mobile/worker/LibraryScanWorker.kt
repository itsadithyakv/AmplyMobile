package com.amply.mobile.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.amply.mobile.AmplyApplication
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class LibraryScanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AmplyApplication).container
        return runCatching {
            container.libraryRepository.scanLibrary()
            container.playlistRepository.regenerateSmartPlaylists(container.libraryRepository.songs.first())
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val UNIQUE_NAME = "amply_periodic_library_scan"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<LibraryScanWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
