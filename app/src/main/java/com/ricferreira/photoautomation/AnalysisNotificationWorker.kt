package com.ricferreira.photoautomation

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class AnalysisNotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = if (AnalysisNotifications.retryPending(applicationContext)) Result.success() else Result.retry()
    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("analysis-notification-delivery", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<AnalysisNotificationWorker>().setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
    }
}
