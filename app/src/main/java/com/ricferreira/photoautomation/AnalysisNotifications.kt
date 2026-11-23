package com.ricferreira.photoautomation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.util.UUID

object AnalysisNotifications {
    const val RESULT_ID = "analysisResultId"
    private const val CHANNEL = "analysis_results"

    @Synchronized
    fun publish(context: Context, answer: String, summary: String): Boolean {
        val resultId = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "analysis-results").apply { mkdirs() }
        val record = JSONObject().put("answer", answer).put("summary", summary)
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val id = prefs.getInt("analysisNotificationId", 1000).let { if (it == Int.MAX_VALUE) 1000 else it + 1 }
        prefs.edit().putInt("analysisNotificationId", id).commit()
        record.put("notificationId", id).put("notificationPending", true)
        val file = File(directory, "$resultId.json")
        file.writeText(record.toString())
        // Persist retry work before posting: a process interruption cannot lose the response's alert.
        AnalysisNotificationWorker.enqueue(context)
        return deliver(context, file, record)
    }
    private fun deliver(context: Context, file: File, record: JSONObject): Boolean {
      return try {
        val answer = record.getString("answer")
        val summary = record.getString("summary")
        val resultId = file.nameWithoutExtension
        val id = record.getInt("notificationId")
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Respostas das análises", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisos quando uma análise de imagens recebe sua resposta."
        })
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL).importance == NotificationManager.IMPORTANCE_NONE) return false
        // Keep headroom below Android's per-app limit without removing saved answers.
        val previous = manager.activeNotifications.filter { it.notification.channelId == CHANNEL && it.id != id }.sortedBy { it.postTime }
        previous.take((previous.size - 19).coerceAtLeast(0)).forEach { manager.cancel(it.id) }
        val intent = Intent(context, MainActivity::class.java).putExtra(RESULT_ID, resultId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Análise concluída").setContentText(answer)
            .setStyle(Notification.BigTextStyle().bigText(answer).setSummaryText(summary))
            .setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(false).setVisibility(Notification.VISIBILITY_PRIVATE).build()
        manager.notify(id, notification)
        file.writeText(record.put("notificationPending", false).toString())
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().remove("analysisNotificationError").apply()
        true
    } catch (e: Exception) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("analysisNotificationError", e.message ?: "Falha ao emitir notificação.").apply()
        false
      }
    }
    @Synchronized
    fun retryPending(context: Context): Boolean {
        var allPosted = true
        File(context.filesDir, "analysis-results").listFiles()?.filter { it.extension == "json" }?.sortedBy { it.lastModified() }?.forEach { file ->
            val record = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return@forEach
            if (record.optBoolean("notificationPending") && !deliver(context, file, record)) allPosted = false
        }
        return allPosted
    }
    fun result(context: Context, id: String?): JSONObject? {
        if (id == null || !id.matches(Regex("[a-fA-F0-9-]{36}"))) return null
        return runCatching { JSONObject(File(context.filesDir, "analysis-results/$id.json").readText()) }.getOrNull()
    }
}
