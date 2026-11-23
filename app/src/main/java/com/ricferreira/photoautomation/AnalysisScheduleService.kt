package com.ricferreira.photoautomation

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.*

class AnalysisScheduleService : Service() {
    companion object {
        @Volatile var running = false
            private set
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private fun update(message: String) {
        prefs.edit().putString("analysisScheduleStatus", message).putLong("analysisScheduleHeartbeatAt", System.currentTimeMillis()).apply()
        val open = PendingIntent.getActivity(this, 99, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        getSystemService(NotificationManager::class.java).notify(2, Notification.Builder(this, "analysis_schedule")
            .setSmallIcon(android.R.drawable.ic_popup_sync).setContentTitle("Análise automática ativa")
            .setContentText(message).setContentIntent(open).setOngoing(true).build())
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!DeviceMode.current(this).canEdit) { stopSelf(); return START_NOT_STICKY }
        if (job != null) return START_NOT_STICKY
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("analysis_schedule", "Rotina de análise", NotificationManager.IMPORTANCE_LOW))
        try {
            startForeground(2, Notification.Builder(this, "analysis_schedule").setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("Análise automática").setContentText("Preparando rotina…").setOngoing(true).build())
        } catch (e: Exception) {
            prefs.edit().putString("analysisScheduleStatus", "O Android não permitiu iniciar a rotina: ${e.message}").apply()
            stopSelf(); return START_NOT_STICKY
        }
        running = true
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:analysis-schedule").apply { acquire(6 * 60 * 60 * 1000L) }
        job = scope.launch {
            while (isActive && DeviceMode.current(this@AnalysisScheduleService).canEdit) {
                val tick = SystemClock.elapsedRealtime()
                var interval = 3.0
                var retryDelay = 0L
                try {
                    val id = ConfigStore.selected(this@AnalysisScheduleService) ?: break
                    val config = withContext(Dispatchers.IO) { ConfigStore.read(Drive.token(this@AnalysisScheduleService), id).config }
                    prefs.edit().putLong("analysisConfigReadAt", System.currentTimeMillis()).putString("analysisCheckedFolder", config.folderId).apply()
                    if (!config.analysisAutoEnabled) break
                    interval = config.intervalSeconds
                    if (!AnalysisKey.configured(this@AnalysisScheduleService)) { update("Configure a chave OpenAI neste servidor."); return@launch stopSelf() }
                    if (AnalysisExecution.mutex.tryLock()) {
                        try {
                            update("${if (config.analysisLatestOnly) "Verificando última foto" else "Analisando toda a pasta"} • intervalo ${interval}s")
                            withContext(Dispatchers.IO) {
                                val singleKey = AnalysisExecution.singleShotKey(id, config)
                                if (config.captureSingleShot && !config.analysisLatestOnly && (!SingleShot.receiptReady(config) || prefs.getString("analysisLastSingleShotKey", null) == singleKey)) {
                                    withContext(Dispatchers.Main) {
                                        update(if (SingleShot.receiptReady(config)) "Foto única já analisada. Aguardando próximo comando de captura." else "Aguardando envio da foto única ao Drive.")
                                    }
                                    return@withContext
                                }
                                val token = Drive.token(this@AnalysisScheduleService)
                                val result = if (config.analysisLatestOnly) {
                                    AnalysisClient.analyzeLatest(applicationContext, token, config,
                                        prefs.getString("analysisLastAutomaticKey", null), { message -> scope.launch { update(message) } })
                                } else {
                                    AnalysisClient.analyze(applicationContext, token, config) { message -> scope.launch { update(message) } }
                                }
                                if (result != null) {
                                    AnalysisExecution.saveResult(applicationContext, result)
                                    if (result.has("latestKey")) prefs.edit().putString("analysisLastAutomaticKey", result.getString("latestKey")).apply()
                                    if (config.captureSingleShot) prefs.edit().putString("analysisLastSingleShotKey", singleKey).apply()
                                }
                                withContext(Dispatchers.Main) {
                                    update(if (result == null) "${prefs.getString("analysisSkipReason", "Aguardando foto nova")} • intervalo ${interval}s" else "Resposta recebida • aguardando próximo ciclo (${interval}s)")
                                }
                            }
                        } finally { AnalysisExecution.mutex.unlock() }
                    } else update("Análise em andamento. Este ciclo será ignorado.")
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    prefs.edit().putString("analysisScheduleError", e.message).putLong("analysisScheduleErrorAt", System.currentTimeMillis()).apply()
                    update("Erro: ${e.message}. Nova tentativa em pelo menos 30 s.")
                    interval = maxOf(interval, 30.0)
                    retryDelay = (interval * 1000).toLong()
                }
                delay(if (retryDelay > 0) retryDelay else AnalysisExecution.delayUntilNextTick(tick, SystemClock.elapsedRealtime(), interval))
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        prefs.edit().putString("analysisScheduleStatus", "Rotina interrompida pelo limite do Android. Abra o servidor para verificar/reiniciar.").apply()
        stopSelf()
    }
    override fun onDestroy() {
        running = false
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
