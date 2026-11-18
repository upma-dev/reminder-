package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloud.CloudRepository
import com.example.cloud.Contact
import com.example.service.SafetyService
import com.example.ui.components.CuteTopAppBar
import com.example.ui.components.EmergencySosDialog
import com.example.ui.screens.CloudSetupRequiredScreen
import com.example.ui.screens.EmergencyScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.PersonScreen
import com.example.ui.screens.PersonalScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.screens.RoutineScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.util.LocationHelper
import com.example.util.NotificationHelper

enum class NavTab(val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    ROUTINES("Routines", Icons.Filled.Alarm, Icons.Outlined.Alarm),
    PERSONAL("Mutual", Icons.Filled.People, Icons.Outlined.People),
    EMERGENCY("SOS Hub", Icons.Filled.Warning, Icons.Outlined.Warning),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {

    private val openLinkId = mutableStateOf<String?>(null)
    private val openLocationPrompt = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        try {
            NotificationHelper.createAllChannels(this)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        readIntent(intent)

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(
                        startLinkId = openLinkId.value,
                        locationPrompt = openLocationPrompt.value,
                        onLocationPromptHandled = { openLocationPrompt.value = false },
                        onLinkHandled = { openLinkId.value = null }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.example.util.ScreenshotPolicy.apply(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        intent?.getStringExtra(NotificationHelper.EXTRA_OPEN_LINK_ID)?.takeIf { it.isNotBlank() }?.let {
            openLinkId.value = it
        }
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_OPEN_LOCATION, false) == true) {
            openLocationPrompt.value = true
        }
    }
}

@Composable
private fun AppRoot(
    startLinkId: String?,
    locationPrompt: Boolean,
    onLocationPromptHandled: () -> Unit,
    onLinkHandled: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val auth by authViewModel.state.collectAsState()
    var offlineMode by rememberSaveable { mutableStateOf(false) }

    when {
        auth.checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        !auth.cloudConfigured && !offlineMode -> CloudSetupRequiredScreen(onContinueOffline = { offlineMode = true })

        !auth.cloudConfigured && offlineMode -> MainAppContent(
            viewModel = viewModel(key = "main_local"),
            profile = null,
            cloudEnabled = false,
            startLinkId = null,
            locationPrompt = false,
            onLocationPromptHandled = {},
            onLinkHandled = {},
            onSignOut = {}
        )

        !auth.signedIn -> LoginScreen(
            state = auth,
            onSendOtp = { activity, phone -> authViewModel.sendOtp(activity, phone) },
            onVerifyOtp = authViewModel::verifyOtp,
            onChangeNumber = authViewModel::changeNumber,
            onGoogle = { authViewModel.signInWithGoogle(it) }
        )

        !auth.ready -> {
            val ctx = LocalContext.current
            ProfileSetupScreen(
                state = auth,
                authEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email.orEmpty(),
                onSaveProfile = authViewModel::saveProfile,
                onSendOtp = { activity, phone -> authViewModel.sendOtp(activity, phone) },
                onVerifyOtp = authViewModel::verifyOtp,
                onChangeNumber = authViewModel::changeNumber,
                onSignOut = {
                    SafetyService.stop(ctx)
                    authViewModel.signOut()
                }
            )
        }

        else -> {
            val context = LocalContext.current
            MainAppContent(
                viewModel = viewModel(key = "main_" + (auth.profile?.uid ?: "x")),
                profile = auth.profile,
                cloudEnabled = true,
                startLinkId = startLinkId,
                locationPrompt = locationPrompt,
                onLocationPromptHandled = onLocationPromptHandled,
                onLinkHandled = onLinkHandled,
                onSignOut = {
                    SafetyService.stop(context)
                    authViewModel.signOut()
                },
                onSaveProfile = authViewModel::saveProfile
            )
        }
    }
}

@Composable
fun MainAppContent(
    profile: com.example.cloud.Profile?,
    cloudEnabled: Boolean,
    startLinkId: String?,
    locationPrompt: Boolean,
    onLocationPromptHandled: () -> Unit,
    onLinkHandled: () -> Unit,
    onSignOut: () -> Unit,
    onSaveProfile: (com.example.cloud.ProfileDetails) -> Unit = {},
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val shakeEnabled by viewModel.shakeEnabled.collectAsState()
    val safeDeadline by viewModel.safeDeadline.collectAsState()

    // Coming back to the app: show my own SOS screen if one is running (started by shake / Safe Walk),
    // and refresh the Safe Walk countdown.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.syncSosDialog(context)
        viewModel.refreshSafeDeadline(context)
    }

    var currentTab by rememberSaveable { mutableStateOf(NavTab.ROUTINES) }
    var openLinkId by rememberSaveable { mutableStateOf<String?>(null) }
    var showLocationDialog by remember { mutableStateOf(false) }

    val filteredReminders by viewModel.filteredReminders.collectAsState()
    val emergencyConfig by viewModel.emergencyConfig.collectAsState()
    val emergencyLogs by viewModel.emergencyLogs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val showPanicDialog by viewModel.showPanicDialog.collectAsState()
    val allContacts by viewModel.contacts.collectAsState()
    val allPeople by viewModel.people.collectAsState()
    val blocked by viewModel.blocked.collectAsState()
    val emergencyList by viewModel.emergencyContacts.collectAsState()
    val people = allPeople.filter { it.uid !in blocked }
    val sujalTasks by viewModel.allSujalTasks.collectAsState()
    val listenState by viewModel.listen.state.collectAsState()
    val liveSharing by viewModel.liveSharing.collectAsState()
    val sosMapLink by viewModel.sosMapLink.collectAsState()
    val sosRecipients by viewModel.sosRecipients.collectAsState()

    // Attach cloud listeners + start the always-on monitor
    LaunchedEffect(cloudEnabled, profile?.name) {
        if (cloudEnabled) {
            viewModel.setAuthUid(CloudRepository.uid)
            SafetyService.start(context)
            CloudRepository.registerForPush() // FCM token -> tokens/{uid}
        } else {
            viewModel.setAuthUid(null)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toast.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // Opened from a chat notification
    LaunchedEffect(startLinkId) {
        if (startLinkId != null) {
            currentTab = NavTab.PERSONAL
            openLinkId = startLinkId
            onLinkHandled()
        }
    }
    // Someone asked me to turn my location on
    LaunchedEffect(locationPrompt) {
        if (locationPrompt) {
            currentTab = NavTab.EMERGENCY
            showLocationDialog = true
            onLocationPromptHandled()
        }
    }

    // First-run runtime permissions
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val wanted = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        try {
            permLauncher.launch(wanted.toTypedArray())
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    // Turning live location ON: make sure permission + GPS are there first.
    val locPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            if (!LocationHelper.isGpsEnabled(context)) {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            viewModel.setLiveSharing(context, true)
        }
    }
    val toggleLiveSharing: (Boolean) -> Unit = { on ->
        if (!on) {
            viewModel.setLiveSharing(context, false)
        } else if (!LocationHelper.hasPermission(context)) {
            locPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            if (!LocationHelper.isGpsEnabled(context)) {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            viewModel.setLiveSharing(context, true)
        }
    }

    val openContact: Contact? = openLinkId?.let { id -> people.firstOrNull { it.linkId == id } }
    // The linked person disappeared (removed) -> leave the chat screen
    LaunchedEffect(openContact, people.size) {
        if (openLinkId != null && people.isNotEmpty() && openContact == null) openLinkId = null
    }

    Scaffold(
        topBar = {
            CuteTopAppBar(
                title = "Cute Reminder",
                onPanicClick = { viewModel.triggerPanicMode(context) }
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .testTag("bottom_navigation_bar")
            ) {
                NavTab.values().forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            currentTab = tab
                            if (tab != NavTab.PERSONAL) openLinkId = null
                        },
                        label = { Text(tab.title) },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (currentTab) {
                NavTab.ROUTINES -> RoutineScreen(
                    notes = filteredReminders,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    selectedTag = selectedTag,
                    onTagSelect = { viewModel.setSelectedTag(it) },
                    onSaveNote = { viewModel.saveReminder(it) },
                    onToggleAlarm = { note, enabled -> viewModel.toggleReminderAlarm(note, enabled) },
                    onToggleCompleted = { note, completed -> viewModel.toggleReminderCompleted(note, completed) },
                    onDeleteNote = { viewModel.deleteReminder(it) },
                    contacts = people
                )

                NavTab.PERSONAL -> {
                    if (!cloudEnabled) {
                        OfflineNotice("Personal circle ke liye login chahiye. Firebase setup (README) poora karo.")
                    } else if (openContact != null) {
                        PersonScreen(
                            contact = openContact,
                            history = sujalTasks,
                            listenState = listenState,
                            onBack = { openLinkId = null },
                            onOpenMusic = { viewModel.listen.open(openContact.linkId) },
                            onCloseMusic = { viewModel.listen.close() },
                            onSendChat = { viewModel.sendChat(openContact, it) },
                            onNudge = { t, b -> viewModel.sendNudge(openContact, t, b) },
                            onReminderNow = { t, b -> viewModel.sendReminderNow(openContact, t, b) },
                            onAlarm = { t, b, h, m -> viewModel.sendAlarm(openContact, t, b, h, m) },
                            onAskLocation = { viewModel.askForLocation(openContact) },
                            onShareLocation = { viewModel.setShareLocationWith(openContact, it) },
                            onMusicLoad = { u, t -> viewModel.listen.load(u, t) },
                            onMusicToggle = { viewModel.listen.togglePlay() },
                            onMusicSeek = { viewModel.listen.seekTo(it) },
                            onMusicInvite = { viewModel.inviteToListen(openContact, it) },
                            onMusicSolo = { f, t -> viewModel.listen.playSolo(f, t) },
                            onMusicExitSolo = { viewModel.listen.exitSolo() }
                        )
                    } else {
                        PersonalScreen(
                            contacts = allContacts,
                            onAdd = { viewModel.addPerson(it) },
                            onAccept = { viewModel.acceptPerson(it) },
                            onRemove = { viewModel.removePerson(it) },
                            blockedUids = blocked,
                            onBlock = { viewModel.blockPerson(it) },
                            onUnblock = { viewModel.unblockPerson(it) },
                            onShareLocation = { c, on -> viewModel.setShareLocationWith(c, on) },
                            onOpen = { openLinkId = it.linkId }
                        )
                    }
                }

                NavTab.EMERGENCY -> EmergencyScreen(
                    config = emergencyConfig,
                    logs = emergencyLogs,
                    emergencyContacts = emergencyList,
                    onAddEmergency = { n, p -> viewModel.addEmergency(n, p) },
                    onRemoveEmergency = { viewModel.removeEmergency(it) },
                    liveSharing = liveSharing,
                    mapLink = sosMapLink,
                    onToggleLiveSharing = toggleLiveSharing,
                    onTriggerPanic = { viewModel.triggerPanicMode(context) },
                    shakeEnabled = shakeEnabled,
                    onToggleShake = { viewModel.setShake(context, it) },
                    safeDeadline = safeDeadline,
                    onStartSafeWalk = { viewModel.startSafeWalk(context, it) },
                    onCancelSafeWalk = { viewModel.cancelSafeWalk(context) },
                    onFakeCall = { sec, name -> com.example.ui.FakeCall.schedule(context, sec, name) }
                )

                NavTab.SETTINGS -> SettingsScreen(
                    config = emergencyConfig,
                    profile = profile,
                    onSaveConfig = { viewModel.updateEmergencyConfig(it) },
                    onSignOut = onSignOut,
                    onSaveProfile = onSaveProfile
                )
            }
        }

        if (showPanicDialog) {
            EmergencySosDialog(
                config = emergencyConfig,
                recipients = sosRecipients,
                mapLink = sosMapLink,
                onDismiss = { viewModel.dismissPanic(context) }
            )
        }

        if (showLocationDialog) {
            AlertDialog(
                onDismissRequest = { showLocationDialog = false },
                title = { Text("📍 Location ON karein?") },
                text = { Text("Aapke kisi dost ne aapki live location maangi hai. ON karne par wo aapko map par dekh sakenge (sirf unko jinke saath aapne share kiya hai).") },
                confirmButton = {
                    TextButton(onClick = {
                        showLocationDialog = false
                        toggleLiveSharing(true)
                    }) { Text("Haan, ON karo") }
                },
                dismissButton = { TextButton(onClick = { showLocationDialog = false }) { Text("Abhi nahi") } }
            )
        }
    }
}

@Composable
private fun OfflineNotice(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
