package com.ricferreira.photoautomation

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.URLEncoder

class ConfigDriveIntegrationTest {
    @Test fun selectedConfigurationAppearsInItsParentFolder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = ConfigStore.selected(context)
        assumeNotNull(id)
        val token = Drive.token(context)
        val report = JSONObject()
        try {
            var metadata = Drive.call(token, "drive/v3/files/$id?fields=id,name,parents,trashed,spaces,ownedByMe&supportsAllDrives=true")
            if (metadata.getBoolean("trashed") && InstrumentationRegistry.getArguments().getString("restoreTrashedConfig") == "true") {
                Drive.call(token, "drive/v3/files/$id?supportsAllDrives=true&fields=id,trashed", "PATCH", JSONObject().put("trashed", false).toString().toByteArray())
                metadata = Drive.call(token, "drive/v3/files/$id?fields=id,name,parents,trashed,spaces,ownedByMe&supportsAllDrives=true")
            }
            report.put("metadata", metadata)
            val parent = metadata.getJSONArray("parents").getString(0)
            val query = URLEncoder.encode("'$parent' in parents", "UTF-8")
            val listed = JSONArray()
            var page = ""
            do {
                val result = Drive.call(token, "drive/v3/files?q=$query&fields=nextPageToken,files(id,name,mimeType,trashed,parents)&pageSize=1000&supportsAllDrives=true&includeItemsFromAllDrives=true&pageToken=$page")
                val items = result.getJSONArray("files")
                for (i in 0 until items.length()) listed.put(items.getJSONObject(i))
                page = result.optString("nextPageToken")
            } while (page.isNotEmpty())
            report.put("listed", listed)
            assertFalse("O JSON selecionado está na lixeira.", metadata.getBoolean("trashed"))
            assertTrue("O JSON existe por ID mas não foi retornado na pasta: $report", (0 until listed.length()).any { listed.getJSONObject(it).getString("id") == id })
        } finally { File(context.filesDir, "drive-diagnostic.json").writeText(report.toString(2)) }
    }
}
