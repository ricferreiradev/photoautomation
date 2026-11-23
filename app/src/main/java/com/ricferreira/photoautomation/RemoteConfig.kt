package com.ricferreira.photoautomation

import android.content.Context
import org.json.JSONObject

data class RemoteConfig(val enabled: Boolean, val folderId: String, val intervalSeconds: Double, val zoom: Float, val analysisPrompt: String = DEFAULT_ANALYSIS_PROMPT,
    val analysisLatestOnly: Boolean = false, val analysisAutoEnabled: Boolean = false,
    val captureSingleShot: Boolean = false, val captureRequestId: String = "",
    val singleShotUploadedFileId: String = "", val singleShotUploadedRequestId: String = "",
    val analysisModel: String = DEFAULT_ANALYSIS_MODEL, val analysisEffort: String = DEFAULT_ANALYSIS_EFFORT,
    val batchPrompt: String = AnalysisBatching.DEFAULT_BATCH_PROMPT,
    val consolidationPrompt: String = AnalysisBatching.DEFAULT_CONSOLIDATION_PROMPT) {
    init {
        require(analysisModel in ANALYSIS_MODELS) { "Modelo de análise inválido." }
        require(analysisEffort in ANALYSIS_EFFORTS) { "Esforço de raciocínio inválido." }
        require(folderId.matches(Regex("[A-Za-z0-9_-]+"))) { "ID da pasta inválido." }
        require(intervalSeconds.isFinite() && intervalSeconds in 0.25..86400.0) { "Intervalo deve ser entre 0,25 e 86400 segundos." }
        require(zoom.isFinite() && zoom in 0.1f..100f) { "Zoom deve ser entre 0,1 e 100x (limitado à câmera)." }
        require(analysisPrompt.isNotBlank() && analysisPrompt.length <= 20000) { "O prompt deve conter de 1 a 20000 caracteres." }
        require(batchPrompt.isNotBlank() && batchPrompt.length <= 20000) { "O prompt dos lotes deve conter de 1 a 20000 caracteres." }
        require(consolidationPrompt.isNotBlank() && consolidationPrompt.length <= 20000) { "O prompt de consolidação deve conter de 1 a 20000 caracteres." }
    }
    fun json() = JSONObject().put("enabled", enabled).put("folderId", folderId)
        .put("intervalSeconds", intervalSeconds).put("zoom", zoom.toDouble()).put("analysisPrompt", analysisPrompt)
        .put("batchPrompt", batchPrompt).put("consolidationPrompt", consolidationPrompt)
        .put("analysisLatestOnly", analysisLatestOnly).put("analysisAutoEnabled", analysisAutoEnabled)
        .put("analysisModel", analysisModel).put("analysisEffort", analysisEffort)
        .put("captureSingleShot", captureSingleShot).put("captureRequestId", captureRequestId)
        .put("singleShotUploadedFileId", singleShotUploadedFileId).put("singleShotUploadedRequestId", singleShotUploadedRequestId)

    companion object {
        const val DEFAULT_ANALYSIS_MODEL = "gpt-6.2-astra"
        const val DEFAULT_ANALYSIS_EFFORT = "medium"
        val ANALYSIS_MODELS = listOf(DEFAULT_ANALYSIS_MODEL, "gpt-6.2-sol")
        val ANALYSIS_EFFORTS = listOf("low", DEFAULT_ANALYSIS_EFFORT, "high", "xhigh", "max")
        const val DEFAULT_ANALYSIS_PROMPT = "Descreva o conteúdo visível na imagem. Transcreva o texto legível e indique os trechos que não conseguiu ler."
        fun parse(json: JSONObject): RemoteConfig {
            require(json.get("enabled") is Boolean) { "enabled deve ser true ou false." }
            require(json.get("folderId") is String) { "folderId deve ser texto." }
            require(json.get("intervalSeconds") is Number && json.get("zoom") is Number) { "intervalSeconds e zoom devem ser números." }
            for (field in listOf("analysisPrompt", "batchPrompt", "consolidationPrompt")) require(!json.has(field) || json.get(field) is String) { "$field deve ser texto." }
            for (field in listOf("analysisLatestOnly", "analysisAutoEnabled", "captureSingleShot")) require(!json.has(field) || json.get(field) is Boolean) { "$field deve ser true ou false." }
            for (field in listOf("captureRequestId", "singleShotUploadedFileId", "singleShotUploadedRequestId", "analysisModel", "analysisEffort")) require(!json.has(field) || json.get(field) is String) { "$field deve ser texto." }
            return RemoteConfig(json.getBoolean("enabled"), json.getString("folderId"), json.getDouble("intervalSeconds"), json.getDouble("zoom").toFloat(), json.optString("analysisPrompt", DEFAULT_ANALYSIS_PROMPT), json.optBoolean("analysisLatestOnly", false), json.optBoolean("analysisAutoEnabled", false),
                json.optBoolean("captureSingleShot", false), json.optString("captureRequestId", ""), json.optString("singleShotUploadedFileId", ""), json.optString("singleShotUploadedRequestId", ""),
                json.optString("analysisModel", DEFAULT_ANALYSIS_MODEL), json.optString("analysisEffort", DEFAULT_ANALYSIS_EFFORT),
                json.optString("batchPrompt", AnalysisBatching.DEFAULT_BATCH_PROMPT),
                json.optString("consolidationPrompt", AnalysisBatching.DEFAULT_CONSOLIDATION_PROMPT))
        }
    }
}

