package com.ricferreira.photoautomation

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class SingleShotRecoveryTest {
    @Test fun confirmedUploadMatchesCompletedCapture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        val id = requireNotNull(ConfigStore.selected(context))
        val token = Drive.token(context)
        val config = ConfigStore.read(token, id).config
        val completed = prefs.getString("singleShotCompletedKey", null)
        assertTrue("O teste exige um comando único já capturado.", SingleShot.completed(id, config, completed))
        val recovered = SingleShot.recoverUpload(token, id, config, completed,
            prefs.getString("singleShotCaptureId", null) ?: prefs.getString("lastPhotoId", null))
        assertTrue("A foto enviada precisa ter confirmação no JSON.", SingleShot.receiptReady(recovered))
        assertEquals(config.captureRequestId, recovered.singleShotUploadedRequestId)
    }
}
