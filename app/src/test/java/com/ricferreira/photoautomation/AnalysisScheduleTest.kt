package com.ricferreira.photoautomation

import org.junit.Assert.*
import org.junit.Test

class AnalysisScheduleTest {
    @Test fun stoppedServiceCanRecoverAfterFirstAttempt() {
        assertFalse(AnalysisExecution.shouldRetryStart(true, 1000, 2000))
        assertTrue(AnalysisExecution.shouldRetryStart(true, 1000, 61000))
        assertTrue(AnalysisExecution.shouldRetryStart(false, 1000, 2000))
    }
    @Test fun automaticAndLatestOnlyAreIndependentInAllCombinations() {
        for (automatic in listOf(false, true)) for (latest in listOf(false, true)) {
            val saved = RemoteConfig.parse(config.copy(analysisAutoEnabled = automatic, analysisLatestOnly = latest).json())
            assertEquals(automatic, saved.analysisAutoEnabled)
            assertEquals(latest, saved.analysisLatestOnly)
        }
    }
    private val config = RemoteConfig(false, "folder", 3.0, 1f)
    @Test fun oldConfigurationsLeaveAutomaticBillingDisabled() {
        val old = config.json().apply { remove("analysisLatestOnly"); remove("analysisAutoEnabled") }
        val parsed = RemoteConfig.parse(old)
        assertFalse(parsed.analysisLatestOnly)
        assertFalse(parsed.analysisAutoEnabled)
    }
    @Test fun optionsSurviveRoundTripIndependentlyOfCaptureEnabled() {
        val changed = config.copy(analysisLatestOnly = true, analysisAutoEnabled = true)
        assertEquals(changed, RemoteConfig.parse(changed.json()))
        assertFalse(changed.enabled)
    }
    @Test fun stringValuesCannotEnableAutomaticBilling() {
        for (field in listOf("analysisLatestOnly", "analysisAutoEnabled")) {
            assertThrows(IllegalArgumentException::class.java) { RemoteConfig.parse(config.json().put(field, "true")) }
        }
    }
    @Test fun slowApiSkipsMissedTicksInsteadOfStartingCatchUpRequests() {
        assertEquals(2000L, AnalysisExecution.delayUntilNextTick(1000, 8000, 3.0))
        assertEquals(2500L, AnalysisExecution.delayUntilNextTick(1000, 1500, 3.0))
        assertEquals(3000L, AnalysisExecution.delayUntilNextTick(1000, 4000, 3.0))
    }
    @Test fun fractionalIntervalUsesTheCaptureCadence() {
        assertEquals(400L, AnalysisExecution.delayUntilNextTick(1000, 1600, 0.5))
        assertEquals(250L, AnalysisExecution.delayUntilNextTick(1000, 1000, 0.25))
    }
    @Test fun repeatedPhotoKeyChangesOnlyWhenRelevantInputChanges() {
        val key = AnalysisExecution.latestKey(config, "photo", "version")
        assertEquals(key, AnalysisExecution.latestKey(config.copy(enabled = true, zoom = 2f, intervalSeconds = 1.0), "photo", "version"))
        assertNotEquals(key, AnalysisExecution.latestKey(config, "new-photo", "version"))
        assertNotEquals(key, AnalysisExecution.latestKey(config, "photo", "new-version"))
        assertNotEquals(key, AnalysisExecution.latestKey(config.copy(analysisPrompt = "Outro prompt"), "photo", "version"))
        assertNotEquals(key, AnalysisExecution.latestKey(config.copy(folderId = "other"), "photo", "version"))
    }
}
