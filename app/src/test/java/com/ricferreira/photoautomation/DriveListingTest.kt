package com.ricferreira.photoautomation

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DriveListingTest {
    @Test fun listingUsesTheSelectedSubfolderAsParent() {
        assertTrue(DriveListing.query("nested_folder", true).contains("'nested_folder' in parents"))
        assertFalse(DriveListing.query("nested_folder", true).contains("'root' in parents"))
    }
    @Test fun folderShortcutOpensItsTargetRatherThanTheShortcutId() {
        val json = JSONObject("""{"id":"shortcut","name":"Subpasta","mimeType":"application/vnd.google-apps.shortcut","shortcutDetails":{"targetId":"real_subfolder","targetMimeType":"application/vnd.google-apps.folder"}}""")
        val item = requireNotNull(DriveListing.item(json, false))
        assertEquals("real_subfolder", item.id)
        assertTrue(item.folder)
        assertTrue(item.shortcut)
    }
    @Test fun regularFolderIsAvailableInBothPickerTypes() {
        val json = JSONObject("""{"id":"nested","name":"Subpasta","mimeType":"application/vnd.google-apps.folder"}""")
        assertTrue(requireNotNull(DriveListing.item(json, false)).folder)
        assertTrue(requireNotNull(DriveListing.item(json, true)).folder)
    }
    @Test fun jsonShortcutCanBeSelectedButIsNotOfferedAsFolder() {
        val json = JSONObject("""{"id":"shortcut","name":"controle.json","mimeType":"application/vnd.google-apps.shortcut","shortcutDetails":{"targetId":"real_json","targetMimeType":"application/json"}}""")
        assertEquals("real_json", requireNotNull(DriveListing.item(json, true)).id)
        assertNull(DriveListing.item(json, false))
    }
    @Test fun unrelatedAndBrokenShortcutsAreNotShown() {
        assertNull(DriveListing.item(JSONObject("""{"id":"shortcut","name":"Outro","mimeType":"application/vnd.google-apps.shortcut"}"""), true))
        assertNull(DriveListing.item(JSONObject("""{"id":"image","name":"image.jpg","mimeType":"image/jpeg"}"""), true))
    }
}
