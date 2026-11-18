package com.example.cloud

object EventType {
    const val SOS = "SOS"                 // I am in danger (to my emergency contacts)
    const val SOS_SAFE = "SOS_SAFE"       // I am safe now -> stop the siren on their phone
    const val SOS_ACK = "SOS_ACK"         // "I have seen your SOS" -> back to the sender
    const val MESSAGE = "MESSAGE"         // chat message
    const val REMINDER = "REMINDER"       // reminder pushed to a personal contact
    const val ALARM = "ALARM"             // alarm set on the other person's phone (hour/minute)
    const val NUDGE = "NUDGE"             // 1-tap love note / nudge
    const val LOC_REQUEST = "LOC_REQUEST" // "please turn on your location"
    const val MUSIC = "MUSIC"             // listen-together invite
    const val TASK = "TASK"               // shared to-do added / completed
}

data class SharedTask(
    val id: String,
    val title: String,
    val done: Boolean,
    val addedBy: String,
    val doneBy: String,
    val ts: Long,
    /** -1 = a plain pending task; otherwise a daily routine at hour:minute */
    val hour: Int = -1,
    val minute: Int = -1
)

data class Profile(
    val uid: String,
    val name: String,
    val phone: String,
    val email: String = "",
    val photoB64: String = "",
    val bloodGroup: String = "",
    val medicalNote: String = "",
    val address: String = ""
)

/** What the profile form edits. photoB64 == null means "keep the current photo". */
data class ProfileDetails(
    val name: String,
    val email: String = "",
    val bloodGroup: String = "",
    val medicalNote: String = "",
    val address: String = "",
    val photoB64: String? = null
)

enum class LinkState { ACCEPTED, INCOMING, OUTGOING }

/** A person in my circle. */
data class Contact(
    val linkId: String,
    val uid: String,
    val name: String,
    val phone: String,
    val state: LinkState,
    /** They are my emergency contact: they receive my SOS. */
    val isMyEmergencyContact: Boolean,
    /** I am their emergency contact: I receive their SOS. */
    val iAmTheirEmergencyContact: Boolean,
    /** I share my live location with them. */
    val iShareLocation: Boolean,
    /** They share their live location with me. */
    val theyShareLocation: Boolean
)

data class InboxEvent(
    val id: String,
    val type: String,
    val from: String,
    val fromName: String,
    val fromPhone: String,
    val title: String,
    val body: String,
    val lat: Double?,
    val lng: Double?,
    val hour: Int,
    val minute: Int,
    val extra: String,
    val ts: Long
)

data class ChatMessage(
    val id: String,
    val from: String,
    val text: String,
    val ts: Long
)

data class LocationPoint(
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val ts: Long,
    val sos: Boolean
)

data class MusicSession(
    val url: String = "",
    val title: String = "",
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val updatedAt: Long = 0L,
    val by: String = ""
)
