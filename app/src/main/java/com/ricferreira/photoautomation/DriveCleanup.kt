package com.ricferreira.photoautomation

import org.json.JSONObject
import java.net.URLEncoder

data class CleanupFile(val id: String, val name: String)
data class CleanupPlan(val folderId: String, val folderName: String, val files: List<CleanupFile>)
data class CleanupResult(val deleted: Int, val skipped: Int, val errors: List<String>)

object DriveCleanup {
    private const val FOLDER = "application/vnd.google-apps.folder"
    fun eligible(metadata: JSONObject, folderId: String, configId: String): Boolean {
        if (metadata.getString("id") == configId || metadata.optBoolean("trashed") || metadata.getString("mimeType") == FOLDER) return false
        val parents = metadata.optJSONArray("parents") ?: return false
        return (0 until parents.length()).any { parents.getString(it) == folderId }
    }
    fun plan(token: String, folderId: String, configId: String): CleanupPlan {
        require(folderId.matches(Regex("[A-Za-z0-9_-]+"))) { "Pasta inválida." }
        val folder = Drive.call(token, "drive/v3/files/$folderId?fields=id,name,mimeType,trashed&supportsAllDrives=true")
        check(folder.getString("mimeType") == FOLDER && !folder.optBoolean("trashed")) { "A pasta escolhida não está disponível." }
        val realId = folder.getString("id")
        val query = URLEncoder.encode("'$realId' in parents and trashed = false and mimeType != '$FOLDER'", "UTF-8")
        val files = mutableListOf<CleanupFile>()
        var page = ""
        do {
            val response = Drive.call(token, "drive/v3/files?q=$query&fields=nextPageToken,files(id,name,mimeType,parents,trashed)&pageSize=1000&supportsAllDrives=true&includeItemsFromAllDrives=true&pageToken=$page")
            val items = response.getJSONArray("files")
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                if (eligible(item, realId, configId)) files.add(CleanupFile(item.getString("id"), item.getString("name")))
            }
            page = response.optString("nextPageToken")
        } while (page.isNotEmpty())
        return CleanupPlan(realId, folder.getString("name"), files.distinctBy { it.id })
    }
    fun execute(token: String, plan: CleanupPlan, configId: String, progress: (Int, Int) -> Unit): CleanupResult {
        var deleted = 0
        var skipped = 0
        val errors = mutableListOf<String>()
        // Only the reviewed snapshot is touched; new uploads after confirmation remain intact.
        plan.files.forEachIndexed { index, item ->
            try {
                val current = Drive.call(token, "drive/v3/files/${item.id}?fields=id,name,mimeType,parents,trashed&supportsAllDrives=true")
                if (!eligible(current, plan.folderId, configId)) skipped++
                else {
                    val result = Drive.call(token, "drive/v3/files/${item.id}?supportsAllDrives=true&fields=id,trashed", "PATCH",
                        JSONObject().put("trashed", true).toString().toByteArray())
                    check(result.getBoolean("trashed")) { "O Drive não confirmou a alteração." }
                    deleted++
                }
            } catch (e: Exception) { errors.add("${item.name}: ${e.message}") }
            progress(index + 1, plan.files.size)
        }
        return CleanupResult(deleted, skipped, errors)
    }
}