data class ConfigSnapshot(val config: RemoteConfig, val etag: String?)

class ConfigInTrashException : IllegalStateException("O JSON está na lixeira do Drive. Restaure o arquivo para voltar a usá-lo.")

object CapturePolicy {
    fun enabled(config: RemoteConfig?, confirmedAt: Long, now: Long): Boolean =
        config?.enabled == true && now >= confirmedAt && now - confirmedAt <= 15000
}

object ConfigStore {
    fun selected(context: Context): String? = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("configId", null)
    fun read(token: String, id: String): ConfigSnapshot {
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "ID do JSON inválido." }
        val metadata = Drive.call(token, "drive/v3/files/$id?fields=id,trashed&supportsAllDrives=true")
        if (metadata.getBoolean("trashed")) throw ConfigInTrashException()
        val response = Drive.response(token, "drive/v3/files/$id?alt=media")
        return ConfigSnapshot(RemoteConfig.parse(response.json), response.etag)
    }
    fun restore(token: String, id: String): ConfigSnapshot {
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "ID do JSON inválido." }
        Drive.call(token, "drive/v3/files/$id?supportsAllDrives=true&fields=id,trashed", "PATCH", JSONObject().put("trashed", false).toString().toByteArray())
        return read(token, id)
    }
    @Synchronized
    fun save(token: String, id: String, original: ConfigSnapshot, config: RemoteConfig): ConfigSnapshot {
        val latest = read(token, id)
        check(latest.config == original.config && (original.etag == null || latest.etag == original.etag)) {
            "O JSON mudou em outro celular. Toque em Recarregar JSON antes de salvar."
        }
        // Preserve extra fields added by hand to the document.
        val document = Drive.call(token, "drive/v3/files/$id?alt=media")
        config.json().keys().forEach { key -> document.put(key, config.json().get(key)) }
        Drive.call(token, "upload/drive/v3/files/$id?uploadType=media", "PATCH", document.toString(2).toByteArray(), etag = latest.etag)
        return read(token, id)
    }
    fun creationMetadata(parentId: String, name: String): JSONObject {
        require(parentId.matches(Regex("[A-Za-z0-9_-]+"))) { "Pasta do JSON inválida." }
        require(name.isNotBlank() && name.endsWith(".json", ignoreCase = true) && '/' !in name && '\\' !in name) { "Informe um nome de arquivo terminado em .json." }
        return JSONObject().put("name", name).put("mimeType", "application/json")
            .put("parents", org.json.JSONArray().put(parentId))
    }
    fun create(token: String, config: RemoteConfig, parentId: String, name: String): String {
        val boundary = "config_${java.util.UUID.randomUUID()}"
        val metadata = creationMetadata(parentId, name)
        val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: application/json\r\n\r\n${config.json().toString(2)}\r\n--$boundary--\r\n"
        return Drive.call(token, "upload/drive/v3/files?uploadType=multipart&fields=id", "POST", body.toByteArray(), "multipart/related; boundary=$boundary").getString("id")
    }
}
