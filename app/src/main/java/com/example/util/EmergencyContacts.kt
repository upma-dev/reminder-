package com.example.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class EmergencyContact(val name: String, val phone: String)

/**
 * My emergency contacts. Anyone can be added by number; they do NOT need to accept.
 * The list is kept on this phone, so an SOS can still go out as SMS with no internet.
 * (The cloud only gets a small "permission" document per contact so people who have the app
 * can receive the ring + live location.)
 */
object EmergencyContacts {

    private const val FILE = "emergency_contacts"
    private const val KEY = "list"

    private val _list = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val list: StateFlow<List<EmergencyContact>> = _list.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(
            FILE + "_" + (com.example.cloud.CloudRepository.uid ?: "local"), Context.MODE_PRIVATE
        )

    fun load(context: Context): List<EmergencyContact> {
        val raw = prefs(context).getString(KEY, "[]") ?: "[]"
        val out = mutableListOf<EmergencyContact>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(EmergencyContact(o.optString("name"), o.optString("phone")))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _list.value = out
        return out
    }

    private fun save(context: Context, items: List<EmergencyContact>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("name", it.name).put("phone", it.phone)) }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
        _list.value = items
    }

    /** Returns false if this number is already in the list. */
    fun add(context: Context, name: String, normalizedPhone: String): Boolean {
        val items = load(context)
        if (items.any { it.phone == normalizedPhone }) return false
        save(context, items + EmergencyContact(name.trim().ifBlank { normalizedPhone }, normalizedPhone))
        return true
    }

    fun remove(context: Context, phone: String) {
        save(context, load(context).filterNot { it.phone == phone })
    }
}
