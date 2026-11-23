package com.ricferreira.photoautomation

import android.app.*
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class CaptureService : LifecycleService() {
    companion object {
        @Volatile var running = false
            private set
        @Volatile var capturing = false
            private set
    }
    private var starting = false
    private var destroyed = false
    private var provider: ProcessCameraProvider? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var latest: RemoteConfig? = null
    private var confirmedAt = 0L
    private var polling: Job? = null
    private var shooting: Job? = null
    private val output = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private fun notification(text: String): Notification {
        val stop = PendingIntent.getService(this, 2, Intent(this, CaptureService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, "capture").setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("PhotoAutomation • receptor ativo").setContentText(text).setOngoing(true)
            .setContentIntent(open)
        if (DeviceMode.current(this) != DeviceMode.CLIENT) builder.addAction(Notification.Action.Builder(null, "Desarmar", stop).build())
        return builder.build()
    }
    override fun onCreate() {
        super.onCreate()
        running = true
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("capture", "Captura de fotos", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (!DeviceMode.current(this).canCapture) { stopSelf(); return START_NOT_STICKY }
        if (starting) return START_NOT_STICKY
        val configId = ConfigStore.selected(this) ?: run { stopSelf(); return START_NOT_STICKY }
        starting = true
        startForeground(1, notification("Lendo configuração do Drive…"))
        prefs.edit().remove("lastError").putString("receiverStatus", "Preparando câmera e lendo JSON…").apply()
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:capture").apply { acquire(24 * 60 * 60 * 1000L) }
        polling = lifecycleScope.launch {
            while (isActive) {
                try {
                    latest = withContext(Dispatchers.IO) {
                        val token = Drive.token(this@CaptureService)
                        val read = ConfigStore.read(token, configId).config
                        val config = SingleShot.recoverUpload(token, configId, read, prefs.getString("singleShotCompletedKey", null),
                            prefs.getString("singleShotCaptureId", null) ?: prefs.getString("lastPhotoId", null))
                        if (!config.enabled && config.captureRequestId.isBlank()) prefs.edit().remove("singleShotCompletedKey").apply()
                        if (config.enabled && SingleShot.completed(configId, config, prefs.getString("singleShotCompletedKey", null)))
                            SingleShot.pause(token, configId, config.captureRequestId) else config
                    }
                    confirmedAt = SystemClock.elapsedRealtime()
                    prefs.edit().remove("remoteError").apply()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { prefs.edit().putString("remoteError", "Falha ao ler JSON: ${e.message}").apply() }
                delay(3000)
            }
        }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (destroyed) return@addListener
            try {
                provider = future.get()
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                // Keep the user-armed service/camera ready while enabled=false for remote resumption.
                val camera = provider!!.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, capture)
                shooting = lifecycleScope.launch {
                    var sessionId: String? = null
                    var appliedZoom: Float? = null
                    var nextCapture = 0L
                    var lastInterval = 0.0
                    var previousMessage = ""
                    try {
                        while (isActive) {
                            val config = latest
                            val fresh = config != null && SystemClock.elapsedRealtime() - confirmedAt <= 15000
                            val singleDone = config != null && SingleShot.completed(configId, config, prefs.getString("singleShotCompletedKey", null))
                            val enabled = CapturePolicy.enabled(config, confirmedAt, SystemClock.elapsedRealtime()) && !singleDone
                            val message: String
                            if (!enabled) {
                                capturing = false
                                sessionId = null
                                nextCapture = 0
                                message = if (!fresh) "Captura pausada: aguardando JSON válido e conexão." else if (singleDone) "Foto única capturada. Aguardando novo comando do servidor." else "Aguardando comando: enabled=false."
                            } else {
                                val settings = requireNotNull(config)
                                if (sessionId == null) sessionId = SessionStats.begin(this@CaptureService)
                                val zoomState = camera.cameraInfo.zoomState.value
                                val zoom = settings.zoom.coerceIn(zoomState?.minZoomRatio ?: 1f, zoomState?.maxZoomRatio ?: 1f)
                                if (appliedZoom != zoom) {
                                    val zoomFuture = camera.cameraControl.setZoomRatio(zoom)
                                    val zoomApplied = withTimeoutOrNull(5000) {
                                        suspendCancellableCoroutine<Unit> { continuation ->
                                            zoomFuture.addListener({
                                                if (continuation.isActive) continuation.resumeWith(runCatching { zoomFuture.get(); Unit })
                                            }, ContextCompat.getMainExecutor(this@CaptureService))
                                        }
                                        true
                                    }
                                    check(zoomApplied == true) { "A câmera não confirmou o zoom. Desarme e arme o receptor novamente." }
                                    appliedZoom = zoom
                                }
                                if (lastInterval != settings.intervalSeconds) { nextCapture = 0; lastInterval = settings.intervalSeconds }
                                message = "Capturando • ${settings.intervalSeconds}s • zoom ${zoom}x"
                                // A disable/change can arrive while awaiting the asynchronous zoom operation.
                                if (latest != settings || !CapturePolicy.enabled(latest, confirmedAt, SystemClock.elapsedRealtime())) continue
                                if (SystemClock.elapsedRealtime() >= nextCapture) {
                                    val queue = File(filesDir, "queue").apply { mkdirs() }
                                    val bytes = withContext(Dispatchers.IO) { queue.listFiles()?.filter { it.extension == "jpg" }?.sumOf { it.length() } ?: 0L }
                                    check(bytes < 500L * 1024 * 1024) { "Fila local atingiu 500 MB. Conecte à internet antes de continuar." }
                                    val started = SystemClock.elapsedRealtime()
                                    captureOne(capture, queue, settings, configId, requireNotNull(sessionId))
                                    capturing = !settings.captureSingleShot
                                    nextCapture = started + (settings.intervalSeconds * 1000).toLong()
                                }
                            }
                            if (message != previousMessage) {
                                prefs.edit().putString("receiverStatus", message).apply()
                                getSystemService(NotificationManager::class.java).notify(1, notification(message))
                                previousMessage = message
                            }
                            delay(50)
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { fail(e) }
                }
            } catch (e: Exception) { fail(e) }
        }, ContextCompat.getMainExecutor(this))
        return START_NOT_STICKY
    }
    private suspend fun captureOne(capture: ImageCapture, queue: File, config: RemoteConfig, configId: String, sessionId: String) = suspendCancellableCoroutine<Unit> { continuation ->
        val id = UUID.randomUUID().toString()
        val file = File(queue, "$id.jpg")
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), output, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                try {
                    runCatching { LastPhoto.save(this@CaptureService, file) }
                        .onSuccess { prefs.edit().putString("lastPhotoId", id).apply() }
                    val temporary = File(queue, "$id.tmp")
                    temporary.writeText(JSONObject().put("session", sessionId).put("folder", config.folderId)
                        .put("singleShot", config.captureSingleShot).put("configId", configId).put("captureRequestId", config.captureRequestId)
                        .put("name", "Photo_${System.currentTimeMillis()}_$id.jpg").toString())
                    check(temporary.renameTo(File(queue, "$id.json"))) { "Falha ao salvar foto na fila." }
                    if (config.captureSingleShot) check(prefs.edit().putString("singleShotCompletedKey", SingleShot.key(configId, config)).putString("singleShotCaptureId", id).commit()) { "Falha ao registrar captura única." }
                    UploadWorker.enqueue(this@CaptureService)
                    if (continuation.isActive) continuation.resumeWith(Result.success(Unit))
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWith(Result.failure(e)) }
            }
            override fun onError(exception: ImageCaptureException) {
                file.delete()
                if (continuation.isActive) continuation.resumeWith(Result.failure(exception))
            }
        })
    }
    private fun fail(e: Exception) {
        prefs.edit().putString("lastError", e.message ?: "Erro de câmera").apply()
        stopSelf()
    }
    override fun onDestroy() {
        running = false
        capturing = false
        destroyed = true
        polling?.cancel(); shooting?.cancel()
        provider?.unbindAll()
        output.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }
        prefs.edit().putString("receiverStatus", "Receptor desarmado neste celular.").apply()
        super.onDestroy()
    }
}
