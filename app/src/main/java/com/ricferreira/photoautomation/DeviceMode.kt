package com.ricferreira.photoautomation

import android.content.Context

enum class DeviceMode(val title: String, val canEdit: Boolean, val canCapture: Boolean, val description: String) {
    SERVER("Servidor", true, false, "Define e salva a configuração no Drive. Este celular não captura fotos."),
    CLIENT("Cliente", false, true, "Conecta automaticamente ao JSON e obedece ao servidor. O botão central apenas indica o estado da captura."),
    BOTH("Servidor + Cliente", true, true, "Configura e captura neste celular. Use esta opção para um único aparelho.");

    companion object {
        fun current(context: Context): DeviceMode {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            return entries.firstOrNull { it.name == prefs.getString("deviceMode", null) }
                ?: if (prefs.getBoolean("receiver", true)) BOTH else SERVER
        }
    }
}
