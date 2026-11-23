package com.ricferreira.photoautomation

import android.content.Context
import android.graphics.Bitmap
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RemotePhotoPreview(val bitmap: Bitmap, val metadata: JSONObject, val downloadedBytes: Int)

/** Manual preview or confirmed single-shot receipt. Cache restoration never contacts Drive. */
object RemotePhoto {
    private fun image(context: Context) = File(context.filesDir, "remote-photo.jpg")
    private fun info(context: Context) = File(context.filesDir, "remote-photo.json")
    @Synchronized
    fun cached(context: Context, folderId: String, expectedFileId: String? = null): RemotePhotoPreview? = runCatching {
        val metadata = JSONObject(info(context).readText())
        if (metadata.getString("folderId") != folderId) return null
        if (expectedFileId != null && metadata.optString("id") != expectedFileId) return null
        val bitmap = LastPhoto.loadFile(image(context)) ?: return null
        RemotePhotoPreview(bitmap, metadata, 0)
    }.getOrNull()

    @Synchronized
    fun fetch(context: Context, token: String, folderId: String, targetFileId: String? = null): RemotePhotoPreview {
        require(folderId.matches(Regex("[A-Za-z0-9_-]+"))) { "Pasta inválida." }
        val query = URLEncoder.encode("'$folderId' in parents and trashed = false and mimeType contains 'image/'", "UTF-8")
        val metadata = if (targetFileId == null) {
            val files = Drive.call(token, "drive/v3/files?q=$query&fields=files(id,name,createdTime,modifiedTime,size,thumbnailLink)&pageSize=1&orderBy=createdTime%20desc,name%20desc&supportsAllDrives=true&includeItemsFromAllDrives=true").getJSONArray("files")
            check(files.length() > 0) { "Nenhuma foto encontrada na pasta configurada no JSON." }
            files.getJSONObject(0)
        } else {
            require(targetFileId.matches(Regex("[A-Za-z0-9_-]+"))) { "ID da foto única inválido." }
            Drive.call(token, "drive/v3/files/$targetFileId?fields=id,name,createdTime,modifiedTime,size,thumbnailLink,parents,trashed,mimeType&supportsAllDrives=true").also {
                check(!it.optBoolean("trashed") && it.getString("mimeType").startsWith("image/")) { "A foto única não está disponível no Drive." }
                val parents = it.getJSONArray("parents")
                check(folderId == "root" || (0 until parents.length()).any { index -> parents.getString(index) == folderId }) { "A foto única não está na pasta configurada." }
            }
        }.put("folderId", folderId)
        val old = runCatching { JSONObject(info(context).readText()) }.getOrNull()
        if (old?.optString("folderId") == folderId && old.optString("id") == metadata.getString("id") && old.optString("modifiedTime") == metadata.optString("modifiedTime")) {
            cached(context, folderId)?.let { return it }
        }
        val thumbnail = metadata.optString("thumbnailLink")
        val url = if (thumbnail.isNotBlank()) URL(thumbnail)
            else URL("https://www.googleapis.com/drive/v3/files/${metadata.getString("id")}?alt=media&supportsAllDrives=true")
        require(url.protocol == "https" && (url.host.endsWith(".googleusercontent.com") || url.host.endsWith(".googleapis.com") || url.host.endsWith(".google.com"))) { "Endereço de imagem não reconhecido." }
        if (thumbnail.isBlank()) check(metadata.optString("size").toLongOrNull()?.let { it <= 5L * 1024 * 1024 } != false) {
            "A foto não tem prévia reduzida e o original excede 5 MB. Tente novamente quando a prévia estiver disponível."
        }
        val conn = url.openConnection() as HttpURLConnection
        val data = try {
            conn.connectTimeout = 15000; conn.readTimeout = 60000
            conn.setRequestProperty("Authorization", "Bearer $token")
            check(conn.responseCode in 200..299) { "Não foi possível baixar a prévia (HTTP ${conn.responseCode})." }
            conn.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 5 * 1024 * 1024) { "Prévia maior que o limite de 5 MB." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally { conn.disconnect() }
        metadata.put("reduced", thumbnail.isNotBlank())
        // Validate before replacing a previously usable cache.
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "A prévia recebida não é uma imagem válida." }
        val target = AtomicFile(image(context))
        info(context).delete()
        val stream = target.startWrite()
        try { stream.write(data); target.finishWrite(stream) }
        catch (e: Exception) { target.failWrite(stream); throw e }
        val bitmap = requireNotNull(LastPhoto.loadFile(image(context))) { "Não foi possível abrir a foto baixada." }
        info(context).writeText(metadata.toString())
        return RemotePhotoPreview(bitmap, metadata, data.size)
    }
}
