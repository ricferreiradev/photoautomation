package com.ricferreira.photoautomation

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Drive {
    // Existing folder browsing requires broader access than drive.file.
    fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive"))).build()
    fun token(context: Context): String {
        val result = Tasks.await(Identity.getAuthorizationClient(context).authorize(request()))
        check(!result.hasResolution()) { "Abra o app e conecte o Google Drive novamente." }
        return requireNotNull(result.accessToken)
    }
    data class Response(val json: JSONObject, val etag: String?)
    fun call(token: String, path: String, method: String = "GET", body: ByteArray? = null, type: String = "application/json", etag: String? = null): JSONObject =
        response(token, path, method, body, type, etag).json

    fun response(token: String, path: String, method: String = "GET", body: ByteArray? = null, type: String = "application/json", etag: String? = null): Response {
        val conn = URL("https://www.googleapis.com/$path").openConnection() as HttpURLConnection
        conn.useCaches = false
        try {
            conn.requestMethod = method
            conn.connectTimeout = 20000; conn.readTimeout = 60000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Cache-Control", "no-cache")
            if (etag != null) conn.setRequestProperty("If-Match", etag)
            if (body != null) { conn.doOutput = true; conn.setRequestProperty("Content-Type", type); conn.outputStream.use { it.write(body) } }
            check(conn.responseCode in 200..299) { if (conn.responseCode == 412) "O JSON mudou em outro celular. Recarregue antes de salvar." else "Google Drive: HTTP ${conn.responseCode}" }
            return Response(JSONObject(conn.inputStream.bufferedReader().use { it.readText() }.ifBlank { "{}" }), conn.getHeaderField("ETag"))
        } finally { conn.disconnect() }
    }
}
