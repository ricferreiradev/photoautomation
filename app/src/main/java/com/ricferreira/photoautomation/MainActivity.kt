package com.ricferreira.photoautomation

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.text.*
import android.widget.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.Gravity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.File
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private lateinit var status: TextView
    private lateinit var remoteStatus: TextView
    private lateinit var receiverStatus: TextView
    private lateinit var folder: TextView
    private lateinit var interval: EditText
    private lateinit var zoom: EditText
    private lateinit var singleShot: CheckBox
    private lateinit var configFile: TextView
    private var remoteEnabled = false
    private lateinit var configPage: ScrollView
    private lateinit var monitorPage: ScrollView
    private lateinit var analysisPage: ScrollView
    private lateinit var analysisTab: Button
    private lateinit var prompt: EditText
    private lateinit var analysisFolder: TextView
    private lateinit var analysisResult: TextView
    private lateinit var analysisSummary: TextView
    private var promptDirty = false
    private var analysisModel = RemoteConfig.DEFAULT_ANALYSIS_MODEL
    private var analysisEffort = RemoteConfig.DEFAULT_ANALYSIS_EFFORT
    private lateinit var analysisModelButton: Button
    private lateinit var analysisEffortButton: Button
    private lateinit var latestOnly: CheckBox
    private lateinit var automaticAnalysis: CheckBox
    private lateinit var scheduleStatus: TextView
    private var analysisOptionsDirty = false
    private var remoteAutomaticEnabled = false
    private var schedulerAttemptKey: String? = null
    private var schedulerAttemptAt = 0L
    private var schedulerPermissionDenied = false
    private var currentPage = 0
    private var showingNotificationResult = false
    private lateinit var configTab: Button
    private lateinit var monitorTab: Button
    private lateinit var playButton: ImageButton
    private lateinit var hiddenCaptureButton: Button
    private lateinit var watchTestButton: Button
    private lateinit var watchControl: CheckBox
    private var lastWatchPressAt = -2000L
    private lateinit var hiddenOverlay: FrameLayout
    private lateinit var hiddenPlayButton: ImageButton
    private lateinit var mainContent: LinearLayout
    private lateinit var hiddenBack: OnBackPressedCallback
    private lateinit var actionHint: TextView
    private lateinit var monitorFile: TextView
    private var actionInProgress = false
    private var clientStartAttempted = false
    private lateinit var cameraPermissionButton: Button
    private lateinit var modeDescription: TextView
    private lateinit var editorSection: LinearLayout
    private lateinit var captureSection: LinearLayout
    private lateinit var createConfigButton: Button
    private lateinit var restoreConfigButton: Button
    private var mode = DeviceMode.BOTH
    private lateinit var counters: TextView
    private lateinit var lastPhoto: ImageView
    private lateinit var photoLabel: TextView
    private lateinit var remotePhotoSection: LinearLayout
    private lateinit var remotePhotoLabel: TextView
    private lateinit var remotePhotoImage: ImageView
    private var remotePreviewFolder: String? = null
    private var remotePreviewVersion = 0
    private var singlePreviewJob: Job? = null
    private var singlePreviewWanted: String? = null
    private var singlePreviewShown: String? = null
    private var singlePreviewRetryAt = 0L
    private var displayedPhotoId: String? = null
    private var refresh: Job? = null
    private var token: String? = null
    private var afterAuth: (() -> Unit)? = null
    private var snapshot: ConfigSnapshot? = null
    private var folderId = "root"
    private var folderName = "Meu Drive"
    private var folderNameJob: Job? = null
    private val folderNameCheckedAt = mutableMapOf<String, Long>()
    private var displayedFolderId: String? = null
    private var dirty = false
    private var rendering = false
    private var busy = false
    private val schedulePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        schedulerPermissionDenied = !granted
        if (granted) startAutomaticService()
        else status.text = "Autorize as notificações para iniciar a análise automática."
    }
    private val analysisPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) status.text = "Notificações não autorizadas. A resposta continuará disponível na tela Análise."
        connect { testAnalysis() }
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        armReceiver(); actionInProgress = false; updatePlayback()
    }
    private val authorization = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        runCatching { Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(result.data) }
            .onSuccess { token = it.accessToken; afterAuth?.invoke(); afterAuth = null }
            .onFailure { status.text = "Conexão cancelada: ${it.message}"; afterAuth = null; actionInProgress = false; updatePlayback() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalysisNotificationWorker.enqueue(this)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(12, 18, 28)) }
        mainContent = root
        val screen = FrameLayout(this)
        screen.addView(root, FrameLayout.LayoutParams(-1, -1))
        setContentView(screen)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val pages = FrameLayout(this)
        root.addView(pages, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(24)) }
        val monitorLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(24)) }
        val analysisLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(24)) }
        configPage = ScrollView(this).apply { addView(layout); pages.addView(this) }
        monitorPage = ScrollView(this).apply { addView(monitorLayout); pages.addView(this) }
        analysisPage = ScrollView(this).apply { addView(analysisLayout); pages.addView(this) }
        fun label(text: String, parent: LinearLayout = layout) = TextView(this).apply { this.text = text; textSize = 18f; parent.addView(this) }
        fun button(text: String, parent: LinearLayout = layout, action: () -> Unit): Button = Button(this).apply {
            this.text = text; parent.addView(this); setOnClickListener { if (!busy) action() }
        }
        fun number(initial: String) = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(initial); editorSection.addView(this)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (!rendering) dirty = true }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        label("Configuração").textSize = 28f
        label("Monitoramento", monitorLayout).textSize = 28f
        monitorFile = label("Nenhum JSON selecionado", monitorLayout)
        hiddenCaptureButton = button("Captura oculta", monitorLayout) { setHiddenCapture(true) }
        watchTestButton = button("Testar disparador do relógio", monitorLayout) { testWatchShutter() }
        val testedWatch = runCatching { JSONObject(File(filesDir, "watch-shutter-diagnostic.json").readText()) }.getOrNull()
        if (!prefs.contains("watchShutterDevice") && testedWatch?.optString("deviceName")?.startsWith("Redmi Watch 5 Lite") == true && testedWatch.optInt("keyCode") == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            prefs.edit().putString("watchShutterDevice", testedWatch.getString("deviceName")).putBoolean("watchShutterEnabled", true).apply()
        }
        watchControl = CheckBox(this).apply {
            text = "Controlar Play/Stop pelo relógio"
            isChecked = prefs.getBoolean("watchShutterEnabled", false)
            monitorLayout.addView(this)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("watchShutterEnabled", checked).apply() }
        }
        label("Função deste celular")
        mode = DeviceMode.current(this)
        val modePicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, DeviceMode.entries.map { it.title })
            setSelection(mode.ordinal); layout.addView(this)
        }
        modeDescription = label(mode.description)
        cameraPermissionButton = button("Autorizar câmera do cliente") { clientStartAttempted = false; ensureClientReady() }
        label("Arquivo de configuração no Drive").apply { setPadding(0, 24, 0, 8) }
        configFile = label(prefs.getString("configLocation", null) ?: ConfigStore.selected(this)?.let { "JSON conectado • $it" } ?: "Nenhum JSON selecionado")
        button("Selecionar JSON em uma pasta") { connect { browseDrive(BrowsePurpose.OPEN_CONFIG) } }
        createConfigButton = button("Criar JSON em uma pasta") { connect { browseDrive(BrowsePurpose.CREATE_CONFIG) } }
        restoreConfigButton = button("Restaurar JSON da lixeira") {
            connect { network {
                val id = requireNotNull(ConfigStore.selected(this@MainActivity))
                val restored = withContext(Dispatchers.IO) { ConfigStore.restore(requireNotNull(token), id) }
                render(restored, true)
                status.text = "JSON restaurado na pasta original. O ID foi preservado."
            } }
        }.apply { visibility = if (prefs.getBoolean("configInTrash", false)) View.VISIBLE else View.GONE }
        button("Usar link ou ID de um JSON") {
            val input = EditText(this).apply { hint = "Cole o link ou ID do JSON"; setSingleLine() }
            AlertDialog.Builder(this).setTitle("Abrir JSON compartilhado").setView(input)
                .setPositiveButton("Abrir") { _, _ -> connect { network { selectConfig(extractId(input.text.toString())) } } }
                .setNegativeButton("Cancelar", null).show()
        }
        button("Copiar link do JSON para outro celular") {
            ConfigStore.selected(this)?.let {
                getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("JSON PhotoAutomation", "https://drive.google.com/file/d/$it/view"))
                status.text = "Link copiado. No outro celular, selecione este JSON ou use o link."
            }
        }
        remoteStatus = label("Selecione ou crie um JSON para começar.", monitorLayout)
        editorSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layout.addView(this) }
        label("Configuração compartilhada", editorSection).apply { setPadding(0, 24, 0, 8) }
        folderId = prefs.getString("folder", "root") ?: "root"
        folderName = prefs.getString("folderName", "Meu Drive") ?: "Meu Drive"
        folder = label("Pasta das fotos: $folderName", editorSection)
        button("Selecionar pasta das fotos", editorSection) { connect { browseDrive(BrowsePurpose.PHOTO_FOLDER) } }
        button("Apagar arquivos da pasta de fotos", editorSection) { connect { reviewFolderCleanup() } }
        label("Intervalo em segundos (0,25 a 86400; ex.: 0,5)", editorSection)
        interval = number("5")
        label("Zoom (1 = normal; ex.: 2 = 2x)", editorSection)
        zoom = number("1")
        singleShot = CheckBox(this).apply {
            text = "Capturar somente uma imagem e parar"
            editorSection.addView(this)
            setOnCheckedChangeListener { _, _ -> if (!rendering) dirty = true }
        }
        button("Salvar configuração no Drive", editorSection) { connect { saveConfig() } }
        button("Descartar alterações e recarregar", editorSection) { connect { network {
            val id = requireNotNull(ConfigStore.selected(this@MainActivity)) { "Selecione um JSON." }
            val loaded = withContext(Dispatchers.IO) { ConfigStore.read(requireNotNull(token), id) }
            render(loaded, true)
            status.text = "Parâmetros recarregados do Drive."
        } } }
        captureSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; monitorLayout.addView(this) }
        label("Captura neste celular", captureSection).apply { setPadding(0, 24, 0, 8) }
        receiverStatus = label("Cliente parado neste celular.", captureSection)
        button("Reenviar fotos pendentes", captureSection) { UploadWorker.enqueue(this); status.text = "Fila de envio agendada." }
        counters = label("Fotos enviadas nesta sessão: 0", captureSection)
        status = TextView(this).apply { text = "Selecione a função do celular e um arquivo de configuração."; setPadding(dp(16), dp(4), dp(16), dp(4)); maxLines = 3; root.addView(this) }
        photoLabel = label("Última foto • aguardando uma captura", captureSection)
        lastPhoto = ImageView(this).apply {
            contentDescription = "Amostra da última foto capturada"; scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true; visibility = android.view.View.GONE
            captureSection.addView(this, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (260 * resources.displayMetrics.density).toInt()))
        }
        remotePhotoSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; monitorLayout.addView(this) }
        label("Última foto no Google Drive", remotePhotoSection).apply { setPadding(0, dp(24), 0, dp(8)) }
        label("No modo de foto única, a imagem aparece automaticamente após o envio. Nas outras capturas, baixe pelo botão. Usa prévia reduzida quando disponível.", remotePhotoSection)
        button("Ver última foto do Drive", remotePhotoSection) { connect { fetchRemotePhoto() } }
        remotePhotoLabel = label("Nenhuma foto baixada. Toque no botão para consultar a pasta do JSON.", remotePhotoSection)
        remotePhotoImage = ImageView(this).apply {
            contentDescription = "Última foto baixada do Google Drive"
            scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = true; visibility = View.GONE
            remotePhotoSection.addView(this, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(300)))
        }
        label("Análise", analysisLayout).textSize = 28f
        analysisFolder = label("Usa a pasta das fotos do JSON selecionado.", analysisLayout)
        analysisModelButton = button("Modelo: $analysisModel", analysisLayout) {
            val models = RemoteConfig.ANALYSIS_MODELS
            AlertDialog.Builder(this).setTitle("Modelo da análise")
                .setSingleChoiceItems(models.toTypedArray(), models.indexOf(analysisModel)) { dialog, index ->
                    analysisOptionsDirty = true
                    analysisModel = models[index]
                    analysisModelButton.text = "Modelo: $analysisModel"
                    dialog.dismiss()
                }.show()
        }
        analysisEffortButton = button("Esforço de raciocínio: $analysisEffort", analysisLayout) {
            val efforts = RemoteConfig.ANALYSIS_EFFORTS
            AlertDialog.Builder(this).setTitle("Esforço de raciocínio")
                .setSingleChoiceItems(efforts.toTypedArray(), efforts.indexOf(analysisEffort)) { dialog, index ->
                    analysisOptionsDirty = true
                    analysisEffort = efforts[index]
                    analysisEffortButton.text = "Esforço de raciocínio: $analysisEffort"
                    dialog.dismiss()
                }.show()
        }
        label("Prompt para as imagens", analysisLayout)
        latestOnly = CheckBox(this).apply {
            text = "Analisar somente a última foto enviada"
            analysisLayout.addView(this)
            setOnCheckedChangeListener { _, _ -> if (!rendering) analysisOptionsDirty = true }
        }
        automaticAnalysis = CheckBox(this).apply {
            text = "Análise automática no mesmo intervalo da captura"
            analysisLayout.addView(this)
            setOnCheckedChangeListener { _, _ -> if (!rendering) analysisOptionsDirty = true }
        }
        label("A rotina respeita a opção acima: última foto quando marcada; toda a pasta quando desmarcada. Salve para aplicar.", analysisLayout)
        scheduleStatus = label("Rotina automática desligada.", analysisLayout)
        button("Verificar / reiniciar rotina automática", analysisLayout) {
            schedulerAttemptKey = null; schedulerPermissionDenied = false
            connect { network {
                val id = requireNotNull(ConfigStore.selected(this)) { "Selecione um JSON." }
                val loaded = withContext(Dispatchers.IO) { ConfigStore.read(requireNotNull(token), id) }
                render(loaded)
                status.text = if (loaded.config.analysisAutoEnabled) "Verificação da rotina solicitada." else "A análise automática está desligada no JSON."
            } }
        }
        prompt = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 6; maxLines = 12; gravity = Gravity.TOP
            setText(RemoteConfig.DEFAULT_ANALYSIS_PROMPT)
            analysisLayout.addView(this)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (!rendering) promptDirty = true }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        button("Salvar prompt e opções no JSON", analysisLayout) { connect { network { savePrompt() } } }
        button("Restaurar prompt padrão", analysisLayout) { prompt.setText(RemoteConfig.DEFAULT_ANALYSIS_PROMPT) }
        button("Editar prompts da pasta inteira", analysisLayout) { connect { network { editBatchPrompts() } } }
        button("Testar análise das fotos", analysisLayout) { startAnalysis() }
        button("Configurar chave OpenAI neste celular", analysisLayout) { configureAnalysis() }
        label("Resposta", analysisLayout)
        button("Copiar resposta", analysisLayout) {
            getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Resposta da análise", analysisResult.text))
            status.text = "Resposta copiada."
        }
        analysisSummary = label(prefs.getString("analysisSummary", "")!!, analysisLayout)
        analysisResult = label(prefs.getString("analysisResult", "A resposta aparecerá aqui. O teste analisa as imagens já enviadas para a pasta, sem incluir fotos pendentes no celular.")!!, analysisLayout).apply { setTextIsSelectable(true) }
        AppAppearance.page(layout, "Conecte seu Drive e defina como este celular participa.", setOf("Função deste celular", "Arquivo de configuração no Drive"))
        AppAppearance.page(monitorLayout, "Acompanhe capturas, envios e a última imagem recebida.", emptySet())
        AppAppearance.page(analysisLayout, "Transforme as imagens do Drive em respostas.", setOf("Prompt para as imagens", "Resposta"))
        analysisResult.apply { textSize = 17f; setTextColor(Color.rgb(232, 238, 247)); setPadding(0, dp(16), 0, dp(12)) }
        status.apply { textSize = 12f; setTextColor(Color.rgb(165, 179, 198)); setPadding(dp(20), dp(8), dp(20), dp(4)) }
        actionHint = TextView(this).apply { gravity = Gravity.CENTER; textSize = 13f; setPadding(dp(12), dp(4), dp(12), dp(4)); root.addView(this) }
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)); setBackgroundColor(Color.rgb(23, 32, 46))
            elevation = dp(6).toFloat(); root.addView(this)
        }
        configTab = Button(this).apply { text = "Configuração"; isAllCaps = false; setBackgroundColor(Color.TRANSPARENT); navigation.addView(this, LinearLayout.LayoutParams(0, dp(64), 1f)); setOnClickListener { showPage(false) } }
        playButton = ImageButton(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(35, 83, 170)) }
            navigation.addView(this, LinearLayout.LayoutParams(dp(64), dp(64)))
            setOnClickListener { togglePlayback() }
        }
        monitorTab = Button(this).apply { text = "Monitor"; isAllCaps = false; setBackgroundColor(Color.TRANSPARENT); navigation.addView(this, LinearLayout.LayoutParams(0, dp(64), 1f)); setOnClickListener { showPage(true) } }
        analysisTab = Button(this).apply { text = "Análise"; textSize = 12f; isAllCaps = false; setBackgroundColor(Color.TRANSPARENT); navigation.addView(this, LinearLayout.LayoutParams(0, dp(64), 1f)); setOnClickListener { showTab(2) } }
        listOf(configTab, monitorTab, analysisTab).forEach { tab ->
            tab.setPadding(0, dp(8), 0, dp(4)); tab.minWidth = 0; tab.minimumWidth = 0; tab.textSize = 11f
            tab.setTypeface(null, android.graphics.Typeface.BOLD)
        }
        configTab.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_settings, 0, 0)
        monitorTab.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_monitor, 0, 0)
        analysisTab.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_analysis, 0, 0)
        configTab.textSize = 12f; monitorTab.textSize = 12f
        hiddenOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            visibility = View.GONE
            screen.addView(this, FrameLayout.LayoutParams(-1, -1))
        }
        hiddenPlayButton = ImageButton(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(12, 12, 12)) }
            imageTintList = android.content.res.ColorStateList.valueOf(Color.rgb(40, 40, 40))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(48), dp(48), dp(48), dp(48))
            hiddenOverlay.addView(this, FrameLayout.LayoutParams(dp(192), dp(192), Gravity.CENTER))
            setOnClickListener { togglePlayback() }
        }
        hiddenBack = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() { setHiddenCapture(false) }
        }
        onBackPressedDispatcher.addCallback(this, hiddenBack)
        if (ConfigStore.selected(this) == prefs.getString("previewConfigId", null)) {
            prefs.getString("previewFolderId", null)?.let { prepareRemotePhotoCache(it) }
        }
        applyMode(mode)
        if (savedInstanceState?.getBoolean("hiddenCapture") == true) setHiddenCapture(true)
        showTab(savedInstanceState?.getInt("page", 0) ?: 0)
        openAnalysisNotification(intent)
        modePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { applyMode(DeviceMode.entries[position]) }
        }
    }
    private fun applyMode(selected: DeviceMode) {
        if (mode != selected) clientStartAttempted = false
        mode = selected
        prefs.edit().putString("deviceMode", selected.name).apply()
        modeDescription.text = selected.description
        editorSection.visibility = if (selected.canEdit) android.view.View.VISIBLE else android.view.View.GONE
        createConfigButton.visibility = editorSection.visibility
        captureSection.visibility = if (selected.canCapture) android.view.View.VISIBLE else android.view.View.GONE
        remotePhotoSection.visibility = if (selected.canEdit) View.VISIBLE else View.GONE
        analysisTab.visibility = if (selected.canEdit) View.VISIBLE else View.GONE
        hiddenCaptureButton.visibility = if (selected.canEdit) View.VISIBLE else View.GONE
        watchTestButton.visibility = hiddenCaptureButton.visibility
        watchControl.visibility = hiddenCaptureButton.visibility
        if (!selected.canEdit) setHiddenCapture(false)
        if (!selected.canEdit && currentPage == 2) showTab(0)
        if (!selected.canCapture) stopService(Intent(this, CaptureService::class.java))
        if (!selected.canEdit) dirty = false
        if (!selected.canEdit) { stopService(Intent(this, AnalysisScheduleService::class.java)); schedulerAttemptKey = null }
        updatePlayback()
        ensureClientReady()
    }
    private fun showPage(monitor: Boolean) {
        showTab(if (monitor) 1 else 0)
    }
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val device = prefs.getString("watchShutterDevice", null)
        if (::playButton.isInitialized && mode.canEdit && prefs.getBoolean("watchShutterEnabled", false) &&
            WatchShutter.matches(device, event.device?.name, event.keyCode)) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastWatchPressAt >= 1000) {
                    lastWatchPressAt = now
                    togglePlayback()
                }
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (mode.canEdit && prefs.getBoolean("watchShutterEnabled", false) &&
            WatchShutter.matches(prefs.getString("watchShutterDevice", null), event.device?.name, keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }
    private fun testWatchShutter() {
        if (!mode.canEdit) return
        val output = TextView(this).apply {
            text = "Com o relógio conectado e na função Câmera, toque no disparador.\n\nAguardando comando…\n\nEste teste não inicia capturas."
            textSize = 16f
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        var count = 0
        val dialog = AlertDialog.Builder(this).setTitle("Disparador do relógio").setView(output)
            .setNegativeButton("Fechar", null).create()
        dialog.setOnKeyListener { _, keyCode, event ->
            val supported = keyCode in setOf(android.view.KeyEvent.KEYCODE_VOLUME_UP, android.view.KeyEvent.KEYCODE_VOLUME_DOWN,
                android.view.KeyEvent.KEYCODE_CAMERA, android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, android.view.KeyEvent.KEYCODE_HEADSETHOOK)
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK) false
            else {
                if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    count++
                    val description = android.view.KeyEvent.keyCodeToString(keyCode)
                    val report = JSONObject().put("keyCode", keyCode).put("keyName", description)
                        .put("deviceName", event.device?.name ?: "desconhecido")
                        .put("receivedAt", System.currentTimeMillis()).put("count", count)
                    File(filesDir, "watch-shutter-diagnostic.json").writeText(report.toString(2))
                    output.text = "Comando recebido: $description\nToques recebidos: $count\n\n${if (supported) "Comando compatível para testar o controle de captura." else "Comando registrado para avaliar compatibilidade."}\n\nNenhuma captura foi iniciada."
                }
                true
            }
        }
        dialog.setOnShowListener { dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        dialog.show()
    }
    private fun setHiddenCapture(enabled: Boolean) {
        if (!::hiddenOverlay.isInitialized) return
        val active = enabled && mode.canEdit
        hiddenOverlay.visibility = if (active) View.VISIBLE else View.GONE
        hiddenOverlay.keepScreenOn = active
        mainContent.importantForAccessibility = if (active) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        hiddenBack.isEnabled = active
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (active) {
            currentFocus?.let { getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(it.windowToken, 0) }
            controller.hide(WindowInsetsCompat.Type.systemBars())
            hiddenPlayButton.requestFocus()
        } else controller.show(WindowInsetsCompat.Type.systemBars())
        updatePlayback()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openAnalysisNotification(intent)
    }
    private fun openAnalysisNotification(source: Intent) {
        val result = AnalysisNotifications.result(this, source.getStringExtra(AnalysisNotifications.RESULT_ID)) ?: return
        showingNotificationResult = true
        analysisResult.text = result.getString("answer")
        analysisSummary.text = result.getString("summary")
        showTab(2)
        source.removeExtra(AnalysisNotifications.RESULT_ID)
    }
    private fun showTab(page: Int) {
        if (page != 2) showingNotificationResult = false
        currentPage = if (page == 2 && !mode.canEdit) 0 else page
        val monitor = currentPage == 1
        // Both views stay mounted: switching tabs keeps edits, scroll positions and the preview.
        configPage.visibility = if (currentPage == 0) View.VISIBLE else View.GONE
        monitorPage.visibility = if (monitor) View.VISIBLE else View.GONE
        analysisPage.visibility = if (currentPage == 2) View.VISIBLE else View.GONE
        configTab.setTextColor(if (currentPage == 0) Color.rgb(146, 187, 255) else Color.rgb(165, 179, 198))
        monitorTab.setTextColor(if (monitor) Color.rgb(146, 187, 255) else Color.rgb(165, 179, 198))
        analysisTab.setTextColor(if (currentPage == 2) Color.rgb(146, 187, 255) else Color.rgb(165, 179, 198))
        configTab.isSelected = currentPage == 0; monitorTab.isSelected = monitor; analysisTab.isSelected = currentPage == 2
        listOf(configTab, monitorTab, analysisTab).forEach { tab ->
            tab.background = GradientDrawable().apply {
                cornerRadius = 14 * resources.displayMetrics.density
                setColor(if (tab.isSelected) Color.rgb(30, 48, 73) else Color.TRANSPARENT)
            }
            tab.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(if (tab.isSelected) Color.rgb(146, 187, 255) else Color.rgb(165, 179, 198))
        }
        currentFocus?.let { getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(it.windowToken, 0) }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", currentPage)
        outState.putBoolean("hiddenCapture", hiddenOverlay.visibility == View.VISIBLE)
        super.onSaveInstanceState(outState)
    }
    private fun updatePlayback() {
        if (!::playButton.isInitialized) return
        val stopping = when (mode) {
            DeviceMode.SERVER -> remoteEnabled
            DeviceMode.CLIENT -> CaptureService.capturing
            DeviceMode.BOTH -> if (snapshot?.config?.captureSingleShot == true) remoteEnabled else CaptureService.running
        }
        playButton.setImageResource(if (stopping) R.drawable.ic_stop else R.drawable.ic_play)
        (playButton.background as GradientDrawable).setColor(if (stopping) Color.rgb(182, 45, 54) else Color.rgb(35, 83, 170))
        playButton.contentDescription = when (mode) {
            DeviceMode.SERVER -> if (stopping) "Pausar captura dos clientes no JSON" else "Ativar captura dos clientes no JSON"
            DeviceMode.CLIENT -> when {
                CaptureService.capturing -> "Gravando • controlado pelo servidor"
                prefs.contains("lastError") -> "Erro no cliente • consulte o monitoramento"
                CaptureService.running -> "Cliente pronto • aguardando o servidor"
                else -> "Preparando cliente para receber comandos"
            }
            DeviceMode.BOTH -> if (stopping) "Parar captura e pausar JSON" else "Ativar captura e iniciar neste celular"
        }
        playButton.isEnabled = mode != DeviceMode.CLIENT && !busy && !actionInProgress && ConfigStore.selected(this) != null && snapshot != null
        playButton.alpha = if (playButton.isEnabled || mode == DeviceMode.CLIENT) 1f else 0.45f
        hiddenPlayButton.setImageResource(if (stopping) R.drawable.ic_stop else R.drawable.ic_play)
        hiddenPlayButton.contentDescription = playButton.contentDescription
        hiddenPlayButton.isEnabled = playButton.isEnabled
        hiddenPlayButton.alpha = if (playButton.isEnabled) 1f else 0.45f
        cameraPermissionButton.visibility = if (mode == DeviceMode.CLIENT && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) View.VISIBLE else View.GONE
        actionHint.text = when {
            actionInProgress || busy -> "Aplicando…"
            ConfigStore.selected(this) == null -> "Selecione um JSON na tela Configuração"
            mode != DeviceMode.CLIENT && snapshot == null -> "Aguardando leitura do JSON"
            else -> playButton.contentDescription
        }
    }
    private fun requestCamera() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        permission.launch(permissions.toTypedArray())
    }
    private fun togglePlayback() {
        if (mode == DeviceMode.CLIENT) return
        if (busy || actionInProgress || !playButton.isEnabled) return
        val start = if (mode == DeviceMode.SERVER || snapshot?.config?.captureSingleShot == true) !remoteEnabled else !CaptureService.running
        if (!start && mode == DeviceMode.BOTH) stopService(Intent(this, CaptureService::class.java))
        val requestedMode = mode
        actionInProgress = true; updatePlayback()
        connect { network {
            check(mode == requestedMode) { "A função deste celular mudou. Toque em Play/Stop novamente." }
            val id = requireNotNull(ConfigStore.selected(this@MainActivity))
            val saved = withContext(Dispatchers.IO) {
                val original = ConfigStore.read(requireNotNull(token), id)
                ConfigStore.save(requireNotNull(token), id, original, original.config.copy(enabled = start,
                    captureRequestId = if (start) java.util.UUID.randomUUID().toString() else original.config.captureRequestId))
            }
            // Play/Stop changes only enabled; unsaved interval/folder/zoom edits stay in the form.
            snapshot = saved
            render(saved)
            status.text = if (start) "Captura ativada no JSON." else "Captura pausada no JSON."
            if (start && requestedMode == DeviceMode.BOTH) requestCamera()
        } }
    }
    private fun extractId(value: String): String {
        val trimmed = value.trim()
        val id = Regex("/d/([A-Za-z0-9_-]+)").find(trimmed)?.groupValues?.get(1)
            ?: Regex("[?&]id=([A-Za-z0-9_-]+)").find(trimmed)?.groupValues?.get(1) ?: trimmed
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "Informe um ID ou link válido do JSON." }
        return id
    }
    private fun draft() = (snapshot?.config ?: RemoteConfig(false, "root", 5.0, 1f)).copy(enabled = remoteEnabled, folderId = folderId,
        intervalSeconds = interval.text.toString().replace(',', '.').toDoubleOrNull() ?: error("Informe o intervalo em segundos."),
        zoom = zoom.text.toString().replace(',', '.').toFloatOrNull() ?: error("Informe o zoom."), analysisPrompt = prompt.text.toString(),
        analysisLatestOnly = latestOnly.isChecked, analysisAutoEnabled = automaticAnalysis.isChecked, captureSingleShot = singleShot.isChecked,
        analysisModel = analysisModel, analysisEffort = analysisEffort)

    private fun render(loaded: ConfigSnapshot, force: Boolean = false) {
        restoreConfigButton.visibility = View.GONE
        prefs.edit().putBoolean("configInTrash", false).apply()
        val config = loaded.config
        remoteAutomaticEnabled = config.analysisAutoEnabled
        if (!analysisOptionsDirty || force) {
            rendering = true
            latestOnly.isChecked = config.analysisLatestOnly; automaticAnalysis.isChecked = config.analysisAutoEnabled
            analysisModel = config.analysisModel
            analysisEffort = config.analysisEffort
            analysisModelButton.text = "Modelo: $analysisModel"
            analysisEffortButton.text = "Esforço de raciocínio: $analysisEffort"
            rendering = false; analysisOptionsDirty = false
        }
        prepareRemotePhotoCache(config.folderId)
        ensureSingleShotPreview(config)
        remoteEnabled = config.enabled
        displayedFolderId = config.folderId
        val remoteFolderName = displayFolderName(config.folderId)
        analysisFolder.text = "Pasta das fotos: $remoteFolderName\nJSON: ${prefs.getString("configLocation", "")}"
        if (!promptDirty || force) {
            rendering = true; prompt.setText(config.analysisPrompt); rendering = false; promptDirty = false
        }
        remoteStatus.text = "No Drive: ${if (config.enabled) "ATIVADO" else "PAUSADO"} • ${config.intervalSeconds}s • ${config.zoom}x\nPasta: $remoteFolderName" + if (dirty && !force) "\nHá alterações locais não salvas." else ""
        if (!dirty || force) {
            rendering = true
            snapshot = loaded
            interval.setText(config.intervalSeconds.toString()); zoom.setText(config.zoom.toString())
            singleShot.isChecked = config.captureSingleShot
            if (folderId != config.folderId) { folderId = config.folderId; folderName = remoteFolderName }
            if (folderName == folderId) folderName = remoteFolderName
            folder.text = "Pasta das fotos: $folderName"
            rendering = false; dirty = false
        }
        updatePlayback()
        resolveFolderName(config.folderId)
        ensureAutomaticSchedule(config)
    }
    private fun displayFolderName(id: String): String = if (id == "root") "Meu Drive"
        else prefs.getString("driveFolderName:$id", null) ?: if (folderId == id && folderName != id) folderName else "Carregando nome da pasta…"

    private fun resolveFolderName(id: String) {
        if (id == "root" || folderNameJob?.isActive == true) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - (folderNameCheckedAt[id] ?: -300000L) < 300000) return
        folderNameCheckedAt[id] = now
        folderNameJob = lifecycleScope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    Drive.call(Drive.token(this@MainActivity), "drive/v3/files/$id?fields=name&supportsAllDrives=true").getString("name")
                }
                prefs.edit().putString("driveFolderName:$id", name).apply()
                if (displayedFolderId == id) {
                    analysisFolder.text = "Pasta das fotos: $name\nJSON: ${prefs.getString("configLocation", "")}"
                    remoteStatus.text = remoteStatus.text.toString().replace(Regex("Pasta: [^\\n]*"), "Pasta: " + java.util.regex.Matcher.quoteReplacement(name))
                }
                if (folderId == id && !dirty) {
                    folderName = name; folder.text = "Pasta das fotos: $name"
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { folderNameCheckedAt[id] = now - 270000 }
        }
    }
    private fun ensureAutomaticSchedule(config: RemoteConfig) {
        if (!config.analysisAutoEnabled || !mode.canEdit) {
            stopService(Intent(this, AnalysisScheduleService::class.java)); schedulerAttemptKey = null
            scheduleStatus.text = "Rotina automática desligada."
            return
        }
        val id = ConfigStore.selected(this) ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) || AnalysisScheduleService.running ||
            !AnalysisExecution.shouldRetryStart(schedulerAttemptKey == id, schedulerAttemptAt, now)) return
        schedulerAttemptKey = id
        schedulerAttemptAt = now
        if (!AnalysisKey.configured(this)) { status.text = "Configure a chave OpenAI para iniciar a rotina automática."; return }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (!schedulerPermissionDenied) schedulePermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        else startAutomaticService()
    }
    private fun startAutomaticService() {
        if (!remoteAutomaticEnabled || !mode.canEdit || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
        runCatching { startForegroundService(Intent(this, AnalysisScheduleService::class.java)) }
            .onFailure { status.text = "Não foi possível iniciar a rotina: ${it.message}" }
    }
    private fun prepareRemotePhotoCache(id: String) {
        if (remotePreviewFolder == id) return
        remotePreviewFolder = id
        val version = ++remotePreviewVersion
        remotePhotoImage.setImageDrawable(null); remotePhotoImage.visibility = View.GONE
        remotePhotoLabel.text = "Toque em Ver última foto do Drive para consultar esta pasta."
        lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) { RemotePhoto.cached(this@MainActivity, id) }
            if (cached != null && remotePreviewFolder == id && remotePreviewVersion == version) showRemotePhoto(cached, true)
        }
    }
    private fun ensureSingleShotPreview(config: RemoteConfig) {
        val configId = ConfigStore.selected(this)
        val key = if (mode.canEdit && configId != null && SingleShot.receiptReady(config))
            "$configId:${config.folderId}:${config.captureRequestId}:${config.singleShotUploadedFileId}" else null
        if (singlePreviewWanted != key) singlePreviewRetryAt = 0
        singlePreviewWanted = key
        if (key == null || key == singlePreviewShown || singlePreviewJob?.isActive == true ||
            android.os.SystemClock.elapsedRealtime() < singlePreviewRetryAt) return
        val version = ++remotePreviewVersion
        singlePreviewJob = lifecycleScope.launch {
            try {
                remotePhotoLabel.text = "Foto única recebida no Drive. Baixando prévia…"
                val preview = withContext(Dispatchers.IO) {
                    RemotePhoto.cached(this@MainActivity, config.folderId, config.singleShotUploadedFileId)
                        ?: RemotePhoto.fetch(this@MainActivity, Drive.token(this@MainActivity), config.folderId, config.singleShotUploadedFileId)
                }
                if (singlePreviewWanted != key || !mode.canEdit || ConfigStore.selected(this@MainActivity) != configId || remotePreviewVersion != version) return@launch
                remotePreviewFolder = config.folderId
                prefs.edit().putString("previewConfigId", configId).putString("previewFolderId", config.folderId).apply()
                showRemotePhoto(preview, preview.downloadedBytes == 0)
                remotePhotoLabel.append("\nFoto única exibida automaticamente após a confirmação do envio.")
                singlePreviewShown = key
                singlePreviewRetryAt = 0
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (singlePreviewWanted == key) remotePhotoLabel.text = "Não foi possível abrir a foto única: ${e.message}. Nova tentativa em 30 s."
                if (singlePreviewWanted == key) singlePreviewRetryAt = android.os.SystemClock.elapsedRealtime() + 30000
            }
        }
    }
    private fun showRemotePhoto(preview: RemotePhotoPreview, fromCache: Boolean) {
        remotePhotoImage.setImageBitmap(preview.bitmap)
        remotePhotoImage.visibility = View.VISIBLE
        val date = runCatching {
            java.time.Instant.parse(preview.metadata.getString("createdTime")).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
        }.getOrDefault("")
        remotePhotoLabel.text = "${preview.metadata.getString("name")}\n$date\n" +
            if (fromCache) "Última foto baixada neste celular. Toque no botão para verificar se há uma mais recente."
            else "${if (preview.metadata.optBoolean("reduced")) "Prévia reduzida" else "Foto original"} • ${((preview.downloadedBytes + 1023) / 1024)} KB baixados nesta consulta."
    }
    private fun fetchRemotePhoto() = network {
        remotePreviewVersion++
        check(mode.canEdit) { "A prévia do Drive fica disponível no servidor." }
        val id = requireNotNull(ConfigStore.selected(this)) { "Selecione um JSON na tela Configuração." }
        status.text = "Consultando a foto mais recente do Drive…"
        val preview = withContext(Dispatchers.IO) {
            val config = ConfigStore.read(requireNotNull(token), id).config
            RemotePhoto.fetch(this@MainActivity, requireNotNull(token), config.folderId)
        }
        remotePreviewFolder = preview.metadata.getString("folderId")
        prefs.edit().putString("previewConfigId", id).putString("previewFolderId", remotePreviewFolder).apply()
        showRemotePhoto(preview, preview.downloadedBytes == 0)
        status.text = if (preview.downloadedBytes == 0) "A foto mais recente já estava baixada. A imagem não foi transferida novamente." else "Última foto do Drive baixada. Capturas únicas confirmadas também atualizam a prévia automaticamente."
    }
    private suspend fun selectConfig(id: String, location: String? = null) {
        val loaded = withContext(Dispatchers.IO) { ConfigStore.read(requireNotNull(token), id) }
        if (ConfigStore.selected(this) != id) stopService(Intent(this, CaptureService::class.java))
        val description = location ?: withContext(Dispatchers.IO) {
            Drive.call(requireNotNull(token), "drive/v3/files/$id?fields=name").getString("name")
        }
        prefs.edit().putString("configId", id).putString("configLocation", description).apply()
        clientStartAttempted = false
        configFile.text = description; render(loaded, true)
        status.text = "JSON conectado. Use o botão Play no centro da barra inferior para iniciar."
    }
    private fun network(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                status.text = "Erro: ${e.message}"
                if (e is ConfigInTrashException) {
                    prefs.edit().putBoolean("configInTrash", true).apply()
                    restoreConfigButton.visibility = View.VISIBLE
                }
            }
            finally { busy = false; actionInProgress = false; updatePlayback() }
        }
    }
    private fun connect(action: () -> Unit) {
        afterAuth = action
        Identity.getAuthorizationClient(this).authorize(Drive.request()).addOnSuccessListener {
            if (it.hasResolution()) authorization.launch(IntentSenderRequest.Builder(requireNotNull(it.pendingIntent).intentSender).build())
            else { token = it.accessToken; afterAuth?.invoke(); afterAuth = null }
        }.addOnFailureListener { status.text = "Erro ao conectar Google: ${it.message}"; afterAuth = null; actionInProgress = false; updatePlayback() }
    }
    private fun saveConfig() = network {
        check(mode.canEdit) { "O modo Cliente apenas lê a configuração. Mude para Servidor para editar." }
        val id = requireNotNull(ConfigStore.selected(this)) { "Selecione ou crie um JSON." }
        val original = requireNotNull(snapshot) { "Recarregue o JSON antes de salvar." }
        val config = draft()
        val saved = withContext(Dispatchers.IO) { ConfigStore.save(requireNotNull(token), id, original, config) }
        render(saved, true)
        status.text = "Parâmetros salvos no Drive. Receptores aplicarão na próxima leitura."
    }
    private fun reviewFolderCleanup() = network {
        check(mode.canEdit) { "A limpeza da pasta é feita pelo servidor." }
        val configId = requireNotNull(ConfigStore.selected(this)) { "Selecione um JSON antes de limpar a pasta." }
        status.text = "Conferindo arquivos da pasta de fotos…"
        val plan = withContext(Dispatchers.IO) {
            val config = ConfigStore.read(requireNotNull(token), configId).config
            DriveCleanup.plan(requireNotNull(token), config.folderId, configId)
        }
        if (plan.files.isEmpty()) { status.text = "Nenhum arquivo para apagar nesta pasta. O JSON ativo e as subpastas são preservados."; return@network }
        val sample = plan.files.take(5).joinToString("\n") { "• ${it.name}" }
        AlertDialog.Builder(this).setTitle("Apagar ${plan.files.size} arquivos?")
            .setMessage("Pasta no Drive: ${plan.folderName}\n\n$sample${if (plan.files.size > 5) "\n…" else ""}\n\nOs arquivos serão movidos para a lixeira do Drive. O JSON de configuração ativo, a pasta e suas subpastas serão preservados.\n\nSe a captura continuar ativa, novas fotos poderão chegar depois desta limpeza.")
            .setPositiveButton("Mover para a lixeira") { _, _ -> connect { cleanFolder(plan, configId) } }
            .setNegativeButton("Cancelar", null).show()
        status.text = "Aguardando confirmação da limpeza de ${plan.folderName}."
    }
    private fun cleanFolder(plan: CleanupPlan, configId: String) = network {
        check(mode.canEdit && ConfigStore.selected(this) == configId) { "A configuração mudou. Confira a pasta novamente." }
        val result = withContext(Dispatchers.IO) {
            val config = ConfigStore.read(requireNotNull(token), configId).config
            val folder = Drive.call(requireNotNull(token), "drive/v3/files/${config.folderId}?fields=id&supportsAllDrives=true")
            check(folder.getString("id") == plan.folderId) { "A pasta no JSON mudou. Confira a limpeza novamente." }
            DriveCleanup.execute(requireNotNull(token), plan, configId) { done, total ->
                runOnUiThread { status.text = "Movendo arquivos para a lixeira: $done de $total…" }
            }
        }
        if (remotePreviewFolder == plan.folderId || ConfigStore.selected(this) == configId) {
            remotePreviewVersion++
            remotePhotoImage.setImageDrawable(null); remotePhotoImage.visibility = View.GONE
            remotePhotoLabel.text = "Pasta limpa. Toque em Ver última foto do Drive para consultar novamente."
            File(filesDir, "remote-photo.json").delete(); File(filesDir, "remote-photo.jpg").delete()
        }
        val message = "${result.deleted} arquivos movidos para a lixeira.\n${result.skipped} ignorados por mudança de pasta/estado.\n${result.errors.size} falhas."
        status.text = message
        AlertDialog.Builder(this).setTitle("Limpeza concluída").setMessage(message + if (result.errors.isNotEmpty()) "\n\n" + result.errors.take(3).joinToString("\n") else "")
            .setPositiveButton("OK", null).show()
    }
    private suspend fun editBatchPrompts() {
        check(mode.canEdit) { "A análise é configurada no servidor." }
        val id = requireNotNull(ConfigStore.selected(this)) { "Selecione um JSON na tela Configuração." }
        val accessToken = requireNotNull(token)
        val original = withContext(Dispatchers.IO) { ConfigStore.read(accessToken, id) }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
        }
        fun addPrompt(label: String, value: String): EditText {
            val caption = TextView(this).apply { text = label }
            fields.addView(caption)
            return EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 4
                gravity = Gravity.TOP
                setText(value)
                fields.addView(this)
                this.id = View.generateViewId()
                caption.labelFor = this.id
            }
        }
        val batch = addPrompt("Prompt dos lotes", original.config.batchPrompt)
        val consolidation = addPrompt("Prompt de consolidação", original.config.consolidationPrompt)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Prompts da pasta inteira")
            .setView(ScrollView(this).apply { addView(fields) })
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar no JSON", null)
            .show()
        val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        save.setOnClickListener {
            network {
                save.isEnabled = false
                try {
                    check(mode.canEdit && ConfigStore.selected(this) == id) { "O JSON selecionado mudou." }
                    val batchText = batch.text.toString()
                    val consolidationText = consolidation.text.toString()
                    val (current, saved) = withContext(Dispatchers.IO) {
                        val current = ConfigStore.read(accessToken, id)
                        check(current.config.batchPrompt == original.config.batchPrompt &&
                            current.config.consolidationPrompt == original.config.consolidationPrompt) {
                            "Os prompts mudaram no Drive. Feche e reabra o editor para recarregar."
                        }
                        current to ConfigStore.save(accessToken, id, current, current.config.copy(
                            batchPrompt = batchText, consolidationPrompt = consolidationText
                        ))
                    }
                    if (snapshot == current) snapshot = saved
                    render(saved)
                    dialog.dismiss()
                    status.text = "Prompts da pasta inteira salvos no JSON do Drive."
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (dialog.isShowing) Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                    throw e
                } finally {
                    save.isEnabled = true
                }
            }
        }
    }

    private suspend fun savePrompt(): RemoteConfig {
        check(mode.canEdit) { "A análise é configurada no servidor." }
        val id = requireNotNull(ConfigStore.selected(this)) { "Selecione um JSON na tela Configuração." }
        val text = prompt.text.toString()
        val onlyLatest = latestOnly.isChecked
        val autoEnabled = automaticAnalysis.isChecked
        val model = analysisModel
        val effort = analysisEffort
        val saved = withContext(Dispatchers.IO) {
            val original = ConfigStore.read(requireNotNull(token), id)
            ConfigStore.save(requireNotNull(token), id, original, original.config.copy(analysisPrompt = text,
                analysisLatestOnly = onlyLatest, analysisAutoEnabled = autoEnabled,
                analysisModel = model, analysisEffort = effort))
        }
        snapshot = saved; promptDirty = false; analysisOptionsDirty = false; render(saved)
        status.text = "Prompt e opções salvos no JSON do Drive."
        return saved.config
    }
    private fun startAnalysis() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            analysisPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else connect { testAnalysis() }
    }
    private fun testAnalysis() = network {
        showingNotificationResult = false
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try {
            analysisResult.text = "Salvando prompt e preparando a análise…"
            val config = savePrompt()
            check(AnalysisKey.configured(this@MainActivity)) { "Configure a chave OpenAI neste celular para realizar o teste." }
            analysisResult.text = "Lendo imagens da pasta e aguardando a resposta da OpenAI…"
            val result = withContext(Dispatchers.IO) {
              AnalysisExecution.mutex.withLock {
                val completed = AnalysisClient.analyze(applicationContext, requireNotNull(token), config) { message ->
                    runOnUiThread { analysisResult.text = message }
                }
                // Preserve and notify an API response even if the activity closes before returning to the UI.
                AnalysisExecution.saveResult(applicationContext, completed)
              }
            }
            val output = result.getString("answer")
            val summary = "${result.getInt("imageCount")} imagens analisadas em ${result.optInt("batchCount", 1)} lotes"
            analysisSummary.text = summary
            analysisResult.text = output
            val notified = result.getBoolean("notificationPosted")
            status.text = if (notified) "Análise concluída. Resposta enviada às notificações." else "Análise concluída. Ative as notificações do app nas configurações do Android para receber o aviso."
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            analysisResult.text = "A análise não foi concluída: ${e.message}"
            throw e
        } finally { window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    private fun configureAnalysis() {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 0, 24, 0) }
        panel.addView(TextView(this).apply { text = if (AnalysisKey.configured(this@MainActivity)) "Uma chave já está configurada neste celular. Deixe o campo vazio para mantê-la ou informe uma nova." else "A chave ficará criptografada somente neste celular. Ela não será salva no JSON nem compartilhada com o cliente." })
        val password = EditText(this).apply { hint = "Chave da API OpenAI"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setSingleLine(); panel.addView(this) }
        AlertDialog.Builder(this).setTitle("Chave OpenAI local").setView(panel)
            .setPositiveButton("Salvar") { _, _ ->
                runCatching { if (password.text.isNotBlank()) AnalysisKey.save(this, password.text.toString()) }
                    .onSuccess { status.text = "Chave mantida somente neste celular."; schedulerAttemptKey = null; snapshot?.config?.let { ensureAutomaticSchedule(it) } }
                    .onFailure { status.text = "Não foi possível salvar a chave: ${it.message}" }
            }.setNeutralButton("Remover chave") { _, _ -> AnalysisKey.remove(this); status.text = "Chave removida deste celular." }
            .setNegativeButton("Cancelar", null).show()
    }
    private enum class BrowsePurpose { OPEN_CONFIG, CREATE_CONFIG, PHOTO_FOLDER }
    private fun browseDrive(purpose: BrowsePurpose) {
        val title = when (purpose) {
            BrowsePurpose.OPEN_CONFIG -> "Selecionar JSON"
            BrowsePurpose.CREATE_CONFIG -> "Pasta para criar o JSON"
            BrowsePurpose.PHOTO_FOLDER -> "Pasta para salvar as fotos"
        }
        val action = when (purpose) {
            BrowsePurpose.CREATE_CONFIG -> "Criar JSON aqui"
            BrowsePurpose.PHOTO_FOLDER -> "Usar esta pasta"
            else -> null
        }
        DriveBrowser(this, lifecycleScope, requireNotNull(token), purpose == BrowsePurpose.OPEN_CONFIG, title, action,
            onFolder = { id, location ->
                if (purpose == BrowsePurpose.CREATE_CONFIG) nameNewConfig(id, location)
                else { folderId = id; folderName = location; folder.text = "Pasta das fotos: $location"; dirty = true }
            }, onFile = { id, location -> network { selectConfig(id, location) } }).show()
    }
    private fun nameNewConfig(parentId: String, location: String) {
        val name = EditText(this).apply { setSingleLine(); setText("photoautomation-config.json") }
        val dialog = AlertDialog.Builder(this).setTitle("Nome do novo JSON").setMessage("Salvar em: $location")
            .setView(name).setPositiveButton("Criar", null).setNegativeButton("Cancelar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val fileName = name.text.toString().trim()
                val config = runCatching {
                    check(mode.canEdit) { "Somente o servidor cria configurações." }
                    ConfigStore.creationMetadata(parentId, fileName)
                    draft().copy(enabled = false)
                }.getOrElse { name.error = it.message; return@setOnClickListener }
                dialog.dismiss()
                network {
                    val id = withContext(Dispatchers.IO) { ConfigStore.create(requireNotNull(token), config, parentId, fileName) }
                    selectConfig(id, "$location / $fileName")
                    status.text = "JSON criado na pasta escolhida, com captura pausada. Configure os parâmetros e salve."
                }
            }
        }
        dialog.show()
    }
    override fun onResume() {
        super.onResume()
        schedulerAttemptKey = null
        ensureClientReady()
        refresh = lifecycleScope.launch {
            var lastRead = 0L
            while (isActive) {
                scheduleStatus.text = if (remoteAutomaticEnabled) prefs.getString("analysisScheduleStatus", "Preparando rotina automática…") else "Rotina automática desligada."
                if (remoteAutomaticEnabled && !AnalysisScheduleService.running) scheduleStatus.append("\nServiço automático não está ativo neste celular.")
                if (remoteAutomaticEnabled) {
                    fun time(key: String): String = prefs.getLong(key, 0).takeIf { it > 0 }?.let {
                        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                    } ?: "ainda não registrado"
                    scheduleStatus.append("\nÚltima consulta: ${time("analysisLastPhotoCheckAt")}\nÚltima resposta: ${time("analysisLastSuccessAt")}\nFoto verificada: ${prefs.getString("analysisLastSeenPhoto", "nenhuma")}")
                    prefs.getString("analysisScheduleError", null)?.let { scheduleStatus.append("\nÚltimo erro (${time("analysisScheduleErrorAt")}): $it") }
                }
                if (AnalysisScheduleService.running && !busy && !showingNotificationResult) {
                    val saved = prefs.getString("analysisResult", null)
                    if (saved != null) { analysisResult.text = saved; analysisSummary.text = prefs.getString("analysisSummary", "") }
                }
                ensureClientReady()
                val pending = withContext(Dispatchers.IO) { File(filesDir, "queue").listFiles()?.count { it.extension == "json" } ?: 0 }
                counters.text = "Fotos enviadas nesta sessão: ${SessionStats.count(this@MainActivity)}\nFotos pendentes: $pending"
                monitorFile.text = "${mode.title}\n${prefs.getString("configLocation", "Nenhum JSON selecionado") }"
                updatePlayback()
                receiverStatus.text = prefs.getString("receiverStatus", "Receptor desarmado neste celular.")
                val errors = listOfNotNull(prefs.getString("lastError", null), prefs.getString("uploadError", null), prefs.getString("remoteError", null))
                if (errors.isNotEmpty()) receiverStatus.append("\n" + errors.joinToString("\n"))
                val photoId = prefs.getString("lastPhotoId", null)
                if (photoId != null && photoId != displayedPhotoId) {
                    val bitmap = withContext(Dispatchers.IO) { runCatching { LastPhoto.load(this@MainActivity) }.getOrNull() }
                    if (bitmap != null) { lastPhoto.setImageBitmap(bitmap); lastPhoto.visibility = android.view.View.VISIBLE; photoLabel.text = "Última foto capturada"; displayedPhotoId = photoId }
                }
                val id = ConfigStore.selected(this@MainActivity)
                if (id != null && !busy && android.os.SystemClock.elapsedRealtime() - lastRead > 3000) {
                    lastRead = android.os.SystemClock.elapsedRealtime()
                    try {
                        val loaded = withContext(Dispatchers.IO) { ConfigStore.read(Drive.token(this@MainActivity), id) }
                        if (id == ConfigStore.selected(this@MainActivity) && !busy) render(loaded)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        remoteStatus.text = "JSON não atualizado: ${e.message}"
                        if (e is ConfigInTrashException) {
                            prefs.edit().putBoolean("configInTrash", true).apply()
                            restoreConfigButton.visibility = View.VISIBLE
                            status.text = "JSON na lixeira. Na tela Configuração, toque em Restaurar JSON da lixeira."
                        }
                    }
                }
                delay(1000)
            }
        }
    }
    override fun onPause() { refresh?.cancel(); super.onPause() }
    private fun ensureClientReady() {
        if (mode != DeviceMode.CLIENT || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) ||
            ConfigStore.selected(this) == null || CaptureService.running || clientStartAttempted) return
        clientStartAttempted = true
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) armReceiver()
        else requestCamera()
    }
    private fun armReceiver() {
        if (!mode.canCapture) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { status.text = "Permita o acesso à câmera."; return }
        if (ConfigStore.selected(this) == null) { status.text = "Selecione ou crie um JSON primeiro."; return }
        startForegroundService(Intent(this, CaptureService::class.java))
        status.text = if (mode == DeviceMode.CLIENT) "Cliente conectado. O servidor controla o início e a pausa das fotos." else "Receptor armado. Aguardando a configuração do JSON."
    }
}
