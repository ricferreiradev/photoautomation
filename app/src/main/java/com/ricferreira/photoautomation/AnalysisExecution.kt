package com.ricferreira.photoautomation

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.security.MessageDigest

object AnalysisExecution {
    fun shouldRetryStart(sameConfig: Boolean, lastAttempt: Long, now: Long): Boolean =
        !sameConfig || now - lastAttempt >= 60000
    val mutex = Mutex()
    fun latestKey(config: RemoteConfig, imageId: String, modifiedTime: String): String {
        val input = listOf(config.folderId, imageId, modifiedTime, config.analysisModel, config.analysisEffort, config.analysisPrompt)
        return hash(input.joinToString("\n"))
    }

    fun singleShotKey(configId: String, config: RemoteConfig): String {
        val latest = latestKey(config, config.singleShotUploadedFileId, config.analysisLatestOnly.toString())
        val legacy = "$configId:${config.captureRequestId}:$latest"
        if (config.analysisLatestOnly || (config.batchPrompt == AnalysisBatching.DEFAULT_BATCH_PROMPT &&
                config.consolidationPrompt == AnalysisBatching.DEFAULT_CONSOLIDATION_PROMPT)) return legacy
        val prompts = org.json.JSONArray(listOf(config.batchPrompt, config.consolidationPrompt))
        return "$legacy:${hash(prompts.toString())}"
    }

    private fun hash(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun delayUntilNextTick(start: Long, now: Long, seconds: Double): Long {
        val interval = (seconds * 1000).toLong().coerceAtLeast(250)
        val elapsed = (now - start).coerceAtLeast(0)
        return interval - elapsed % interval
    }
    fun saveResult(context: Context, result: JSONObject): JSONObject {
        val answer = result.getString("answer")
        val summary = "${result.getInt("imageCount")} imagens analisadas em ${result.optInt("batchCount", 1)} lotes"
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("analysisResult", answer).putString("analysisSummary", summary)
            .putLong("analysisLastSuccessAt", System.currentTimeMillis()).apply()
        val notified = runCatching { AnalysisNotifications.publish(context, answer, summary) }.getOrDefault(false)
        return result.put("notificationPosted", notified)
    }
}
