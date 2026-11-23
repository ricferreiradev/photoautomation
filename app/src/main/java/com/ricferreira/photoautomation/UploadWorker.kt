package com.ricferreira.photoautomation

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val queue = File(applicationContext.filesDir, "queue")
            val pending = queue.listFiles()?.filter { it.extension == "json" }?.sortedBy { it.name }.orEmpty()
            if (pending.isEmpty()) return@withContext Result.success()
            val token = Drive.token(applicationContext)
            for (metadata in pending) {
                if (isStopped) return@withContext Result.retry()
                val file = File(queue, "${metadata.nameWithoutExtension}.jpg")
                if (!file.exists()) { metadata.delete(); continue }
                val saved = JSONObject(metadata.readText())
                // Recover from an interrupted response without uploading a second copy.
                val key = metadata.nameWithoutExtension
                val query = URLEncoder.encode("trashed = false and appProperties has { key='captureId' and value='$key' }", "UTF-8")
                val existing = Drive.call(token, "drive/v3/files?q=$query&fields=files(id)").getJSONArray("files")
                var driveId: String
                if (existing.length() == 0) {
                    val json = JSONObject().put("name", saved.getString("name"))
                        .put("parents", JSONArray().put(saved.getString("folder")))
                        .put("appProperties", JSONObject().put("captureId", key))
                    val boundary = "photo_$key"
                    val header = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$json\r\n--$boundary\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray()
                    val body = header + file.readBytes() + "\r\n--$boundary--\r\n".toByteArray()
                    driveId = Drive.call(token, "upload/drive/v3/files?uploadType=multipart&fields=id", "POST", body, "multipart/related; boundary=$boundary").getString("id")
                } else driveId = existing.getJSONObject(0).getString("id")
                if (saved.optBoolean("singleShot")) SingleShot.recordUpload(token, saved.getString("configId"), saved.optString("captureRequestId"), driveId, saved.getString("folder"))
                SessionStats.uploaded(applicationContext, saved.optString("session"), key)
                // Remove local content only after Drive confirms upload or an existing copy.
                file.delete(); metadata.delete()
            }
            applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().remove("uploadError").apply()
            Result.success()
        } catch (e: Exception) {
            applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("uploadError", e.message).apply()
            Result.retry()
        }
    }
    companion object {
        fun enqueue(context: Context) {
            val work = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("photo-uploads", ExistingWorkPolicy.APPEND_OR_REPLACE, work)
        }
    }
}
