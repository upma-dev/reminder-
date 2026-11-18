package com.example.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * All cloud access lives here. Every call is safe when Firebase is not configured
 * (no google-services.json): it simply returns empty flows / failures instead of crashing.
 */
object CloudRepository {

    /** Numbers typed without a country code get this prefix. */
    const val DEFAULT_COUNTRY_CODE = "+91"

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val isConfigured: Boolean
        get() = appContext?.let { FirebaseApp.getApps(it).isNotEmpty() } == true

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    val uid: String? get() = if (isConfigured) auth.currentUser?.uid else null

    @Volatile
    var myName: String = ""
        private set

    /** My phone number as stored in my profile (self-entered, NOT OTP-verified). */
    @Volatile
    var myPhone: String = ""
        private set

    fun normalizePhone(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^0-9+]"), "")
        return when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("00") -> "+" + cleaned.drop(2)
            cleaned.length == 10 -> DEFAULT_COUNTRY_CODE + cleaned
            else -> "+$cleaned"
        }
    }

    private fun linkIdOf(a: String, b: String) = if (a < b) "${a}_$b" else "${b}_$a"

    // ───────────────────────── Profile ─────────────────────────

    /** Short medical / address line that travels with every SOS I send. */
    @Volatile
    var myEmergencyInfo: String = ""
        private set

    private fun buildInfo(blood: String, note: String, address: String): String =
        listOf(
            blood.takeIf { it.isNotBlank() }?.let { "Blood group: $it" },
            note.takeIf { it.isNotBlank() }?.let { "Medical: $it" },
            address.takeIf { it.isNotBlank() }?.let { "Home: $it" }
        ).filterNotNull().joinToString(" • ")

    /**
     * Profile data is split by who may read it:
     *  users/{uid}    -> name + verified phone only (needed so friends can find me by number)
     *  profiles/{uid} -> email, blood group, medical note, address: ONLY me
     *  photos/{uid}   -> profile photo: me + people I accepted
     * Blood group / medical note / address reach others only inside an SOS I send.
     */
    suspend fun loadMyProfile(): Profile? {
        val me = uid ?: return null
        val doc = db.collection("users").document(me).get().await()
        if (!doc.exists()) return null
        val priv = try { db.collection("profiles").document(me).get().await() } catch (e: Exception) { null }
        val photo = try { db.collection("photos").document(me).get().await().getString("b64").orEmpty() } catch (e: Exception) { "" }
        val p = Profile(
            uid = me,
            name = doc.getString("name").orEmpty(),
            phone = doc.getString("phone").orEmpty(),
            email = priv?.getString("email").orEmpty(),
            photoB64 = photo,
            bloodGroup = priv?.getString("bloodGroup").orEmpty(),
            medicalNote = priv?.getString("medicalNote").orEmpty(),
            address = priv?.getString("address").orEmpty()
        )
        myName = p.name
        myPhone = p.phone
        myEmergencyInfo = buildInfo(p.bloodGroup, p.medicalNote, p.address)
        photoCache[me] = p.photoB64
        return p
    }

    /** Saves the phone number the user typed in (not OTP-verified: Google login + manual number). */
    suspend fun saveMyPhone(raw: String) {
        val user = auth.currentUser ?: return
        val phone = normalizePhone(raw)
        db.collection("users").document(user.uid).set(
            hashMapOf<String, Any>("phone" to phone, "updatedAt" to System.currentTimeMillis()),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
        myPhone = phone
    }

    /** Creates/updates my profile. A verified (OTP) phone on the account is written too; a manual number is kept as is. */
    suspend fun saveMyProfile(d: ProfileDetails) {
        val user = auth.currentUser ?: return
        val pub = hashMapOf<String, Any>(
            "name" to d.name.trim(),
            "updatedAt" to System.currentTimeMillis()
        )
        val verifiedPhone = user.phoneNumber
        if (!verifiedPhone.isNullOrBlank()) pub["phone"] = verifiedPhone
        db.collection("users").document(user.uid).set(pub, com.google.firebase.firestore.SetOptions.merge()).await()

        db.collection("profiles").document(user.uid).set(
            hashMapOf(
                "email" to d.email.trim().ifBlank { user.email ?: "" },
                "bloodGroup" to d.bloodGroup.trim(),
                "medicalNote" to d.medicalNote.trim(),
                "address" to d.address.trim()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()

        d.photoB64?.let {
            db.collection("photos").document(user.uid).set(hashMapOf("b64" to it)).await()
            photoCache[user.uid] = it
        }
        myName = d.name.trim()
        myEmergencyInfo = buildInfo(d.bloodGroup, d.medicalNote, d.address)
    }

    private val photoCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Profile photo (base64) of a person; rules only allow me and my accepted contacts to read it. */
    suspend fun photoOf(userUid: String): String {
        photoCache[userUid]?.let { return it }
        return try {
            val b64 = db.collection("photos").document(userUid).get().await().getString("b64").orEmpty()
            photoCache[userUid] = b64
            b64
        } catch (e: Exception) {
            "" // not allowed / no photo -> letter avatar
        }
    }

    /** One-shot list of my links (used from background code such as the SOS sender). */
    suspend fun contactsOnce(): List<Contact> {
        val me = uid ?: return emptyList()
        return try {
            db.collection("links").whereArrayContains("members", me).get().await()
                .documents.mapNotNull { toContact(it, me) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ───────────────────────── Emergency contacts (one-way) ─────────────────────────

    private fun emergencyDocId(owner: String, phone: String) = "${owner}_$phone"

    /** Cloud "permission" document: lets this phone number receive my SOS + live location while an SOS is active. */
    suspend fun publishEmergencyContact(name: String, phone: String) {
        val me = uid ?: return
        try {
            db.collection("emergencyContacts").document(emergencyDocId(me, phone)).set(
                hashMapOf("owner" to me, "name" to name, "phone" to phone, "ts" to System.currentTimeMillis())
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun unpublishEmergencyContact(phone: String) {
        val me = uid ?: return
        try {
            db.collection("emergencyContacts").document(emergencyDocId(me, phone)).delete().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Is this number a registered, logged-in-capable app user? Returns their uid or null. */
    suspend fun appUserUid(phone: String): String? = try {
        findUserByPhone(phone)?.uid
    } catch (e: Exception) {
        null
    }

    /** Called when an SOS ends so contacts stop seeing my location. */
    suspend fun markLocationSosOff() {
        val me = uid ?: return
        try {
            db.collection("locations").document(me).update("sos", false).await()
        } catch (e: Exception) {
            // no location doc yet
        }
    }

    // ───────────────────────── Blocking (mutual contacts) ─────────────────────────

    fun blockedFlow(): Flow<Set<String>> = callbackFlow {
        val me = uid
        if (me == null) {
            trySend(emptySet())
            awaitClose { }
            return@callbackFlow
        }
        val reg = db.collection("blocks").whereEqualTo("blocker", me).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            trySend(snap.documents.mapNotNull { it.getString("blocked") }.toSet())
        }
        awaitClose { reg.remove() }
    }

    suspend fun block(otherUid: String) {
        val me = uid ?: return
        try {
            db.collection("blocks").document("${me}_$otherUid").set(
                hashMapOf("blocker" to me, "blocked" to otherUid, "ts" to System.currentTimeMillis())
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun unblock(otherUid: String) {
        val me = uid ?: return
        try {
            db.collection("blocks").document("${me}_$otherUid").delete().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ───────────────────────── Chat management ─────────────────────────

    suspend fun deleteMessage(linkId: String, messageId: String) {
        try {
            db.collection("links").document(linkId).collection("messages").document(messageId).delete().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Deletes every message I sent in this chat, for both people. */
    suspend fun deleteMyMessages(linkId: String) {
        val me = uid ?: return
        try {
            val snap = db.collection("links").document(linkId).collection("messages")
                .whereEqualTo("from", me).get().await()
            snap.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ───────────────────────── Push token ─────────────────────────

    /** Stored in tokens/{uid} (owner-only). The Cloud Function reads it to push events to this phone. */
    suspend fun saveFcmToken(token: String) {
        val me = uid ?: return
        try {
            db.collection("tokens").document(me)
                .set(hashMapOf("token" to token, "updatedAt" to System.currentTimeMillis())).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun registerForPush() {
        if (uid == null) return
        try {
            val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
            saveFcmToken(token)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Call before sign-out so this phone stops receiving the old account's alerts. */
    suspend fun unregisterPush() {
        val me = uid ?: return
        try {
            db.collection("tokens").document(me).delete().await()
            com.google.firebase.messaging.FirebaseMessaging.getInstance().deleteToken().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun findUserByPhone(rawPhone: String): Profile? {
        val phone = normalizePhone(rawPhone)
        val snap = db.collection("users").whereEqualTo("phone", phone).limit(1).get().await()
        val doc = snap.documents.firstOrNull() ?: return null
        return Profile(uid = doc.id, name = doc.getString("name").orEmpty(), phone = phone)
    }

    // ───────────────────────── Contacts / links ─────────────────────────

    fun contactsFlow(): Flow<List<Contact>> = callbackFlow {
        val me = uid
        if (me == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val reg = db.collection("links").whereArrayContains("members", me)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                trySend(snap.documents.mapNotNull { toContact(it, me) }.sortedBy { it.name.lowercase() })
            }
        awaitClose { reg.remove() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun toContact(doc: DocumentSnapshot, me: String): Contact? {
        val members = doc.get("members") as? List<String> ?: return null
        val other = members.firstOrNull { it != me } ?: return null
        val names = doc.get("names") as? Map<String, String> ?: emptyMap()
        val phones = doc.get("phones") as? Map<String, String> ?: emptyMap()
        val emergencyOf = doc.get("emergencyOf") as? List<String> ?: emptyList()
        val shareLoc = doc.get("shareLoc") as? List<String> ?: emptyList()
        val accepted = doc.getString("status") == "ACCEPTED"
        val requestedBy = doc.getString("requestedBy").orEmpty()
        val state = when {
            accepted -> LinkState.ACCEPTED
            requestedBy == me -> LinkState.OUTGOING
            else -> LinkState.INCOMING
        }
        return Contact(
            linkId = doc.id,
            uid = other,
            name = names[other].orEmpty().ifBlank { phones[other].orEmpty() },
            phone = phones[other].orEmpty(),
            state = state,
            isMyEmergencyContact = emergencyOf.contains(me),
            iAmTheirEmergencyContact = emergencyOf.contains(other),
            iShareLocation = shareLoc.contains(me),
            theyShareLocation = shareLoc.contains(other)
        )
    }

    /** Sends a connection request to the account that owns this phone number. */
    suspend fun sendRequest(rawPhone: String): Result<String> {
        val me = uid ?: return Result.failure(IllegalStateException("Login required"))
        return try {
            val target = findUserByPhone(rawPhone)
                ?: return Result.failure(IllegalStateException("Is number par abhi app account nahi hai. Unhe app install karke login karne bolo."))
            if (target.uid == me) return Result.failure(IllegalStateException("Ye aapka apna number hai"))

            val linkId = linkIdOf(me, target.uid)
            val ref = db.collection("links").document(linkId)
            val existing = try { ref.get().await() } catch (e: Exception) { null }
            if (existing != null && existing.exists()) {
                return Result.failure(IllegalStateException("Ye person pehle se list/request me hai"))
            }
            val myPhoneNow = this.myPhone.ifBlank { auth.currentUser?.phoneNumber.orEmpty() }
            ref.set(
                hashMapOf(
                    "members" to listOf(me, target.uid),
                    "status" to "PENDING",
                    "requestedBy" to me,
                    "names" to mapOf(me to myName.ifBlank { "Someone" }, target.uid to target.name),
                    "phones" to mapOf(me to myPhoneNow, target.uid to target.phone),
                    "emergencyOf" to emptyList<String>(),
                    "shareLoc" to emptyList<String>(),
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()
            Result.success(target.name.ifBlank { target.phone })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptRequest(linkId: String) {
        db.collection("links").document(linkId).update("status", "ACCEPTED").await()
    }

    suspend fun removeLink(linkId: String) {
        db.collection("links").document(linkId).delete().await()
    }

    suspend fun setEmergencyContact(linkId: String, on: Boolean) {
        val me = uid ?: return
        val update = if (on) FieldValue.arrayUnion(me) else FieldValue.arrayRemove(me)
        val ref = db.collection("links").document(linkId)
        ref.update("emergencyOf", update).await()
        // An emergency contact always gets live location too (can be switched off separately).
        if (on) ref.update("shareLoc", FieldValue.arrayUnion(me)).await()
    }

    suspend fun setShareLocation(linkId: String, on: Boolean) {
        val me = uid ?: return
        val update = if (on) FieldValue.arrayUnion(me) else FieldValue.arrayRemove(me)
        db.collection("links").document(linkId).update("shareLoc", update).await()
    }

    // ───────────────────────── Events (inbox) ─────────────────────────

    suspend fun sendEvent(
        to: String,
        type: String,
        title: String = "",
        body: String = "",
        lat: Double? = null,
        lng: Double? = null,
        hour: Int = -1,
        minute: Int = -1,
        extra: String = ""
    ): Boolean {
        val me = uid ?: return false
        return try {
            val user = auth.currentUser
            val data = hashMapOf<String, Any>(
                "type" to type,
                "from" to me,
                "fromName" to myName.ifBlank { user?.displayName ?: "Someone" },
                "fromPhone" to myPhone.ifBlank { user?.phoneNumber ?: "" },
                "to" to to,
                "title" to title,
                "body" to body,
                "hour" to hour,
                "minute" to minute,
                "extra" to extra,
                "ts" to System.currentTimeMillis()
            )
            if (lat != null && lng != null) {
                data["lat"] = lat
                data["lng"] = lng
            }
            db.collection("events").add(data).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Listens to my inbox; each new event is delivered once, then deleted from the cloud. */
    fun listenInbox(onEvent: (InboxEvent) -> Unit): ListenerRegistration? {
        val me = uid ?: return null
        return db.collection("events").whereEqualTo("to", me)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                for (change in snap.documentChanges) {
                    if (change.type != DocumentChange.Type.ADDED) continue
                    val d = change.document
                    val event = InboxEvent(
                        id = d.id,
                        type = d.getString("type").orEmpty(),
                        from = d.getString("from").orEmpty(),
                        fromName = d.getString("fromName").orEmpty(),
                        fromPhone = d.getString("fromPhone").orEmpty(),
                        title = d.getString("title").orEmpty(),
                        body = d.getString("body").orEmpty(),
                        lat = d.getDouble("lat"),
                        lng = d.getDouble("lng"),
                        hour = (d.getLong("hour") ?: -1L).toInt(),
                        minute = (d.getLong("minute") ?: -1L).toInt(),
                        extra = d.getString("extra").orEmpty(),
                        ts = d.getLong("ts") ?: 0L
                    )
                    try {
                        onEvent(event)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    d.reference.delete()
                }
            }
    }

    // ───────────────────────── Chat ─────────────────────────

    fun messagesFlow(linkId: String): Flow<List<ChatMessage>> = callbackFlow {
        val reg = db.collection("links").document(linkId).collection("messages")
            .orderBy("ts", Query.Direction.ASCENDING)
            .limitToLast(300)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                trySend(snap.documents.map {
                    ChatMessage(it.id, it.getString("from").orEmpty(), it.getString("text").orEmpty(), it.getLong("ts") ?: 0L)
                })
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendMessage(linkId: String, toUid: String, text: String): Boolean {
        val me = uid ?: return false
        if (text.isBlank()) return false
        return try {
            val now = System.currentTimeMillis()
            val batch = db.batch()
            batch.set(
                db.collection("links").document(linkId).collection("messages").document(),
                hashMapOf("from" to me, "to" to toUid, "text" to text.trim(), "ts" to now)
            )
            batch.set(
                db.collection("events").document(),
                hashMapOf(
                    "type" to EventType.MESSAGE,
                    "from" to me,
                    "fromName" to myName.ifBlank { "Someone" },
                    "fromPhone" to myPhone.ifBlank { auth.currentUser?.phoneNumber ?: "" },
                    "to" to toUid,
                    "title" to myName.ifBlank { "New message" },
                    "body" to text.trim(),
                    "hour" to -1,
                    "minute" to -1,
                    "extra" to linkId,
                    "ts" to now
                )
            )
            batch.commit().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ───────────────────────── Shared tasks ─────────────────────────

    fun tasksFlow(linkId: String): Flow<List<SharedTask>> = callbackFlow {
        val reg = db.collection("links").document(linkId).collection("tasks")
            .orderBy("ts", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                trySend(snap.documents.map {
                    SharedTask(
                        id = it.id,
                        title = it.getString("title").orEmpty(),
                        done = it.getBoolean("done") ?: false,
                        addedBy = it.getString("addedBy").orEmpty(),
                        doneBy = it.getString("doneBy").orEmpty(),
                        ts = it.getLong("ts") ?: 0L,
                        hour = (it.getLong("hour") ?: -1L).toInt(),
                        minute = (it.getLong("minute") ?: -1L).toInt()
                    )
                })
            }
        awaitClose { reg.remove() }
    }

    /** hour/minute >= 0 makes it a shared DAILY ROUTINE (both phones get a daily alarm). */
    suspend fun addTask(linkId: String, otherUid: String, title: String, hour: Int = -1, minute: Int = -1) {
        val me = uid ?: return
        if (title.isBlank()) return
        try {
            db.collection("links").document(linkId).collection("tasks").add(
                hashMapOf(
                    "title" to title.trim(), "done" to false, "addedBy" to me, "doneBy" to "",
                    "ts" to System.currentTimeMillis(), "hour" to hour, "minute" to minute
                )
            ).await()
            if (hour >= 0) {
                // the other person gets the same daily alarm on their phone
                sendEvent(otherUid, EventType.ALARM, title.trim(), "Shared daily routine", hour = hour, minute = minute)
            } else {
                sendEvent(otherUid, EventType.TASK, "New shared task", title.trim(), extra = linkId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun setTaskDone(linkId: String, otherUid: String, task: SharedTask, done: Boolean) {
        val me = uid ?: return
        try {
            db.collection("links").document(linkId).collection("tasks").document(task.id)
                .update(mapOf("done" to done, "doneBy" to if (done) me else "")).await()
            if (done) sendEvent(otherUid, EventType.TASK, "Task done ✅", task.title, extra = linkId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteTask(linkId: String, taskId: String) {
        try {
            db.collection("links").document(linkId).collection("tasks").document(taskId).delete().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ───────────────────────── Live location ─────────────────────────

    suspend fun publishLocation(lat: Double, lng: Double, accuracy: Float, sos: Boolean) {
        val me = uid ?: return
        try {
            db.collection("locations").document(me).set(
                hashMapOf(
                    "lat" to lat,
                    "lng" to lng,
                    "acc" to accuracy.toDouble(),
                    "sos" to sos,
                    "ts" to System.currentTimeMillis()
                )
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun locationFlow(otherUid: String): Flow<LocationPoint?> = callbackFlow {
        val reg = db.collection("locations").document(otherUid).addSnapshotListener { doc, err ->
            if (err != null || doc == null || !doc.exists()) {
                trySend(null)
            } else {
                val lat = doc.getDouble("lat")
                val lng = doc.getDouble("lng")
                if (lat == null || lng == null) {
                    trySend(null)
                } else {
                    trySend(
                        LocationPoint(
                            lat, lng,
                            (doc.getDouble("acc") ?: 0.0).toFloat(),
                            doc.getLong("ts") ?: 0L,
                            doc.getBoolean("sos") ?: false
                        )
                    )
                }
            }
        }
        awaitClose { reg.remove() }
    }

    // ───────────────────────── Listen together ─────────────────────────

    fun sessionFlow(linkId: String): Flow<MusicSession?> = callbackFlow {
        val reg = db.collection("links").document(linkId).collection("session").document("current")
            .addSnapshotListener { doc, _ ->
                if (doc == null || !doc.exists()) {
                    trySend(null)
                } else {
                    trySend(
                        MusicSession(
                            url = doc.getString("url").orEmpty(),
                            title = doc.getString("title").orEmpty(),
                            playing = doc.getBoolean("playing") ?: false,
                            positionMs = doc.getLong("positionMs") ?: 0L,
                            updatedAt = doc.getLong("updatedAt") ?: 0L,
                            by = doc.getString("by").orEmpty()
                        )
                    )
                }
            }
        awaitClose { reg.remove() }
    }

    suspend fun setSession(linkId: String, session: MusicSession) {
        val me = uid ?: return
        try {
            db.collection("links").document(linkId).collection("session").document("current").set(
                hashMapOf(
                    "url" to session.url,
                    "title" to session.title,
                    "playing" to session.playing,
                    "positionMs" to session.positionMs,
                    "updatedAt" to System.currentTimeMillis(),
                    "by" to me
                )
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
