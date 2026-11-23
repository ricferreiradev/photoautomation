package com.ricferreira.photoautomation

import org.junit.Assert.*
import org.junit.Test

class WatchShutterTest {
    @Test fun onlyRegisteredWatchVolumeUpCanControlCapture() {
        val watch = "Redmi Watch 5 Lite B692"
        assertTrue(WatchShutter.matches(watch, watch, 24))
        assertFalse(WatchShutter.matches(watch, "gpio-keys", 24))
        assertFalse(WatchShutter.matches(watch, watch, 25))
        assertFalse(WatchShutter.matches(null, watch, 24))
        assertFalse(WatchShutter.matches(watch, null, 24))
    }
}
