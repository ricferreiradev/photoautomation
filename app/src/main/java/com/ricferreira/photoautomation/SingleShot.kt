package com.ricferreira.photoautomation

object SingleShot {
    fun key(configId: String, config: RemoteConfig) = "$configId:${config.captureRequestId}"
    fun completed(configId: String, config: RemoteConfig, completedKey: String?): Boolean =
        config.captureSingleShot && key(configId, config) == completedKey
    fun receiptReady(config: RemoteConfig): Boolean = config.captureSingleShot &&
        config.singleShotUploadedFileId.isNotBlank() && config.singleShotUploadedRequestId == config.captureRequestId

    @Synchronized
    fun pause(token: String, configId: String, requestId: String): RemoteConfig {
        val snapshot = ConfigStore.read(token, configId)
        val config = snapshot.config
        return if (config.captureSingleShot && config.captureRequestId == requestId && config.enabled)
            ConfigStore.save(token, configId, snapshot, config.copy(enabled = false)).config else config
    }
    @Synchronized
    fun recordUpload(token: String, configId: String, requestId: String, fileId: String, destinationFolder: String) {
        val snapshot = ConfigStore.read(token, configId)
        if (snapshot.config.captureRequestId != requestId || !snapshot.config.captureSingleShot || snapshot.config.folderId != destinationFolder) return
        if (snapshot.config.singleShotUploadedFileId == fileId && snapshot.config.singleShotUploadedRequestId == requestId) return
        ConfigStore.save(token, configId, snapshot, snapshot.config.copy(singleShotUploadedFileId = fileId, singleShotUploadedRequestId = requestId))
    }

    fun recoverUpload(token: String, configId: String, config: RemoteConfig, completedKey: String?, captureId: String?): RemoteConfig {
        if (!completed(configId, config, completedKey) || receiptReady(config) || captureId.isNullOrBlank()) return config
        require(captureId.matches(Regex("[A-Za-z0-9_-]+"))) { "Identificador da captura inválido." }
        val query = java.net.URLEncoder.encode("'${config.folderId}' in parents and trashed = false and appProperties has { key='captureId' and value='$captureId' }", "UTF-8")
        val files = Drive.call(token, "drive/v3/files?q=$query&fields=files(id)&supportsAllDrives=true&includeItemsFromAllDrives=true").getJSONArray("files")
        if (files.length() == 0) return config
        recordUpload(token, configId, config.captureRequestId, files.getJSONObject(0).getString("id"), config.folderId)
        return ConfigStore.read(token, configId).config
    }
}
