package com.ricferreira.photoautomation

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.net.URLEncoder

class AnalysisScheduleDiagnosticTest {
    @Test fun inspectLatestPhotoWithoutCallingOpenAI() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val token = Drive.token(context)
        val config = ConfigStore.read(token, requireNotNull(ConfigStore.selected(context))).config
        val query = URLEncoder.encode("'${config.folderId}' in parents and trashed = false and mimeType contains 'image/'", "UTF-8")
        val files = Drive.call(token, "drive/v3/files?q=$query&fields=files(id,name,createdTime,modifiedTime)&pageSize=10&orderBy=createdTime%20desc,name%20desc&supportsAllDrives=true&includeItemsFromAllDrives=true").getJSONArray("files")
        val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        val report = JSONObject().put("folderId", config.folderId).put("latestOnly", config.analysisLatestOnly)
            .put("autoEnabled", config.analysisAutoEnabled).put("interval", config.intervalSeconds).put("latestPhotos", files)
        if (files.length() > 0) {
            val latest = files.getJSONObject(0)
            report.put("latestAlreadyAnswered", AnalysisExecution.latestKey(config, latest.getString("id"), latest.optString("modifiedTime")) == prefs.getString("analysisLastAutomaticKey", null))
        }
        File(context.filesDir, "analysis-schedule-diagnostic.json").writeText(report.toString(2))
        assertTrue(config.analysisAutoEnabled)
    }
}
