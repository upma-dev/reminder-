package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloud.CloudRepository
import com.example.cloud.Contact
import com.example.cloud.EventType
import com.example.cloud.LinkState
import com.example.data.AppDatabase
import com.example.data.ReminderRepository
import com.example.data.model.EmergencyConfig
import com.example.data.model.EmergencyLog
import com.example.data.model.ReminderNote
import com.example.data.model.SujalTaskMessage
import com.example.service.SafetyService
import com.example.util.AlarmScheduler
import com.example.util.LivePrefs
import com.example.util.LocationHelper
import com.example.util.SirenPlayer
import com.example.util.SafeTimer
import com.example.util.SosSender
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ReminderRepository(db.reminderDao(), db.sujalTaskDao(), db.emergencyDao())

    // ───────────── local data ─────────────

    val allReminders: StateFlow<List<ReminderNote>> = repository.allReminders
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSujalTasks: StateFlow<List<SujalTaskMessage>> = repository.allSujalTasks
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val emergencyConfig: StateFlow<EmergencyConfig> = repository.emergencyConfig
        .map { it ?: EmergencyConfig() }
        .catch { emit(EmergencyConfig()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EmergencyConfig())

    val emergencyLogs: StateFlow<List<EmergencyLog>> = repository.emergencyLogs
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTag = MutableStateFlow("All")
    val selectedTag: StateFlow<String> = _selectedTag.asStateFlow()

    val filteredReminders: StateFlow<List<ReminderNote>> =
        combine(allReminders, searchQuery, selectedTag) { notes, query, tag ->
            notes.filter { note ->
                val matchesQuery = query.isBlank() ||
                    note.title.contains(query, ignoreCase = true) ||
                    note.queryDetails.contains(query, ignoreCase = true)
                val matchesTag = tag == "All" || note.categoryTag.equals(tag, ignoreCase = true)
                matchesQuery && matchesTag
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ───────────── cloud: people ─────────────

    private val authUid = MutableStateFlow<String?>(CloudRepository.uid)

    /** Called by the UI when the logged-in user changes so cloud listeners re-attach. */
    fun setAuthUid(uid: String?) {
        authUid.value = uid
        com.example.util.EmergencyContacts.load(getApplication())
        // alarms of the account that is now logged in
        viewModelScope.launch {
            try {
                db.reminderDao().getEnabledReminders().forEach { AlarmScheduler.scheduleAlarm(getApplication(), it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val contacts: StateFlow<List<Contact>> = authUid
        .flatMapLatest { if (it == null) flowOf(emptyList()) else CloudRepository.contactsFlow() }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val people: StateFlow<List<Contact>> = contacts
        .map { list -> list.filter { it.state == LinkState.ACCEPTED } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    private fun say(text: String) {
        _toast.tryEmit(text)
    }

    // ───────────── emergency contacts (one-way) ─────────────

    val emergencyContacts: StateFlow<List<com.example.util.EmergencyContact>> = com.example.util.EmergencyContacts.list

    fun addEmergency(name: String, rawPhone: String) {
        val phone = CloudRepository.normalizePhone(rawPhone)
        if (phone.length < 11) {
            say("Sahi mobile number daalo")
            return
        }
        if (!com.example.util.EmergencyContacts.add(getApplication(), name, phone)) {
            say("Ye number pehle se list me hai")
            return
        }
        say("Emergency contact add ho gaya")
        // cloud permission doc: lets them receive the ring + live location if they have the app
        viewModelScope.launch {
            if (CloudRepository.isConfigured && CloudRepository.uid != null) {
                CloudRepository.publishEmergencyContact(name.trim().ifBlank { phone }, phone)
            }
        }
    }

    fun removeEmergency(phone: String) {
        com.example.util.EmergencyContacts.remove(getApplication(), phone)
        viewModelScope.launch {
            if (CloudRepository.isConfigured && CloudRepository.uid != null) CloudRepository.unpublishEmergencyContact(phone)
        }
    }

    // ───────────── blocking mutual contacts ─────────────

    val blocked: StateFlow<Set<String>> = authUid
        .flatMapLatest { if (it == null) flowOf(emptySet()) else CloudRepository.blockedFlow() }
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun blockPerson(c: Contact) = viewModelScope.launch {
        CloudRepository.block(c.uid)
        say("${c.name} block ho gaya. Ab wo aapko message, alarm ya kuch nahi bhej sakta.")
    }

    fun unblockPerson(c: Contact) = viewModelScope.launch {
        CloudRepository.unblock(c.uid)
        say("${c.name} unblock ho gaya")
    }

    fun addPerson(phone: String) {
        viewModelScope.launch {
            val result = CloudRepository.sendRequest(phone)
            result.onSuccess { say("Request bhej di: $it") }
            result.onFailure { say(it.localizedMessage ?: "Request fail ho gayi") }
        }
    }

    fun acceptPerson(c: Contact) = viewModelScope.launch {
        runCatching { CloudRepository.acceptRequest(c.linkId) }
            .onSuccess { say("${c.name} ab aapki list me hai") }
            .onFailure { say(it.localizedMessage ?: "Fail") }
    }

    fun removePerson(c: Contact) = viewModelScope.launch {
        runCatching { CloudRepository.removeLink(c.linkId) }
            .onFailure { say(it.localizedMessage ?: "Fail") }
    }

    fun setEmergencyContact(c: Contact, on: Boolean) = viewModelScope.launch {
        runCatching { CloudRepository.setEmergencyContact(c.linkId, on) }
            .onSuccess { say(if (on) "${c.name} ab emergency contact hai" else "${c.name} emergency list se hata diya") }
            .onFailure { say(it.localizedMessage ?: "Fail") }
    }

    fun setShareLocationWith(c: Contact, on: Boolean) = viewModelScope.launch {
        runCatching { CloudRepository.setShareLocation(c.linkId, on) }
            .onFailure { say(it.localizedMessage ?: "Fail") }
    }

    // ───────────── cloud: live location ─────────────

    private val _liveSharing = MutableStateFlow(LivePrefs.isSharing(application))
    val liveSharing: StateFlow<Boolean> = _liveSharing.asStateFlow()

    fun setLiveSharing(context: Context, on: Boolean) {
        LivePrefs.setSharing(context, on)
        _liveSharing.value = on
        if (on) SafetyService.start(context)
        // When switching on, push one position immediately so contacts see me right away.
        if (on) viewModelScope.launch {
            LocationHelper.getCurrent(context)?.let {
                CloudRepository.publishLocation(it.latitude, it.longitude, it.accuracy, false)
            }
        }
    }

    // ───────────── personal: send things to a person ─────────────

    private fun record(c: Contact, title: String, note: String, ok: Boolean) {
        viewModelScope.launch {
            repository.insertSujalTask(
                SujalTaskMessage(
                    taskTitle = title,
                    noteText = note,
                    recipientName = c.name,
                    recipientPhone = c.phone,
                    statusSent = ok
                )
            )
        }
    }

    fun sendNudge(c: Contact, title: String, body: String) {
        viewModelScope.launch {
            val ok = CloudRepository.sendEvent(c.uid, EventType.NUDGE, title, body)
            record(c, "💖 $title", body, ok)
            say(if (ok) "${c.name} ko bhej diya 💖" else "Bhej nahi paya (internet?)")
        }
    }

    fun sendReminderNow(c: Contact, title: String, body: String) {
        viewModelScope.launch {
            val ok = CloudRepository.sendEvent(c.uid, EventType.REMINDER, title, body)
            record(c, "📌 $title", body, ok)
            say(if (ok) "Reminder ${c.name} ko chala gaya" else "Bhej nahi paya (internet?)")
        }
    }

    fun sendAlarm(c: Contact, title: String, body: String, hour: Int, minute: Int) {
        viewModelScope.launch {
            val ok = CloudRepository.sendEvent(c.uid, EventType.ALARM, title, body, hour = hour, minute = minute)
            record(c, "⏰ $title (${String.format("%02d:%02d", hour, minute)})", body, ok)
            say(if (ok) "${c.name} ke phone par alarm set ho gaya" else "Bhej nahi paya (internet?)")
        }
    }

    fun askForLocation(c: Contact) {
        viewModelScope.launch {
            val ok = CloudRepository.sendEvent(c.uid, EventType.LOC_REQUEST, "Location please")
            say(if (ok) "${c.name} ko location ON karne ka request gaya" else "Bhej nahi paya (internet?)")
        }
    }

    fun inviteToListen(c: Contact, title: String) {
        viewModelScope.launch {
            CloudRepository.sendEvent(c.uid, EventType.MUSIC, title.ifBlank { "Listen together" }, extra = c.linkId)
        }
    }

    fun sendChat(c: Contact, text: String) {
        viewModelScope.launch {
            if (!CloudRepository.sendMessage(c.linkId, c.uid, text)) say("Message nahi gaya")
        }
    }

    // ───────────── listen together ─────────────

    val listen = ListenTogether(application, viewModelScope)

    // ───────────── routines ─────────────

    init {
        viewModelScope.launch {
            try {
                repository.allReminders.firstOrNull()?.let { list ->
                    if (list.isEmpty()) {
                        listOf(
                            ReminderNote(
                                title = "Morning Warm Water & Hydration",
                                queryDetails = "Drink 2 glasses of lukewarm water with lemon to feel energized!",
                                hour = 7, minute = 30, categoryTag = "Health", isAlarmEnabled = true
                            )
                        ).forEach { note ->
                            val id = repository.insertReminder(note)
                            AlarmScheduler.scheduleAlarm(getApplication(), note.copy(id = id))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedTag(tag: String) {
        _selectedTag.value = tag
    }

    fun saveReminder(reminder: ReminderNote) {
        viewModelScope.launch {
            if (reminder.id == 0L) {
                val newId = repository.insertReminder(reminder)
                AlarmScheduler.scheduleAlarm(getApplication(), reminder.copy(id = newId))
            } else {
                repository.updateReminder(reminder)
                AlarmScheduler.scheduleAlarm(getApplication(), reminder)
            }
        }
    }

    fun toggleReminderAlarm(reminder: ReminderNote, enabled: Boolean) {
        viewModelScope.launch {
            val updated = reminder.copy(isAlarmEnabled = enabled)
            repository.updateReminder(updated)
            if (enabled) AlarmScheduler.scheduleAlarm(getApplication(), updated)
            else AlarmScheduler.cancelAlarm(getApplication(), updated.id)
        }
    }

    fun toggleReminderCompleted(reminder: ReminderNote, completed: Boolean) {
        viewModelScope.launch { repository.updateReminder(reminder.copy(isCompleted = completed)) }
    }

    fun deleteReminder(reminder: ReminderNote) {
        viewModelScope.launch {
            repository.deleteReminder(reminder)
            AlarmScheduler.cancelAlarm(getApplication(), reminder.id)
        }
    }

    // ───────────── SOS (sender side) ─────────────

    private val _showPanicDialog = MutableStateFlow(false)
    val showPanicDialog: StateFlow<Boolean> = _showPanicDialog.asStateFlow()

    private val _sosMapLink = MutableStateFlow<String?>(null)
    val sosMapLink: StateFlow<String?> = _sosMapLink.asStateFlow()

    private val _sosRecipients = MutableStateFlow<List<String>>(emptyList())
    val sosRecipients: StateFlow<List<String>> = _sosRecipients.asStateFlow()

    fun triggerPanicMode(context: Context) {
        _showPanicDialog.value = true
        viewModelScope.launch {
            val r = SosSender.trigger(context, reason = "PANIC_BUTTON", fromUi = true)
            _sosMapLink.value = r.mapLink
            _sosRecipients.value = r.recipients
            if (r.recipients.isEmpty() && r.smsTargets == 0) {
                say("Koi emergency contact set nahi hai. Personal tab me kisi ko Emergency contact banao.")
            }
        }
    }

    /** "I am safe": stops my siren and silences the alarm on every phone that received the SOS. */
    fun dismissPanic(context: Context) {
        _showPanicDialog.value = false
        viewModelScope.launch { SosSender.markSafe(context) }
    }

    /** Called on resume: an SOS started by shake / Safe Walk while the app was closed shows its screen. */
    fun syncSosDialog(context: Context) {
        _showPanicDialog.value = LivePrefs.isMySosActive(context)
    }

    // ───────────── women-safety tools ─────────────

    private val _shake = MutableStateFlow(LivePrefs.isShakeEnabled(application))
    val shakeEnabled: StateFlow<Boolean> = _shake.asStateFlow()

    fun setShake(context: Context, on: Boolean) {
        LivePrefs.setShakeEnabled(context, on)
        _shake.value = on
        if (on) SafetyService.start(context)
    }

    private val _safeDeadline = MutableStateFlow(LivePrefs.safeDeadline(application))
    val safeDeadline: StateFlow<Long> = _safeDeadline.asStateFlow()

    fun startSafeWalk(context: Context, minutes: Int) {
        SafeTimer.start(context, minutes)
        _liveSharing.value = true
        _safeDeadline.value = LivePrefs.safeDeadline(context)
        say("Safe Walk shuru: $minutes min. Time khatam hone par SOS khud chala jayega.")
    }

    fun cancelSafeWalk(context: Context) {
        SafeTimer.cancel(context)
        _safeDeadline.value = 0L
        say("Safe Walk band. Aap safe ho 💚")
    }

    fun refreshSafeDeadline(context: Context) {
        _safeDeadline.value = LivePrefs.safeDeadline(context)
    }

    fun updateEmergencyConfig(config: EmergencyConfig) {
        viewModelScope.launch { repository.saveEmergencyConfig(config) }
    }

    override fun onCleared() {
        super.onCleared()
        listen.close()
    }
}
