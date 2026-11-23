package com.ricferreira.photoautomation

import android.content.Context
import java.util.UUID

/** Shared lock keeps a late upload from updating a newly started session. */
object SessionStats {
    @Synchronized
    fun begin(context: Context): String {
        val id = UUID.randomUUID().toString()
        check(context.getSharedPreferences("session", Context.MODE_PRIVATE).edit()
            .putString("id", id).putStringSet("uploaded", emptySet()).commit())
        return id
    }

    @Synchronized
    fun uploaded(context: Context, session: String, photo: String) {
        val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        if (session.isEmpty() || prefs.getString("id", null) != session) return
        val ids = prefs.getStringSet("uploaded", emptySet()).orEmpty().toMutableSet()
        if (ids.add(photo)) check(prefs.edit().putStringSet("uploaded", ids).commit())
    }

    @Synchronized
    fun count(context: Context): Int = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        .getStringSet("uploaded", emptySet()).orEmpty().size
}
