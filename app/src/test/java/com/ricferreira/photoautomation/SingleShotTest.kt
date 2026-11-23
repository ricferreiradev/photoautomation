package com.ricferreira.photoautomation

import org.junit.Assert.*
import org.junit.Test

class SingleShotTest {
    private val config = RemoteConfig(true, "photos", 0.5, 2f, captureSingleShot = true, captureRequestId = "request-1")
    @Test fun completionBlocksSameCommandAcrossReloadsButNotNextPlay() {
        val key = SingleShot.key("config", config)
        assertTrue(SingleShot.completed("config", RemoteConfig.parse(config.json()), key))
        assertFalse(SingleShot.completed("config", config.copy(captureRequestId = "request-2"), key))
        assertFalse(SingleShot.completed("another-config", config, key))
    }
    @Test fun intervalZoomAndPromptChangesCannotTriggerASecondPhoto() {
        assertTrue(SingleShot.completed("config", config.copy(intervalSeconds = 10.0, zoom = 1f, analysisPrompt = "Novo prompt"), SingleShot.key("config", config)))
    }
    @Test fun continuousModeDoesNotUseSingleShotLatch() {
        assertFalse(SingleShot.completed("config", config.copy(captureSingleShot = false), SingleShot.key("config", config)))
    }
    @Test fun analysisWaitsForTheUploadOfTheSameRequest() {
        assertFalse(SingleShot.receiptReady(config))
        assertFalse(SingleShot.receiptReady(config.copy(singleShotUploadedFileId = "old-photo", singleShotUploadedRequestId = "old-request")))
        assertTrue(SingleShot.receiptReady(config.copy(enabled = false, singleShotUploadedFileId = "photo", singleShotUploadedRequestId = "request-1")))
    }
    @Test fun fieldsSurviveRoundTripAndDefaultToContinuousForOldJson() {
        assertEquals(config, RemoteConfig.parse(config.json()))
        val legacy = config.json().apply { remove("captureSingleShot"); remove("captureRequestId") }
        assertFalse(RemoteConfig.parse(legacy).captureSingleShot)
    }
}
