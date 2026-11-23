package com.ricferreira.photoautomation

import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AnalysisNotificationTest {
    @Test fun repeatedAnswersEachProduceTheirOwnNotification() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        val ids = mutableListOf<Int>()
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            repeat(2) {
                assertTrue(AnalysisNotifications.publish(context, "Teste de notificação • Não foi possível determinar.", "Verificação local, sem chamada à IA"))
                ids.add(prefs.getInt("analysisNotificationId", 0))
            }
            assertNotEquals(ids[0], ids[1])
            // Android processes notify asynchronously.
            val deadline = android.os.SystemClock.elapsedRealtime() + 5000
            while (!manager.activeNotifications.map { it.id }.containsAll(ids) && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
            assertTrue(manager.activeNotifications.map { it.id }.containsAll(ids))
            assertTrue(AnalysisNotifications.retryPending(context))
        } finally { ids.forEach { manager.cancel(it) } }
    }
}
