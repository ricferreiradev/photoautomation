package com.ricferreira.photoautomation

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URLEncoder

data class DriveBrowserItem(val id: String, val name: String, val folder: Boolean, val shortcut: Boolean = false)

object DriveListing {
    private const val FOLDER = "application/vnd.google-apps.folder"
    private const val SHORTCUT = "application/vnd.google-apps.shortcut"
    fun query(parent: String, includeJson: Boolean): String {
        require(parent == "#shared" || parent.matches(Regex("[A-Za-z0-9_-]+"))) { "Pasta inválida." }
        val location = if (parent == "#shared") "sharedWithMe = true" else "'$parent' in parents"
        val types = "mimeType = '$FOLDER' or mimeType = '$SHORTCUT'" + if (includeJson) " or mimeType = 'application/json' or name contains '.json'" else ""
        return "$location and trashed = false and ($types)"
    }
    fun item(json: JSONObject, includeJson: Boolean): DriveBrowserItem? {
        val shortcut = json.getString("mimeType") == SHORTCUT
        val details = json.optJSONObject("shortcutDetails")
        val type = if (shortcut) details?.optString("targetMimeType") else json.getString("mimeType")
        val id = if (shortcut) details?.optString("targetId") else json.getString("id")
        if (id.isNullOrBlank()) return null
        val folder = type == FOLDER
        val name = json.getString("name")
        if (!folder && !(includeJson && (type == "application/json" || name.endsWith(".json", true)))) return null
        return DriveBrowserItem(id, name, folder, shortcut)
    }
}

/** One persistent dialog: entering a folder replaces its contents instead of stacking dialogs. */
class DriveBrowser(
    private val context: Context,
    private val scope: CoroutineScope,
    private val token: String,
    private val includeJson: Boolean,
    private val title: String,
    private val folderAction: String?,
    private val onFolder: (String, String) -> Unit,
    private val onFile: (String, String) -> Unit
) {
    private val path = mutableListOf("root" to "Meu Drive")
    private val breadcrumb = TextView(context)
    private val message = TextView(context)
    private val progress = ProgressBar(context)
    private val list = ListView(context)
    private lateinit var dialog: AlertDialog
    private var job: Job? = null
    private var loading = false

    fun show() {
        val density = context.resources.displayMetrics.density
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding((20 * density).toInt(), 0, (20 * density).toInt(), 0)
            addView(breadcrumb); addView(progress); addView(message)
            addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (310 * density).toInt()))
        }
        val builder = AlertDialog.Builder(context).setTitle(title).setView(panel)
            .setNegativeButton("Cancelar", null).setNeutralButton("Voltar", null)
        if (folderAction != null) builder.setPositiveButton(folderAction, null)
        dialog = builder.create()
        dialog.setOnDismissListener { job?.cancel() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (!loading && path.size > 1) { path.removeAt(path.lastIndex); load() }
            }
            if (folderAction != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!loading && path.last().first != "#shared") {
                    val current = path.last().first to location()
                    dialog.dismiss(); onFolder(current.first, current.second)
                }
            }
            load()
        }
        dialog.show()
    }
    private fun location() = path.joinToString(" / ") { it.second }
    private fun load() {
        job?.cancel()
        loading = true
        breadcrumb.text = location()
        progress.visibility = View.VISIBLE
        message.text = "Carregando subpastas…"
        list.adapter = null
        list.isEnabled = false
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = false
        if (folderAction != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        job = scope.launch {
            try {
                val parent = path.last().first
                val entries = withContext(Dispatchers.IO) {
                    val result = mutableListOf<DriveBrowserItem>()
                    var page = ""
                    do {
                        val query = URLEncoder.encode(DriveListing.query(parent, includeJson), "UTF-8")
                        val json = Drive.call(token, "drive/v3/files?q=$query&fields=nextPageToken,files(id,name,mimeType,shortcutDetails(targetId,targetMimeType))&pageSize=1000&orderBy=name&supportsAllDrives=true&includeItemsFromAllDrives=true&pageToken=$page")
                        val files = json.getJSONArray("files")
                        for (i in 0 until files.length()) DriveListing.item(files.getJSONObject(i), includeJson)?.let { result.add(it) }
                        page = json.optString("nextPageToken")
                    } while (page.isNotEmpty())
                    result.sortedWith(compareByDescending<DriveBrowserItem> { it.folder }.thenBy { it.name.lowercase() }).toMutableList()
                }
                if (path.size == 1) entries.add(0, DriveBrowserItem("#shared", "Compartilhados comigo", true))
                list.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1,
                    entries.map { "${if (it.folder) "📁" else "📄"} ${it.name}${if (it.shortcut) " ↗" else ""}${if (it.folder) "   ›" else ""}" })
                list.setOnItemClickListener { _, _, position, _ ->
                    if (!loading) {
                        val entry = entries[position]
                        if (entry.folder) { path.add(entry.id to entry.name); load() }
                        else { val fullPath = "${location()} / ${entry.name}"; dialog.dismiss(); onFile(entry.id, fullPath) }
                    }
                }
                list.isEnabled = true
                message.text = if (entries.isEmpty()) "Nenhuma ${if (includeJson) "subpasta ou arquivo JSON" else "subpasta"} neste local." else "Toque no nome da pasta para abrir suas subpastas."
                if (folderAction != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = parent != "#shared"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.text = "Não foi possível abrir esta pasta: ${e.message}" }
            finally {
                loading = false
                progress.visibility = View.GONE
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = path.size > 1
            }
        }
    }
}
