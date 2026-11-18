package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloud.CloudRepository
import com.example.cloud.Profile
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

data class AuthState(
    val checking: Boolean = true,
    val cloudConfigured: Boolean = false,
    val signedIn: Boolean = false,
    val profile: Profile? = null,
    val needsPhone: Boolean = false,
    val needsName: Boolean = false,
    val otpSent: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null
) {
    val ready: Boolean get() = signedIn && !needsPhone && !needsName && profile != null
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null

    init {
        CloudRepository.init(application)
        if (!CloudRepository.isConfigured) {
            _state.value = AuthState(checking = false, cloudConfigured = false)
        } else {
            _state.update { it.copy(cloudConfigured = true) }
            val listener = FirebaseAuth.AuthStateListener { refreshProfile() }
            authListener = listener
            FirebaseAuth.getInstance().addAuthStateListener(listener)
        }
    }

    override fun onCleared() {
        authListener?.let { FirebaseAuth.getInstance().removeAuthStateListener(it) }
        super.onCleared()
    }

    fun refreshProfile() {
        if (!CloudRepository.isConfigured) return
        viewModelScope.launch {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                _state.update { it.copy(checking = false, signedIn = false, profile = null, needsPhone = false, needsName = false) }
                return@launch
            }
            try {
                var profile = CloudRepository.loadMyProfile()
                val verifiedPhone = user.phoneNumber.orEmpty()

                // Auto-create profile when we already know everything (e.g. Google name + verified phone).
                if (verifiedPhone.isNotBlank() && (profile == null || profile.phone != verifiedPhone)) {
                    val name = profile?.name?.takeIf { it.isNotBlank() } ?: user.displayName.orEmpty()
                    if (name.isNotBlank()) {
                        CloudRepository.saveMyProfile(com.example.cloud.ProfileDetails(name = name))
                        profile = CloudRepository.loadMyProfile()
                    }
                }
                _state.update {
                    it.copy(
                        checking = false,
                        signedIn = true,
                        profile = profile,
                        needsPhone = verifiedPhone.isBlank() && profile?.phone.isNullOrBlank(),
                        needsName = profile?.name.isNullOrBlank(),
                        busy = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(checking = false, signedIn = true, busy = false, error = e.localizedMessage) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(error = null, info = null) }

    // ───────────── Phone OTP ─────────────

    fun sendOtp(activity: Activity, rawPhone: String) {
        if (!CloudRepository.isConfigured) return
        val phone = CloudRepository.normalizePhone(rawPhone)
        if (phone.length < 11) {
            _state.update { it.copy(error = "Sahi mobile number daalo (country code ke saath ya 10 digit)") }
            return
        }
        _state.update { it.copy(busy = true, error = null, info = null) }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Instant verification / auto SMS retrieval
                signInOrLink(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _state.update { it.copy(busy = false, error = e.localizedMessage ?: "OTP bhejne me error") }
            }

            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
                resendToken = token
                _state.update { it.copy(busy = false, otpSent = true, info = "OTP $phone par bhej diya gaya") }
            }
        }

        val builder = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        resendToken?.let { builder.setForceResendingToken(it) }
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    fun verifyOtp(code: String) {
        val id = verificationId
        if (id == null || code.length < 6) {
            _state.update { it.copy(error = "6 digit OTP daalo") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        signInOrLink(PhoneAuthProvider.getCredential(id, code.trim()))
    }

    fun changeNumber() {
        verificationId = null
        _state.update { it.copy(otpSent = false, error = null, info = null) }
    }

    private fun signInOrLink(credential: AuthCredential) {
        viewModelScope.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val current = auth.currentUser
                if (current != null) {
                    // Google user adding a verified phone number
                    current.linkWithCredential(credential).await()
                } else {
                    auth.signInWithCredential(credential).await()
                }
                _state.update { it.copy(otpSent = false, busy = false, info = null) }
                refreshProfile()
            } catch (e: FirebaseAuthUserCollisionException) {
                _state.update { it.copy(busy = false, error = "Ye number kisi aur account se juda hua hai") }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.localizedMessage ?: "OTP galat hai") }
            }
        }
    }

    // ───────────── Google ─────────────

    fun signInWithGoogle(activity: Activity) {
        if (!CloudRepository.isConfigured) return
        val ctx = activity.applicationContext
        // 1) .env override (GOOGLE_WEB_CLIENT_ID)  2) generated from google-services.json
        val resId = ctx.resources.getIdentifier("default_web_client_id", "string", ctx.packageName)
        val webClientId = com.example.BuildConfig.GOOGLE_WEB_CLIENT_ID.ifBlank {
            if (resId == 0) "" else ctx.getString(resId)
        }
        if (webClientId.isBlank()) {
            _state.update { it.copy(error = "Google Sign-In setup adhura hai: Firebase me Google provider ON karke google-services.json dobara download karo (ya .env me GOOGLE_WEB_CLIENT_ID daalo)") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val option = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val result = CredentialManager.create(activity).getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val google = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCred = GoogleAuthProvider.getCredential(google.idToken, null)
                    FirebaseAuth.getInstance().signInWithCredential(firebaseCred).await()
                    _state.update { it.copy(busy = false) }
                    refreshProfile()
                } else {
                    _state.update { it.copy(busy = false, error = "Google account nahi mila") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.localizedMessage ?: "Google sign-in fail") }
            }
        }
    }

    // ───────────── Profile / logout ─────────────

    /** Google login + manual number: store the typed phone (no OTP). */
    fun savePhone(raw: String) {
        val phone = CloudRepository.normalizePhone(raw)
        if (!Regex("^\\+[0-9]{10,15}$").matches(phone)) {
            _state.update { it.copy(error = "Sahi mobile number daalo (10 digit)") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                CloudRepository.saveMyPhone(phone)
                refreshProfile()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.localizedMessage) }
            }
        }
    }

    fun saveProfile(details: com.example.cloud.ProfileDetails) {
        if (details.name.isBlank()) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                CloudRepository.saveMyProfile(details)
                refreshProfile()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.localizedMessage) }
            }
        }
    }

    fun signOut() {
        verificationId = null
        viewModelScope.launch {
            // This account's alarms / SOS state must not leak into the next login on this phone.
            val app = getApplication<Application>()
            runCatching {
                com.example.data.AppDatabase.getDatabase(app).reminderDao().getEnabledReminders()
                    .forEach { com.example.util.AlarmScheduler.cancelAlarm(app, it.id) }
            }
            runCatching {
                com.example.util.SafeTimer.cancel(app)
                com.example.util.SirenPlayer.stop(app)
                com.example.util.LivePrefs.setSharing(app, false)
                com.example.util.LivePrefs.setMySosActive(app, false)
                com.example.util.LivePrefs.setSosTargets(app, emptyList())
            }
            // Remove the push token first (needs the signed-in session), then sign out.
            if (CloudRepository.isConfigured) {
                CloudRepository.unregisterPush()
                FirebaseAuth.getInstance().signOut()
            }
            _state.update { AuthState(checking = false, cloudConfigured = it.cloudConfigured) }
        }
    }
}
