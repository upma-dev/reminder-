package com.example.ui.viewmodel

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.cloud.CloudRepository
import com.example.cloud.MusicSession
import com.example.util.MusicLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

data class ListenState(
    val url: String = "",
    val title: String = "",
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val loading: Boolean = false,
    val error: String? = null,
    /** true = playing a song only for me (not synced with the other person) */
    val solo: Boolean = false,
    /** true = this device is using its own downloaded copy */
    val offlineCopy: Boolean = false
)

/**
 * Two modes:
 *  - Together: both phones follow one shared session (url + playing + position), so play / pause /
 *    seek stay in sync. If a phone has the song downloaded it plays the local copy (no data, no lag).
 *  - Solo: play any saved song only on this phone.
 */
class ListenTogether(private val context: Context, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(ListenState())
    val state: StateFlow<ListenState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var ready = false
    private var currentKey = ""
    private var linkId: String? = null
    private var listenJob: Job? = null
    private var tickJob: Job? = null
    private var pendingSession: MusicSession? = null
    private var lastRemote: MusicSession? = null
    private var solo = false

    fun open(linkId: String) {
        if (this.linkId == linkId) return
        close()
        this.linkId = linkId
        listenJob = scope.launch {
            CloudRepository.sessionFlow(linkId).collect { applyRemote(it) }
        }
        tickJob = scope.launch {
            while (isActive) {
                val p = player
                if (p != null && ready) {
                    try {
                        _state.update { it.copy(positionMs = p.currentPosition.toLong(), playing = p.isPlaying) }
                    } catch (e: Exception) {
                        // player released
                    }
                }
                delay(500)
            }
        }
    }

    fun close() {
        listenJob?.cancel()
        tickJob?.cancel()
        releasePlayer()
        linkId = null
        solo = false
        lastRemote = null
        _state.value = ListenState()
    }

    // ───────────── together: publish to the shared session ─────────────

    fun load(url: String, title: String) {
        val id = linkId ?: return
        val clean = url.trim()
        if (!clean.startsWith("http")) {
            _state.update { it.copy(error = "Direct audio link daalo (http/https .mp3 jaisa)") }
            return
        }
        exitSolo()
        _state.update { it.copy(error = null) }
        scope.launch {
            CloudRepository.setSession(id, MusicSession(clean, title.ifBlank { clean.substringAfterLast('/') }, true, 0L))
        }
    }

    fun togglePlay() {
        if (solo) {
            val p = player ?: return
            try {
                if (p.isPlaying) p.pause() else p.start()
                _state.update { it.copy(playing = p.isPlaying) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }
        val id = linkId ?: return
        val s = _state.value
        if (s.url.isBlank()) return
        scope.launch {
            CloudRepository.setSession(id, MusicSession(s.url, s.title, !s.playing, currentPosition()))
        }
    }

    fun seekTo(ms: Long) {
        if (solo) {
            try {
                player?.seekTo(ms.toInt())
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }
        val id = linkId ?: return
        val s = _state.value
        if (s.url.isBlank()) return
        scope.launch { CloudRepository.setSession(id, MusicSession(s.url, s.title, s.playing, ms)) }
    }

    // ───────────── solo ─────────────

    fun playSolo(file: File, title: String) {
        solo = true
        releasePlayer()
        _state.value = ListenState(title = title, url = "solo", solo = true, loading = true, offlineCopy = true)
        prepare("solo:${file.path}", file.path) { mp ->
            mp.start()
            _state.update { it.copy(playing = true) }
        }
    }

    /** Leave solo mode and fall back to whatever the shared session is doing. */
    fun exitSolo() {
        if (!solo) return
        solo = false
        releasePlayer()
        _state.value = ListenState()
        lastRemote?.let { applyRemote(it) }
    }

    private fun currentPosition(): Long = try {
        if (ready) player?.currentPosition?.toLong() ?: 0L else 0L
    } catch (e: Exception) {
        0L
    }

    // ───────────── remote state -> local player ─────────────

    private fun applyRemote(session: MusicSession?) {
        lastRemote = session
        if (solo) return
        if (session == null || session.url.isBlank()) {
            releasePlayer()
            _state.value = ListenState()
            return
        }
        val local = MusicLibrary.localFileForUrl(context, session.url)
        _state.update { it.copy(url = session.url, title = session.title, error = null, offlineCopy = local != null) }

        if (session.url != currentKey || player == null) {
            _state.update { it.copy(loading = true) }
            pendingSession = session
            prepare(session.url, local?.path ?: session.url) { _ ->
                syncPlayer(pendingSession ?: session)
                pendingSession = null
            }
        } else if (ready) {
            syncPlayer(session)
        } else {
            pendingSession = session
        }
    }

    private fun targetPosition(s: MusicSession): Long =
        if (s.playing) s.positionMs + (System.currentTimeMillis() - s.updatedAt).coerceIn(0L, 10_000L) else s.positionMs

    private fun syncPlayer(s: MusicSession) {
        val p = player ?: return
        try {
            val target = targetPosition(s)
            if (abs(p.currentPosition - target) > 1500) p.seekTo(target.toInt())
            if (s.playing && !p.isPlaying) p.start()
            if (!s.playing && p.isPlaying) p.pause()
            _state.update { it.copy(playing = s.playing) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun prepare(key: String, source: String, onReady: (MediaPlayer) -> Unit) {
        releasePlayer()
        currentKey = key
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(source)
                setOnPreparedListener { mp ->
                    ready = true
                    _state.update { it.copy(loading = false, durationMs = mp.duration.toLong()) }
                    onReady(mp)
                }
                setOnErrorListener { _, _, _ ->
                    _state.update { it.copy(loading = false, playing = false, error = "Ye gaana play nahi ho paya (direct mp3/stream link ya audio file chahiye)") }
                    true
                }
                setOnCompletionListener {
                    _state.update { it.copy(playing = false) }
                    val id = linkId
                    if (!solo && id != null && CloudRepository.uid != null) {
                        scope.launch { CloudRepository.setSession(id, MusicSession(currentKey, _state.value.title, false, 0L)) }
                    }
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.localizedMessage ?: "Player error") }
        }
    }

    private fun releasePlayer() {
        ready = false
        currentKey = ""
        pendingSession = null
        try {
            player?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        player = null
    }
}
