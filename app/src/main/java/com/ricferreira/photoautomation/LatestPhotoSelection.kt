package com.ricferreira.photoautomation

import java.net.URLEncoder

object LatestPhotoSelection {
    fun path(folderId: String): String {
        require(folderId.matches(Regex("[A-Za-z0-9_-]+")))
        val query = URLEncoder.encode("'$folderId' in parents and trashed = false and mimeType contains 'image/'", "UTF-8")
        return "drive/v3/files?q=$query&fields=files(id,name,mimeType,modifiedTime)&pageSize=1&orderBy=createdTime%20desc,name%20desc&supportsAllDrives=true&includeItemsFromAllDrives=true"
    }
}
