package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class Track(
    val id: String,        // file name inside the library folder
    val title: String,
    val url: String,       // original link ("" for files imported from the phone)
    val sizeBytes: Long
) {
    val canShare: Boolean get() = url.isNotBlank()
}

data class DownloadState(
    val active: Boolean = false,
    val title: String = "",
    val percent: Int = 0,
    val error: String? = null
)

/**
 * Songs kept inside the app (private storage, other apps cannot read them):
 *  - download from a direct audio link, or
 *  - import a file from the phone.
 * Tracks that came from a link can be played together; the other person uses their own
 * downloaded copy if they have one, otherwise it streams.
 */
object MusicLibrary {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _download = MutableStateFlow(DownloadState())
    val download: StateFlow<DownloadState> = _download.asStateFlow()

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, "music").apply { mkdirs() }

    private fun indexFile(context: Context) = File(dir(context), "index.json")

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    fun refresh(context: Context) {
        val d = dir(context)
        val meta = JSONObject(runCatching { indexFile(context).readText() }.getOrDefault("{}"))
        val list = (d.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name != "index.json" && !it.name.endsWith(".part") }
            .map { f ->
                val m = meta.optJSONObject(f.name)
                Track(f.name, m?.optString("title").orEmpty().ifBlank { f.nameWithoutExtension }, m?.optString("url").orEmpty(), f.length())
            }
            .sortedBy { it.title.lowercase() }
        _tracks.value = list
    }

    private fun saveMeta(context: Context, id: String, title: String, url: String) {
        val meta = JSONObject(runCatching { indexFile(context).readText() }.getOrDefault("{}"))
        meta.put(id, JSONObject().put("title", title).put("url", url))
        indexFile(context).writeText(meta.toString())
    }

    fun fileFor(context: Context, track: Track): File = File(dir(context), track.id)

    /** The downloaded copy of this link, if I have one. */
    fun localFileForUrl(context: Context, url: String): File? {
        if (url.isBlank()) return null
        val f = File(dir(context), "dl_${hash(url)}.audio")
        return if (f.exists() && f.length() > 0) f else null
    }

    fun download(context: Context, url: String, title: String) {
        val app = context.applicationContext
        val clean = url.trim()
        if (!clean.startsWith("http")) {
            _download.value = DownloadState(error = "Direct audio link daalo (http/https)")
            return
        }
        if (_download.value.active) return
        val shown = title.ifBlank { clean.substringAfterLast('/').substringBefore('?').ifBlank { "Song" } }
        _download.value = DownloadState(active = true, title = shown)

        scope.launch {
            val target = File(dir(app), "dl_${hash(clean)}.audio")
            val part = File(dir(app), target.name + ".part")
            try {
                http.newCall(Request.Builder().url(clean).build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("Server ne mana kiya (${resp.code})")
                    val body = resp.body ?: throw IllegalStateException("Khali jawab")
                    val type = body.contentType()?.toString().orEmpty()
                    if (type.startsWith("text/html")) throw IllegalStateException("Ye audio file ka link nahi hai (web page hai)")
                    val total = body.contentLength()
                    var read = 0L
                    body.byteStream().use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(16 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                read += n
                                if (total > 0) _download.update { it.copy(percent = (read * 100 / total).toInt()) }
                            }
                        }
                    }
                }
                part.renameTo(target)
                saveMeta(app, target.name, shown, clean)
                _download.value = DownloadState()
                refresh(app)
            } catch (e: Exception) {
                part.delete()
                _download.value = DownloadState(error = e.localizedMessage ?: "Download fail")
            }
        }
    }

    /** Copies an audio file chosen from the phone into the library. */
    fun importFile(context: Context, uri: Uri) {
        val app = context.applicationContext
        scope.launch {
            try {
                var name = "Song"
                app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) name = c.getString(0) ?: name
                }
                val id = "imp_${hash(uri.toString() + System.currentTimeMillis())}.audio"
                val target = File(dir(app), id)
                app.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                }
                saveMeta(app, id, name.substringBeforeLast('.'), "")
                refresh(app)
            } catch (e: Exception) {
                _download.value = DownloadState(error = e.localizedMessage ?: "Import fail")
            }
        }
    }

    fun delete(context: Context, track: Track) {
        val app = context.applicationContext
        fileFor(app, track).delete()
        val meta = JSONObject(runCatching { indexFile(app).readText() }.getOrDefault("{}"))
        meta.remove(track.id)
        indexFile(app).writeText(meta.toString())
        refresh(app)
    }

    fun clearError() = _download.update { it.copy(error = null) }
}
