package com.ricferreira.photoautomation

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RemoteConfigTest {
    @Test fun analysisSettingsDefaultAndRoundTrip() {
        val legacy = RemoteConfig.parse(document())
        assertEquals("gpt-6.2-astra", legacy.analysisModel)
        assertEquals("medium", legacy.analysisEffort)
        val changed = legacy.copy(analysisModel = "gpt-6.2-sol", analysisEffort = "high")
        assertEquals(changed, RemoteConfig.parse(changed.json()))
        assertThrows(IllegalArgumentException::class.java) { RemoteConfig.parse(document().put("analysisEffort", "none")) }
    }

    @Test fun oldConfigGetsDefaultPromptWithoutBreakingCapture() {
        val config = RemoteConfig.parse(document())
        assertEquals(RemoteConfig.DEFAULT_ANALYSIS_PROMPT, config.analysisPrompt)
        assertEquals(AnalysisBatching.DEFAULT_BATCH_PROMPT, config.batchPrompt)
        assertEquals(AnalysisBatching.DEFAULT_CONSOLIDATION_PROMPT, config.consolidationPrompt)
    }
    @Test fun customPromptSurvivesConfigRoundTrip() {
        val config = RemoteConfig.parse(document()).copy(analysisPrompt = "Descreva as imagens.",
            batchPrompt = "Observe os detalhes.\nTranscreva o texto.", consolidationPrompt = "Resuma as descrições.")
        assertEquals(config, RemoteConfig.parse(config.json()))
    }
    @Test fun invalidPromptIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { RemoteConfig.parse(document().put("analysisPrompt", true)) }
        assertThrows(IllegalArgumentException::class.java) { RemoteConfig.parse(document().put("analysisPrompt", "  ")) }
    }
    @Test fun jsonCreationTargetsTheChosenFolder() {
        val metadata = ConfigStore.creationMetadata("chosen_folder", "camera.json")
        assertEquals("chosen_folder", metadata.getJSONArray("parents").getString(0))
        assertEquals("camera.json", metadata.getString("name"))
        assertEquals("application/json", metadata.getString("mimeType"))
    }
    @Test fun invalidJsonNamesAreRejected() {
        for (name in listOf("", "config", "photo.jpg", "folder/config.json")) {
            assertThrows(IllegalArgumentException::class.java) { ConfigStore.creationMetadata("root", name) }
        }
    }
    @Test fun serverCannotCaptureAndClientCannotEdit() {
        assertFalse(DeviceMode.SERVER.canCapture)
        assertTrue(DeviceMode.SERVER.canEdit)
        assertTrue(DeviceMode.CLIENT.canCapture)
        assertFalse(DeviceMode.CLIENT.canEdit)
        assertTrue(DeviceMode.BOTH.canCapture && DeviceMode.BOTH.canEdit)
    }
    private fun document() = JSONObject("""{"enabled":true,"folderId":"folder_123","intervalSeconds":0.5,"zoom":2} """)

    @Test fun fractionalIntervalAndZoomSurviveRoundTrip() {
        val config = RemoteConfig.parse(document())
        assertEquals(0.5, config.intervalSeconds, 0.0)
        assertEquals(2f, config.zoom, 0f)
        assertEquals(config, RemoteConfig.parse(config.json()))
    }
    @Test fun longIntervalsAreAccepted() {
        assertEquals(86400.0, RemoteConfig.parse(document().put("intervalSeconds", 86400)).intervalSeconds, 0.0)
    }
    @Test fun minimumIntervalIsAccepted() {
        assertEquals(0.25, RemoteConfig.parse(document().put("intervalSeconds", 0.25)).intervalSeconds, 0.0)
    }
    @Test fun unsafeIntervalsAreRejected() {
        for (value in listOf(-1.0, 0.0, 0.1, 86401.0)) {
            assertThrows(IllegalArgumentException::class.java) { RemoteConfig.parse(document().put("intervalSeconds", value)) }
        }
        assertThrows(IllegalArgumentException::class.java) { RemoteConfig(true, "folder", Double.NaN, 1f) }
    }
    @Test fun textualBooleanIsRejectedInsteadOfEnablingTheCamera() {
        assertThrows(IllegalArgumentException::class.java) { RemoteConfig.parse(document().put("enabled", "true")) }
    }
    @Test fun numericStringsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { RemoteConfig.parse(document().put("intervalSeconds", "0.5")) }
        assertThrows(IllegalArgumentException::class.java) { RemoteConfig.parse(document().put("zoom", "2")) }
    }
    @Test fun missingFieldsAreRejected() {
        val json = document().apply { remove("enabled") }
        assertThrows(org.json.JSONException::class.java) { RemoteConfig.parse(json) }
    }
    @Test fun invalidFolderIdsAreRejected() {
        for (folder in listOf("", "folder/path", "https://drive.google.com/folder", "foo'bar")) {
            assertThrows(IllegalArgumentException::class.java) { RemoteConfig.parse(document().put("folderId", folder)) }
        }
    }
    @Test fun invalidZoomIsRejected() {
        for (zoom in listOf(0f, -1f, 101f, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { RemoteConfig(true, "root", 1.0, zoom) }
        }
    }
    @Test fun cameraRequiresFreshEnabledConfiguration() {
        val config = RemoteConfig.parse(document())
        assertTrue(CapturePolicy.enabled(config, 1000, 1000))
        assertTrue(CapturePolicy.enabled(config, 1000, 16000))
        assertFalse(CapturePolicy.enabled(config, 1000, 16001))
        assertFalse(CapturePolicy.enabled(config.copy(enabled = false), 1000, 1000))
        assertFalse(CapturePolicy.enabled(null, 1000, 1000))
        assertFalse(CapturePolicy.enabled(config, 1000, 999))
    }
}
