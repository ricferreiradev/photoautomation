package com.ricferreira.photoautomation

import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder

class LatestPhotoSelectionTest {
    @Test fun latestAvailableImageIsListedInsteadOfOpeningAnOldReceipt() {
        val path = URLDecoder.decode(LatestPhotoSelection.path("photos-folder"), "UTF-8")
        assertTrue(path.startsWith("drive/v3/files?q="))
        assertTrue(path.contains("'photos-folder' in parents and trashed = false"))
        assertTrue(path.contains("mimeType contains 'image/'"))
        assertTrue(path.contains("pageSize=1"))
        assertTrue(path.contains("orderBy=createdTime desc,name desc"))
    }
}
