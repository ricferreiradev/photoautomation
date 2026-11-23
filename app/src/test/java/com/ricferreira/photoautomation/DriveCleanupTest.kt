package com.ricferreira.photoautomation

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DriveCleanupTest {
    private fun photo() = JSONObject("""{"id":"photo","mimeType":"image/jpeg","parents":["photos"],"trashed":false}""")
    @Test fun regularFileInChosenFolderIsEligible() {
        assertTrue(DriveCleanup.eligible(photo(), "photos", "config"))
    }
    @Test fun activeConfigurationCannotBeTrashedEvenInsideThePhotosFolder() {
        assertFalse(DriveCleanup.eligible(photo().put("id", "config").put("mimeType", "application/json"), "photos", "config"))
    }
    @Test fun fileMovedElsewhereAfterReviewIsNotTrashed() {
        assertFalse(DriveCleanup.eligible(photo(), "other", "config"))
    }
    @Test fun subfoldersArePreserved() {
        assertFalse(DriveCleanup.eligible(photo().put("mimeType", "application/vnd.google-apps.folder"), "photos", "config"))
    }
    @Test fun alreadyTrashedAndParentlessFilesAreSkipped() {
        assertFalse(DriveCleanup.eligible(photo().put("trashed", true), "photos", "config"))
        assertFalse(DriveCleanup.eligible(photo().apply { remove("parents") }, "photos", "config"))
    }
}
