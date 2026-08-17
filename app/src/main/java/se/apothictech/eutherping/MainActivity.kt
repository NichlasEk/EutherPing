package se.apothictech.eutherping

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Bitmap
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import se.apothictech.eutherping.contacts.ContactRepository
import se.apothictech.eutherping.contacts.PhoneContact
import se.apothictech.eutherping.secure.SecurePeerState
import se.apothictech.eutherping.secure.SecureProtocol
import se.apothictech.eutherping.secure.SecureAttachmentDescriptor
import se.apothictech.eutherping.secure.SecureAttachmentRepository
import se.apothictech.eutherping.secure.BluetoothAttachmentTransport
import se.apothictech.eutherping.secure.SecureRepository
import se.apothictech.eutherping.sms.IncomingMessageNotifier
import se.apothictech.eutherping.sms.CarrierMmsAttachment
import se.apothictech.eutherping.sms.CarrierMmsRepository
import se.apothictech.eutherping.sms.CarrierSubscription
import se.apothictech.eutherping.sms.CarrierSubscriptionRepository
import se.apothictech.eutherping.sms.CachedConversation
import se.apothictech.eutherping.sms.CachedConversationIndex
import se.apothictech.eutherping.sms.ConversationIndexCache
import se.apothictech.eutherping.sms.SmsRepository
import se.apothictech.eutherping.sms.SmsConversationIndexEntry
import se.apothictech.eutherping.sms.SmsSearchHit
import se.apothictech.eutherping.sms.MessageDeliveryState
import se.apothictech.eutherping.sms.deliveryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LocalLightTheme = staticCompositionLocalOf { false }

private val Void: Color @Composable get() = MaterialTheme.colorScheme.background
private val Deep: Color @Composable get() = MaterialTheme.colorScheme.surface
private val Panel: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val Toxic: Color @Composable get() = MaterialTheme.colorScheme.primary
private val ToxicSoft: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val Amber: Color @Composable get() = MaterialTheme.colorScheme.secondary
private val Violet: Color @Composable get() = MaterialTheme.colorScheme.tertiary
private val Mist: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val Muted: Color @Composable get() = MaterialTheme.colorScheme.outline

private enum class AppTheme(val storedValue: String) {
    COOL_DARK("cool_dark"),
    LIGHT("light"),
}

internal const val PREFERENCES_NAME = "eutherping_preferences"
private const val THEME_PREFERENCE = "app_theme"

private fun loadAppTheme(context: android.content.Context): AppTheme {
    val stored = context.getSharedPreferences(PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)
        .getString(THEME_PREFERENCE, null)
    return AppTheme.entries.firstOrNull { it.storedValue == stored } ?: AppTheme.COOL_DARK
}

private fun saveAppTheme(context: android.content.Context, theme: AppTheme) {
    context.getSharedPreferences(PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)
        .edit()
        .putString(THEME_PREFERENCE, theme.storedValue)
        .apply()
}

internal fun shouldRelockVessels(
    biometricGateEnabled: Boolean,
    trustedActivityResultPending: Boolean,
): Boolean = biometricGateEnabled && !trustedActivityResultPending

enum class Transport(val label: String) {
    SECURE("SECURE PING"),
    SMS("CELL // SMS + MMS"),
}

private enum class SignalTab(val label: String) {
    SIGNALS("SIGNALS"),
    CONTACTS("VESSELS"),
    SYSTEM("SYSTEM"),
}

private enum class ConversationControlAction {
    TOGGLE_PIN,
    TOGGLE_ARCHIVE,
    TOGGLE_READ,
    TOGGLE_BLOCK,
}

private data class Conversation(
    val id: Int,
    val name: String,
    val initials: String,
    val preview: String,
    val time: String,
    val transport: Transport,
    val unread: Int = 0,
    val distance: String,
    val smsAddress: String? = null,
    val threadId: Long? = null,
    val hasDraft: Boolean = false,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val blocked: Boolean = false,
    val recipients: List<String> = smsAddress?.let(::listOf).orEmpty(),
)

private data class DemoMessage(
    val id: Long,
    val text: String,
    val outgoing: Boolean,
    val time: String,
    val transport: Transport,
    val attachment: SecureAttachmentDescriptor? = null,
    val carrierMmsAttachment: CarrierMmsAttachment? = null,
    val wireBody: String = text,
    val isMms: Boolean = false,
    val deliveryState: MessageDeliveryState? = null,
    val subscriptionId: Int? = null,
)

private sealed interface ImageActionTarget {
    val suggestedName: String

    data class Secure(val descriptor: SecureAttachmentDescriptor) : ImageActionTarget {
        override val suggestedName: String = descriptor.name
    }

    data class Carrier(val attachment: CarrierMmsAttachment) : ImageActionTarget {
        override val suggestedName: String = attachment.name
    }
}

private fun safeImageName(name: String): String = name
    .substringAfterLast('/')
    .substringAfterLast('\\')
    .replace(Regex("[^A-Za-z0-9._ -]"), "_")
    .ifBlank { "EutherPing-image" }

private data class DeckHistory(
    val signals: List<Conversation> = emptyList(),
    val vessels: List<Conversation> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

private data class ContactsState(
    val contacts: List<PhoneContact> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

private data class MessageSearchState(
    val hits: List<SmsSearchHit> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

private data class LoadedConversationMessages(
    val messages: List<DemoMessage> = emptyList(),
    val hasOlder: Boolean = false,
)

private const val INITIAL_CONVERSATION_MESSAGES = 20
private const val CONVERSATION_PAGE_INCREMENT = 30

private data class ConversationPageCacheKey(
    val threadId: Long?,
    val address: String,
    val secureLane: Boolean,
    val smsRevision: Int,
    val secureRevision: Int,
    val attachmentRevision: Int,
    val limit: Int,
)

private object ConversationPageMemoryCache {
    private const val MAX_PAGES = 12
    private val pages = object : LinkedHashMap<ConversationPageCacheKey, LoadedConversationMessages>(
        MAX_PAGES,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<ConversationPageCacheKey, LoadedConversationMessages>?,
        ): Boolean = size > MAX_PAGES
    }

    @Synchronized
    fun get(key: ConversationPageCacheKey): LoadedConversationMessages? = pages[key]

    @Synchronized
    fun put(key: ConversationPageCacheKey, page: LoadedConversationMessages) {
        pages[key] = page
    }
}

private fun applyContactNames(history: DeckHistory, contacts: List<PhoneContact>): DeckHistory {
    fun label(conversation: Conversation): Conversation {
        if (conversation.recipients.size > 1) {
            val names = conversation.recipients.map { address ->
                ContactRepository.displayName(contacts, address) ?: address
            }
            val label = names.joinToString(", ")
            return conversation.copy(name = label, initials = names.take(2).joinToString("") { nameInitials(it).take(1) })
        }
        val address = conversation.smsAddress ?: return conversation
        val name = ContactRepository.displayName(contacts, address) ?: return conversation
        return conversation.copy(name = name, initials = nameInitials(name))
    }
    return history.copy(
        signals = history.signals.map(::label),
        vessels = history.vessels.map(::label),
    )
}

private fun Conversation.cached() = CachedConversation(
    id = id,
    name = name,
    initials = initials,
    preview = preview,
    time = time,
    lane = transport.name,
    unread = unread,
    distance = distance,
    smsAddress = smsAddress.orEmpty(),
    threadId = threadId,
    recipients = recipients,
)

private fun CachedConversation.live(): Conversation? {
    val transport = runCatching { Transport.valueOf(lane) }.getOrNull() ?: return null
    if (smsAddress.isBlank()) return null
    return Conversation(
        id = id,
        name = name,
        initials = initials,
        preview = preview,
        time = time,
        transport = transport,
        unread = unread,
        distance = distance,
        smsAddress = smsAddress,
        threadId = threadId,
        recipients = recipients,
    )
}

private fun DeckHistory.cached() = CachedConversationIndex(
    signals = signals.map(Conversation::cached),
    vessels = vessels.map(Conversation::cached),
    updatedAt = System.currentTimeMillis(),
)

private fun CachedConversationIndex.live() = DeckHistory(
    signals = signals.mapNotNull(CachedConversation::live),
    vessels = vessels.mapNotNull(CachedConversation::live),
    loading = true,
)

private fun buildDeckHistory(
    context: android.content.Context,
    entries: List<SmsConversationIndexEntry>,
): DeckHistory {
    val signals = mutableListOf<Conversation>()
    val vessels = mutableListOf<Conversation>()
    entries.forEach { entry ->
        entry.latestOrdinary?.let { latest ->
            signals += Conversation(
                id = (100_000L + entry.threadId).toInt(),
                name = entry.address,
                initials = addressInitials(entry.address),
                preview = latest.body,
                time = formatMessageTime(latest.timestamp),
                transport = Transport.SMS,
                unread = entry.ordinaryUnread,
                distance = CarrierSubscriptionRepository.label(context, latest.subscriptionId)
                    ?: "ANDROID TELEPHONY",
                smsAddress = entry.address,
                threadId = entry.threadId,
                recipients = entry.participants,
            )
        }
        val peer = SecureRepository.peer(context, entry.address)
        if (peer.state != SecurePeerState.NONE || entry.latestSecure != null) {
            val decoded = entry.latestSecure?.let { message ->
                SecureRepository.decodeForDisplay(
                context = context,
                    address = entry.address,
                body = message.body,
                incoming = message.incoming,
                )
            }
            vessels += Conversation(
                id = (200_000L + entry.threadId).toInt(),
                name = entry.address,
                initials = addressInitials(entry.address),
                preview = decoded?.text ?: securePeerPreview(peer.state),
                time = formatMessageTime(entry.latestSecure?.timestamp ?: entry.latestTimestamp),
                transport = Transport.SECURE,
                unread = entry.secureUnread,
                distance = securePeerLabel(peer.state),
                smsAddress = entry.address,
                threadId = entry.threadId,
            )
        }
    }
    return DeckHistory(signals = signals, vessels = vessels)
}

class MainActivity : ComponentActivity() {
    private var requestedAddress by mutableStateOf<String?>(null)
    private var requestedSecureLane by mutableStateOf(false)
    private var framePerformanceTracker: FramePerformanceTracker? = null
    private var backgroundedAtElapsed = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        val createStartedAt = SystemClock.elapsedRealtime()
        val firstActivityInProcess = synchronized(MainActivity::class.java) {
            processFirstActivity.also { processFirstActivity = false }
        }
        super.onCreate(savedInstanceState)
        requestedAddress = intent.smsAddress()
        requestedSecureLane = intent.getBooleanExtra(EXTRA_SECURE_LANE, false)
        SecureAttachmentRepository.clearTransientPlaintext(this)
        SecureAttachmentRepository.ensureServerStarted(this).onFailure {
            Log.w("EutherPingAttachment", "Direct Wi-Fi attachment server is unavailable", it)
        }
        if (BluetoothAttachmentTransport.hasPermission(this)) {
            BluetoothAttachmentTransport.ensureServerStarted(this).onFailure {
                Log.i("EutherPingAttachment", "Bluetooth attachment server is not ready", it)
            }
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            EutherPingApp(
                requestedAddress = requestedAddress,
                requestedSecureLane = requestedSecureLane,
                onAddressConsumed = {
                    requestedAddress = null
                    requestedSecureLane = false
                },
            )
        }
        framePerformanceTracker = FramePerformanceTracker(this, window)
        window.decorView.post {
            PerformanceDiagnostics.record(
                this,
                event = if (firstActivityInProcess) "cold_first_frame" else "warm_first_frame",
                durationMs = SystemClock.elapsedRealtime() - PROCESS_STARTED_ELAPSED,
            )
        }
        PerformanceDiagnostics.record(
            this,
            event = "activity_create",
            durationMs = SystemClock.elapsedRealtime() - createStartedAt,
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedAddress = intent.smsAddress()
        requestedSecureLane = intent.getBooleanExtra(EXTRA_SECURE_LANE, false)
    }

    override fun onResume() {
        super.onResume()
        val resumeStartedAt = backgroundedAtElapsed
        if (resumeStartedAt > 0L) {
            backgroundedAtElapsed = 0L
            window.decorView.post {
                PerformanceDiagnostics.record(
                    this,
                    event = "warm_resume_frame",
                    durationMs = SystemClock.elapsedRealtime() - resumeStartedAt,
                )
            }
        }
        if (BluetoothAttachmentTransport.hasPermission(this)) {
            BluetoothAttachmentTransport.ensureServerStarted(this).onFailure {
                Log.i("EutherPingAttachment", "Bluetooth attachment server is not ready", it)
            }
        }
    }

    override fun onStop() {
        backgroundedAtElapsed = SystemClock.elapsedRealtime()
        framePerformanceTracker?.flush()
        super.onStop()
    }

    override fun onDestroy() {
        framePerformanceTracker?.stop()
        framePerformanceTracker = null
        super.onDestroy()
    }

    companion object {
        private val PROCESS_STARTED_ELAPSED = SystemClock.elapsedRealtime()
        private var processFirstActivity = true
        const val EXTRA_SECURE_LANE = "se.apothictech.eutherping.SECURE_LANE"
    }
}

@Composable
private fun EutherPingApp(
    requestedAddress: String?,
    requestedSecureLane: Boolean,
    onAddressConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var appTheme by rememberSaveable { mutableStateOf(loadAppTheme(context)) }
    var activeConversation by remember { mutableStateOf<Conversation?>(null) }
    var draftRevision by remember { mutableIntStateOf(0) }
    var selectedTab by rememberSaveable { mutableStateOf(SignalTab.SIGNALS) }
    var biometricGateEnabled by remember { mutableStateOf(VesselBiometricGate.isEnabled(context)) }
    var vesselsUnlocked by remember { mutableStateOf(false) }
    var showVesselGate by remember { mutableStateOf(false) }
    var pendingSecureConversation by remember { mutableStateOf<Conversation?>(null) }
    val trustedActivityResultPending = remember { mutableStateOf(false) }
    var biometricAttempt by remember { mutableIntStateOf(0) }
    var biometricError by remember { mutableStateOf<String?>(null) }
    var setupRevision by remember { mutableIntStateOf(0) }
    var carrierPermissionRequested by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, biometricGateEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                setupRevision++
                trustedActivityResultPending.value = false
            }
            if (
                event == Lifecycle.Event.ON_STOP &&
                shouldRelockVessels(biometricGateEnabled, trustedActivityResultPending.value)
            ) {
                vesselsUnlocked = false
                showVesselGate = false
                pendingSecureConversation = null
                if (selectedTab == SignalTab.CONTACTS) selectedTab = SignalTab.SIGNALS
                if (activeConversation?.transport == Transport.SECURE) activeConversation = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        setupRevision++
    }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        setupRevision++
        if (SmsRepository.isDefaultSmsApp(context)) {
            permissionsLauncher.launch(SmsRepository.requiredPermissions)
        }
    }
    val isDefaultSmsApp = remember(setupRevision) { SmsRepository.isDefaultSmsApp(context) }
    val hasSmsPermissions = remember(setupRevision) { SmsRepository.hasSmsPermissions(context) }
    val smsRevision = rememberSmsRevision(
        enabled = isDefaultSmsApp && hasSmsPermissions,
        resumeRevision = setupRevision,
    )

    LaunchedEffect(isDefaultSmsApp, hasSmsPermissions, setupRevision) {
        if (isDefaultSmsApp && !hasSmsPermissions) {
            permissionsLauncher.launch(SmsRepository.requiredPermissions)
        } else if (isDefaultSmsApp && hasSmsPermissions) {
            if (!carrierPermissionRequested && !SmsRepository.hasCarrierIdentityPermissions(context)) {
                carrierPermissionRequested = true
                permissionsLauncher.launch(SmsRepository.carrierIdentityPermissions)
            }
            withContext(Dispatchers.IO) { CarrierMmsRepository.recoverPendingDownloads(context) }
        }
    }

    fun requestSmsSetup() {
        if (!SmsRepository.isDefaultSmsApp(context)) {
            val request = if (Build.VERSION.SDK_INT >= 29) {
                context.getSystemService(RoleManager::class.java)
                    .createRequestRoleIntent(RoleManager.ROLE_SMS)
            } else {
                Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                }
            }
            roleLauncher.launch(request)
        } else {
            permissionsLauncher.launch(SmsRepository.requiredPermissions)
        }
    }

    fun requestVesselAccess(conversation: Conversation? = null) {
        if (!biometricGateEnabled || vesselsUnlocked) {
            selectedTab = SignalTab.CONTACTS
            if (conversation != null) activeConversation = conversation
            return
        }
        pendingSecureConversation = conversation
        biometricError = null
        biometricAttempt++
        showVesselGate = true
    }

    LaunchedEffect(requestedAddress, requestedSecureLane) {
        val address = requestedAddress?.trim().orEmpty()
        if (address.isNotEmpty()) {
            val contactName = withContext(Dispatchers.IO) {
                ContactRepository.displayName(context, address)
            }
            if (requestedSecureLane) {
                requestVesselAccess(secureConversation(address, null, contactName))
            } else {
                selectedTab = SignalTab.SIGNALS
                activeConversation = cellConversation(address, null, contactName)
            }
            onAddressConsumed()
        }
    }
    fun closeConversation() {
        activeConversation = null
        draftRevision++
    }
    BackHandler(enabled = activeConversation != null) {
        closeConversation()
    }
    val isLightTheme = appTheme == AppTheme.LIGHT
    val scheme = if (isLightTheme) {
        lightColorScheme(
            primary = Color(0xFF276F31),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFBCE6C1),
            secondary = Color(0xFFA54B00),
            onSecondary = Color.White,
            tertiary = Color(0xFF783A98),
            onTertiary = Color.White,
            background = Color(0xFFF0F7F1),
            surface = Color(0xFFFAFDFA),
            surfaceVariant = Color(0xFFE0ECE2),
            onBackground = Color(0xFF102116),
            onSurface = Color(0xFF102116),
            outline = Color(0xFF607766),
        )
    } else {
        darkColorScheme(
            primary = Color(0xFF8BFF62),
            onPrimary = Color(0xFF020604),
            primaryContainer = Color(0xFF4FB847),
            secondary = Color(0xFFFF9D32),
            tertiary = Color(0xFFC87CFF),
            background = Color(0xFF020604),
            surface = Color(0xFF06100B),
            surfaceVariant = Color(0xE80A1510),
            onBackground = Color(0xFFD7F7DC),
            onSurface = Color(0xFFD7F7DC),
            outline = Color(0xFF78927F),
        )
    }

    SideEffect {
        (context as? MainActivity)?.enableEdgeToEdge(
            statusBarStyle = if (isLightTheme) {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                )
            } else {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            },
            navigationBarStyle = if (isLightTheme) {
                SystemBarStyle.light(0xFFF0F7F1.toInt(), 0xFFF0F7F1.toInt())
            } else {
                SystemBarStyle.dark(android.graphics.Color.BLACK)
            },
        )
    }

    CompositionLocalProvider(LocalLightTheme provides isLightTheme) {
        MaterialTheme(colorScheme = scheme) {
            Surface(modifier = Modifier.fillMaxSize(), color = Void) {
                AbyssBackground {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (activeConversation == null) 1f else 0f),
                        ) {
                            SignalDeck(
                                selectedTab = selectedTab,
                                onSelectedTabChange = { tab ->
                                    if (tab == SignalTab.CONTACTS) requestVesselAccess() else selectedTab = tab
                                },
                                isDefaultSmsApp = isDefaultSmsApp,
                                hasSmsPermissions = hasSmsPermissions,
                                smsRevision = smsRevision,
                                draftRevision = draftRevision,
                                permissionRevision = setupRevision,
                                appTheme = appTheme,
                                interactionEnabled = activeConversation == null,
                                onThemeChange = { selectedTheme ->
                                    appTheme = selectedTheme
                                    saveAppTheme(context, selectedTheme)
                                },
                                biometricGateEnabled = biometricGateEnabled,
                                onBiometricGateEnabledChange = { enabled ->
                                    biometricGateEnabled = enabled
                                    VesselBiometricGate.setEnabled(context, enabled)
                                    if (!enabled) vesselsUnlocked = true
                                },
                                onRequestSmsSetup = ::requestSmsSetup,
                                onOpenConversation = { conversation ->
                                    if (conversation.transport == Transport.SECURE) {
                                        requestVesselAccess(conversation)
                                    } else {
                                        activeConversation = conversation
                                    }
                                },
                            )
                        }
                        activeConversation?.let { conversation ->
                            ConversationDeck(
                                conversation = conversation,
                                smsRevision = smsRevision,
                                onBack = ::closeConversation,
                                onTrustedActivityResultPendingChange = { pending ->
                                    trustedActivityResultPending.value = pending
                                },
                            )
                        }
                        if (showVesselGate) {
                            VesselBiometricGateScreen(
                                attempt = biometricAttempt,
                                error = biometricError,
                                onAuthenticationMessage = { biometricError = it },
                                onAuthenticated = {
                                    vesselsUnlocked = true
                                    showVesselGate = false
                                    selectedTab = SignalTab.CONTACTS
                                    activeConversation = pendingSecureConversation
                                    pendingSecureConversation = null
                                },
                                onRetry = {
                                    biometricError = null
                                    biometricAttempt++
                                },
                                onCancel = {
                                    showVesselGate = false
                                    pendingSecureConversation = null
                                    selectedTab = SignalTab.SIGNALS
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSmsRevision(enabled: Boolean, resumeRevision: Int): Int {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(context, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val handler = Handler(Looper.getMainLooper())
        val refresh = Runnable { revision++ }
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(refresh)
                handler.postDelayed(refresh, 450L)
            }
        }
        context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        context.contentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, observer)
        onDispose {
            handler.removeCallbacks(refresh)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    LaunchedEffect(enabled, resumeRevision) {
        if (enabled) revision++
    }
    return revision
}

private fun Intent.smsAddress(): String? {
    if (action != Intent.ACTION_SENDTO && action != Intent.ACTION_VIEW) return null
    return data?.schemeSpecificPart?.substringBefore('?')?.takeIf { it.isNotBlank() }
}

@Composable
private fun AbyssBackground(content: @Composable () -> Unit) {
    val lightTheme = LocalLightTheme.current
    val background = Void
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = if (lightTheme) {
                        listOf(Color(0xFFD6EAD9), background, Color(0xFFEAF4EC))
                    } else {
                        listOf(Color(0xFF102719), background, Color.Black)
                    },
                    center = Offset(250f, 180f),
                    radius = 1_500f,
                ),
            )
            .drawBehind {
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        (if (lightTheme) Color.Black else Color.White).copy(alpha = 0.018f),
                        Offset(0f, y),
                        Offset(size.width, y),
                        1f,
                    )
                    y += 8f
                }
            },
    ) {
        content()
    }
}

@Composable
private fun SignalDeck(
    selectedTab: SignalTab,
    onSelectedTabChange: (SignalTab) -> Unit,
    isDefaultSmsApp: Boolean,
    hasSmsPermissions: Boolean,
    smsRevision: Int,
    draftRevision: Int,
    permissionRevision: Int,
    appTheme: AppTheme,
    biometricGateEnabled: Boolean,
    interactionEnabled: Boolean,
    onThemeChange: (AppTheme) -> Unit,
    onBiometricGateEnabledChange: (Boolean) -> Unit,
    onRequestSmsSetup: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showContactSearch by rememberSaveable { mutableStateOf(false) }
    var showMessageSearch by rememberSaveable { mutableStateOf(false) }
    var messageSearchQuery by rememberSaveable { mutableStateOf("") }
    var blockCandidate by remember { mutableStateOf<Conversation?>(null) }
    var contactPermissionRevision by remember { mutableIntStateOf(0) }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        contactPermissionRevision++
        if (granted) showContactSearch = true
    }
    val hasContactsPermission = remember(contactPermissionRevision, permissionRevision) {
        ContactRepository.hasPermission(context)
    }
    val contactsState by produceState(
        initialValue = ContactsState(loading = hasContactsPermission),
        hasContactsPermission,
        contactPermissionRevision,
        permissionRevision,
    ) {
        value = if (!hasContactsPermission) {
            ContactsState()
        } else {
            withContext(Dispatchers.IO) {
                ContactRepository.loadPhoneContactsResult(context).fold(
                    onSuccess = { ContactsState(contacts = it) },
                    onFailure = { ContactsState(error = it.message ?: it.javaClass.simpleName) },
                )
            }
        }
    }
    val phoneContacts = contactsState.contacts
    var historyRetry by remember { mutableIntStateOf(0) }
    var controlsRevision by remember { mutableIntStateOf(0) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val realSmsReady = isDefaultSmsApp && hasSmsPermissions
    val blockedNumbers by produceState(
        initialValue = emptySet<String>(),
        realSmsReady,
        controlsRevision,
    ) {
        value = if (realSmsReady) {
            withContext(Dispatchers.IO) { ConversationControlsRepository.blockedNumbers(context) }
        } else {
            emptySet()
        }
    }
    val initialHistory = remember(realSmsReady) {
        if (realSmsReady) {
            ConversationIndexCache.load(context)?.live() ?: DeckHistory(loading = true)
        } else {
            DeckHistory()
        }
    }
    val unlabelledHistory by produceState(
        initialValue = initialHistory,
        realSmsReady,
        smsRevision,
        historyRetry,
    ) {
        value = if (!realSmsReady) {
            DeckHistory()
        } else {
            val cached = withContext(Dispatchers.IO) { ConversationIndexCache.load(context) }
            if (cached != null) value = cached.live()
            val refreshed = withContext(Dispatchers.IO) {
                val startedAt = SystemClock.elapsedRealtime()
                SmsRepository.loadConversationIndex(context, SecureRepository::isSecureBody).fold(
                    onSuccess = {
                        buildDeckHistory(context, it).also { history ->
                            val elapsed = SystemClock.elapsedRealtime() - startedAt
                            Log.i(
                                "EutherPingPerf",
                                "conversation-index loaded in $elapsed ms " +
                                    "(${history.signals.size} signals, ${history.vessels.size} vessels)",
                            )
                            PerformanceDiagnostics.record(
                                context,
                                event = "conversation_index",
                                durationMs = elapsed,
                                values = mapOf(
                                    "signals" to history.signals.size.toLong(),
                                    "vessels" to history.vessels.size.toLong(),
                                ),
                            )
                        }
                    },
                    onFailure = {
                        if (cached != null) {
                            cached.live().copy(loading = false, error = it.message ?: it.javaClass.simpleName)
                        } else {
                            DeckHistory(error = it.message ?: it.javaClass.simpleName)
                        }
                    },
                )
            }
            if (refreshed.error == null) {
                withContext(Dispatchers.IO) { ConversationIndexCache.save(context, refreshed.cached()) }
            }
            refreshed
        }
    }
    val history = remember(unlabelledHistory, phoneContacts, draftRevision, controlsRevision, blockedNumbers) {
        applyContactNames(unlabelledHistory, phoneContacts).let { named ->
            fun decorate(conversation: Conversation, secure: Boolean): Conversation {
                val address = conversation.smsAddress.orEmpty()
                val controls = ConversationControlsRepository.state(context, address, secure)
                return conversation.copy(
                    hasDraft = DraftRepository.hasDraft(context, address, secure),
                    pinned = controls.pinned,
                    archived = controls.archived,
                    blocked = ConversationControlsRepository.isBlocked(blockedNumbers, address),
                )
            }
            named.copy(
                signals = named.signals.map { decorate(it, false) }
                    .sortedByDescending(Conversation::pinned),
                vessels = named.vessels.map { decorate(it, true) }
                    .sortedByDescending(Conversation::pinned),
            )
        }
    }

    fun openContactSearch() {
        if (ContactRepository.hasPermission(context)) {
            showContactSearch = true
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    fun applyConversationControl(conversation: Conversation, action: ConversationControlAction) {
        val address = conversation.smsAddress ?: return
        val secure = conversation.transport == Transport.SECURE
        when (action) {
            ConversationControlAction.TOGGLE_PIN -> {
                ConversationControlsRepository.setPinned(context, address, secure, !conversation.pinned)
                controlsRevision++
            }
            ConversationControlAction.TOGGLE_ARCHIVE -> {
                ConversationControlsRepository.setArchived(context, address, secure, !conversation.archived)
                controlsRevision++
            }
            ConversationControlAction.TOGGLE_READ -> coroutineScope.launch {
                val changed = withContext(Dispatchers.IO) {
                    if (conversation.unread > 0) {
                        SmsRepository.markThreadRead(context, conversation.threadId)
                    } else {
                        SmsRepository.markThreadUnread(
                            context,
                            conversation.threadId,
                            secureLane = secure,
                        )
                    }
                }
                if (changed) historyRetry++ else Toast.makeText(
                    context,
                    "Android could not change the read state",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            ConversationControlAction.TOGGLE_BLOCK -> blockCandidate = conversation
        }
    }

    BackHandler(
        enabled = interactionEnabled && (showContactSearch || showMessageSearch || selectedTab != SignalTab.SIGNALS),
    ) {
        if (showMessageSearch) {
            showMessageSearch = false
            messageSearchQuery = ""
        } else if (showContactSearch) {
            showContactSearch = false
        } else {
            onSelectedTabChange(SignalTab.SIGNALS)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (!showContactSearch) {
                DeckNavigation(selectedTab, onSelected = onSelectedTabChange)
            }
        },
        modifier = Modifier.safeDrawingPadding(),
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            if (showMessageSearch) {
                val searchState by produceState(
                    initialValue = MessageSearchState(),
                    messageSearchQuery,
                    selectedTab,
                    realSmsReady,
                ) {
                    val query = messageSearchQuery.trim()
                    if (!realSmsReady || query.isBlank()) {
                        value = MessageSearchState()
                    } else {
                        value = MessageSearchState(loading = true)
                        delay(250)
                        value = withContext(Dispatchers.IO) {
                            SmsRepository.searchMessages(
                                context,
                                query,
                                secureLane = selectedTab == SignalTab.CONTACTS,
                                matchingAddresses = phoneContacts.asSequence()
                                    .filter { contact ->
                                        contact.name.contains(query, ignoreCase = true) ||
                                            contact.phoneNumber.contains(query, ignoreCase = true)
                                    }
                                    .map(PhoneContact::phoneNumber)
                                    .toSet(),
                            ).fold(
                                onSuccess = { MessageSearchState(hits = it) },
                                onFailure = { MessageSearchState(error = it.message ?: it.javaClass.simpleName) },
                            )
                        }
                    }
                }
                MessageSearchScreen(
                    query = messageSearchQuery,
                    onQueryChange = { messageSearchQuery = it },
                    secure = selectedTab == SignalTab.CONTACTS,
                    contacts = phoneContacts,
                    state = searchState,
                    onBack = {
                        showMessageSearch = false
                        messageSearchQuery = ""
                    },
                    onOpen = { hit ->
                        showMessageSearch = false
                        messageSearchQuery = ""
                        onOpenConversation(
                            if (selectedTab == SignalTab.CONTACTS) {
                                secureConversation(hit.address, hit.threadId, null)
                            } else {
                                cellConversation(hit.address, hit.threadId, null)
                            },
                        )
                    },
                )
            } else if (showContactSearch) {
                ContactSearchScreen(
                    contacts = phoneContacts,
                    loading = contactsState.loading,
                    error = contactsState.error,
                    secureMode = selectedTab == SignalTab.CONTACTS,
                    onBack = { showContactSearch = false },
                    onContactSelected = { contact ->
                        showContactSearch = false
                        onOpenConversation(
                            if (selectedTab == SignalTab.CONTACTS) {
                                secureConversation(contact.phoneNumber, null, contact.name)
                            } else {
                                cellConversation(contact.phoneNumber, null, contact.name)
                            },
                        )
                    },
                )
            } else {
                DeckHeader(
                    onSearch = if (selectedTab == SignalTab.SYSTEM) null else ::openContactSearch,
                    onRefresh = { historyRetry++ },
                    onOpenSystem = { onSelectedTabChange(SignalTab.SYSTEM) },
                    onSearchMessages = {
                        messageSearchQuery = ""
                        showMessageSearch = true
                    },
                    showArchived = showArchived,
                    onToggleArchived = { showArchived = !showArchived },
                    searchDescription = if (selectedTab == SignalTab.CONTACTS) {
                        "Find a vessel"
                    } else {
                        "Search contacts"
                    },
                )
                when (selectedTab) {
                    SignalTab.SIGNALS -> SignalsScreen(
                        isDefaultSmsApp = isDefaultSmsApp,
                        hasSmsPermissions = hasSmsPermissions,
                        conversations = history.signals,
                        historyLoading = history.loading,
                        historyError = history.error,
                        onRetryHistory = { historyRetry++ },
                        onRequestSmsSetup = onRequestSmsSetup,
                        onOpenConversation = onOpenConversation,
                        showArchived = showArchived,
                        onConversationAction = ::applyConversationControl,
                    )
                    SignalTab.CONTACTS -> VesselsScreen(
                        isDefaultSmsApp = isDefaultSmsApp,
                        hasSmsPermissions = hasSmsPermissions,
                        vessels = history.vessels,
                        historyLoading = history.loading,
                        historyError = history.error,
                        onRetryHistory = { historyRetry++ },
                        onRequestSmsSetup = onRequestSmsSetup,
                        onOpenConversation = onOpenConversation,
                        showArchived = showArchived,
                        onConversationAction = ::applyConversationControl,
                    )
                    SignalTab.SYSTEM -> SystemScreen(
                        isDefaultSmsApp = isDefaultSmsApp,
                        hasSmsPermissions = hasSmsPermissions,
                        appTheme = appTheme,
                        onThemeChange = onThemeChange,
                        biometricGateEnabled = biometricGateEnabled,
                        onBiometricGateEnabledChange = onBiometricGateEnabledChange,
                        onRequestSmsSetup = onRequestSmsSetup,
                    )
                }
            }
        }
    }
    blockCandidate?.let { conversation ->
        AlertDialog(
            onDismissRequest = { blockCandidate = null },
            containerColor = Deep,
            title = {
                Text(
                    if (conversation.blocked) "UNBLOCK NUMBER?" else "BLOCK NUMBER?",
                    color = Amber,
                    fontFamily = FontFamily.Monospace,
                )
            },
            text = {
                Text(
                    if (conversation.blocked) {
                        "Android will allow calls and carrier messages from ${conversation.name} again."
                    } else {
                        "Android will block calls and carrier messages from ${conversation.name}. Existing history is kept."
                    },
                    color = Mist,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    blockCandidate = null
                    coroutineScope.launch {
                        ConversationControlsRepository.setBlocked(
                            context,
                            conversation.smsAddress.orEmpty(),
                            !conversation.blocked,
                        ).onSuccess {
                            controlsRevision++
                        }.onFailure { error ->
                            Toast.makeText(
                                context,
                                "Could not update block list: ${error.message}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }) {
                    Text(if (conversation.blocked) "UNBLOCK" else "BLOCK", color = Amber)
                }
            },
            dismissButton = {
                TextButton(onClick = { blockCandidate = null }) { Text("CANCEL", color = Muted) }
            },
        )
    }
}

@Composable
private fun DeckHeader(
    onSearch: (() -> Unit)?,
    onRefresh: () -> Unit,
    onOpenSystem: () -> Unit,
    onSearchMessages: () -> Unit,
    showArchived: Boolean,
    onToggleArchived: () -> Unit,
    searchDescription: String,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .border(1.dp, Toxic.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            MiniSonar(modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "EUTHER//PING",
                color = Toxic,
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.8.sp,
            )
            Text(
                "ACOUSTIC MESSAGE TERMINAL ${BuildConfig.VERSION_NAME}",
                color = Toxic.copy(alpha = 0.48f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 0.7.sp,
            )
        }
        if (onSearch != null) {
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = searchDescription, tint = Toxic)
            }
        }
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Muted)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            if (onSearch != null) {
                DropdownMenuItem(
                    text = { Text("Search messages") },
                    onClick = {
                        menuExpanded = false
                        onSearchMessages()
                    },
                )
            }
            if (onSearch != null) {
                DropdownMenuItem(
                    text = { Text(if (showArchived) "Hide archived" else "Show archived") },
                    onClick = {
                        menuExpanded = false
                        onToggleArchived()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Refresh messages") },
                onClick = {
                    menuExpanded = false
                    onRefresh()
                },
            )
            DropdownMenuItem(
                text = { Text("System and privacy") },
                onClick = {
                    menuExpanded = false
                    onOpenSystem()
                },
            )
        }
    }
}

@Composable
private fun SignalsScreen(
    isDefaultSmsApp: Boolean,
    hasSmsPermissions: Boolean,
    conversations: List<Conversation>,
    historyLoading: Boolean,
    historyError: String?,
    onRetryHistory: () -> Unit,
    onRequestSmsSetup: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    showArchived: Boolean,
    onConversationAction: (Conversation, ConversationControlAction) -> Unit,
) {
    var showNewSignal by rememberSaveable { mutableStateOf(false) }
    val realSmsReady = isDefaultSmsApp && hasSmsPermissions
    val displayedConversations = if (realSmsReady) {
        conversations.filter { showArchived || !it.archived }
    } else {
        sampleConversations().filter { it.transport == Transport.SMS }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!realSmsReady) {
            item {
                SmsSetupBanner(
                    isDefaultSmsApp = isDefaultSmsApp,
                    onRequestSmsSetup = onRequestSmsSetup,
                )
            }
        } else {
            item {
                NewCellSignalButton(onClick = { showNewSignal = true })
            }
            if (historyLoading || historyError != null) {
                item {
                    HistoryStatusCard(
                        loading = historyLoading,
                        error = historyError,
                        contentAvailable = conversations.isNotEmpty(),
                        onRetry = onRetryHistory,
                    )
                }
            }
        }
        item {
            SonarHero(
                smsReady = realSmsReady,
                activeSignals = displayedConversations.size,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "ACTIVE SIGNALS",
                    color = Mist,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${displayedConversations.size} CELL",
                    color = Amber,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            }
        }
        items(displayedConversations, key = { it.id }) { conversation ->
            ConversationRow(
                conversation,
                onClick = { onOpenConversation(conversation) },
                onAction = { onConversationAction(conversation, it) },
            )
        }
    }
    if (showNewSignal) {
        NewSmsDialog(
            onDismiss = { showNewSignal = false },
            onOpen = { recipients ->
                showNewSignal = false
                onOpenConversation(cellConversation(recipients, null))
            },
        )
    }
}

@Composable
private fun SmsSetupBanner(isDefaultSmsApp: Boolean, onRequestSmsSetup: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Amber.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, Amber.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(15.dp)) {
            Text(
                if (isDefaultSmsApp) "SMS PERMISSIONS REQUIRED" else "CONNECT ANDROID SMS ARRAY",
                color = Amber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
            Text(
                if (isDefaultSmsApp) {
                    "EutherPing has the SMS role. Grant access to read, receive and send your messages."
                } else {
                    "Android must make EutherPing the default SMS app before sensitive SMS access can be requested."
                },
                color = Mist,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 7.dp, bottom = 11.dp),
            )
            Button(
                onClick = onRequestSmsSetup,
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Void),
            ) {
                Text(
                    if (isDefaultSmsApp) "GRANT SMS ACCESS" else "MAKE DEFAULT SMS APP",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun HistoryStatusCard(
    loading: Boolean,
    error: String?,
    contentAvailable: Boolean,
    onRetry: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Amber.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, Amber.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                when {
                    loading && contentAvailable -> "CACHED SIGNALS READY // SYNCING ANDROID MESSAGE ARRAY…"
                    loading -> "READING ANDROID MESSAGE ARRAY…"
                    else -> "MESSAGE ARRAY COULD NOT BE READ"
                },
                color = Amber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
            if (error != null) {
                Text(
                    error,
                    color = Mist,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 7.dp),
                )
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Void),
                    modifier = Modifier.padding(top = 9.dp),
                ) {
                    Text("RETRY", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NewCellSignalButton(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Amber.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, Amber.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(15.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Amber)
            Spacer(Modifier.width(9.dp))
            Text(
                "NEW CELL SIGNAL",
                color = Amber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
            )
        }
    }
}

@Composable
private fun NewSmsDialog(onDismiss: () -> Unit, onOpen: (List<String>) -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    val recipients = remember(address) {
        address.split(Regex("[,;\\n]+"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Deep,
        title = {
            Text("NEW CELL SIGNAL", color = Amber, fontFamily = FontFamily.Monospace)
        },
        text = {
            OutlinedTextField(
                value = address,
                onValueChange = {
                    address = it.filter { character ->
                        character.isDigit() || character in "+*# ,;\n"
                    }
                },
                label = { Text("Phone number(s), comma separated") },
                supportingText = {
                    if (recipients.size > 1) Text("${recipients.size} recipients // group MMS")
                },
                singleLine = false,
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber,
                    focusedTextColor = Mist,
                    unfocusedTextColor = Mist,
                    cursorColor = Amber,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onOpen(recipients) }, enabled = recipients.isNotEmpty()) {
                Text("OPEN CHANNEL", color = Amber, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Muted, fontFamily = FontFamily.Monospace)
            }
        },
    )
}

@Composable
private fun SonarHero(
    smsReady: Boolean,
    activeSignals: Int,
) {
    val lightTheme = LocalLightTheme.current
    val heroToxic = if (lightTheme) Color(0xFF8BFF62) else Toxic
    val heroAmber = if (lightTheme) Color(0xFFFF9D32) else Amber
    val heroMist = if (lightTheme) Color(0xFFD7F7DC) else Mist
    val heroMuted = if (lightTheme) Color(0xFF8AA08F) else Muted
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1.75f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Toxic.copy(alpha = 0.27f)),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(R.drawable.sonar_abyss),
                contentDescription = "EutherPing sonar field",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.7f),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xD9000503)),
                        ),
                    ),
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(heroToxic)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "SONAR ARRAY ONLINE",
                        color = heroToxic,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                    )
                }
                Text(
                    if (smsReady) {
                        "$activeSignals active ${if (activeSignals == 1) "signal" else "signals"} in the log"
                    } else {
                        "Secure Ping is ready to arm"
                    },
                    color = heroMist,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    if (smsReady) {
                        "Ordinary carrier SMS and MMS stay here. Secure identities and encrypted pings live under Vessels."
                    } else {
                        "Enable EutherPing as the SMS app to pair and exchange encrypted SMS capsules."
                    },
                    color = heroMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            StatusChip(
                text = if (smsReady) "CELL ARRAY" else "ARRAY STANDBY",
                color = heroAmber,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
    onAction: (ConversationControlAction) -> Unit,
) {
    val accent = if (conversation.transport == Transport.SECURE) Violet else Amber
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true },
            ),
            shape = RoundedCornerShape(17.dp),
            colors = CardDefaults.cardColors(containerColor = Panel),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
        ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VesselAvatar(conversation.initials, accent)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        conversation.name,
                        color = Mist,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        conversation.time,
                        color = if (conversation.unread > 0) accent else Muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (conversation.hasDraft) "Draft · ${conversation.preview}" else conversation.preview,
                        color = if (conversation.hasDraft || conversation.unread > 0) accent else Muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (conversation.unread > 0) {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(21.dp)
                                .background(accent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                conversation.unread.toString(),
                                color = Void,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusChip(conversation.transport.label, accent)
                    if (conversation.pinned) {
                        Text("  //  PINNED", color = Toxic, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    }
                    if (conversation.archived) {
                        Text("  //  ARCHIVED", color = Violet, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    }
                    if (conversation.blocked) {
                        Text("  //  BLOCKED", color = Amber, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    }
                    Text(
                        "  //  ${conversation.distance}",
                        color = Muted.copy(alpha = 0.75f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                    )
                }
            }
        }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            buildList {
                add((if (conversation.pinned) "Unpin" else "Pin") to ConversationControlAction.TOGGLE_PIN)
                add((if (conversation.archived) "Unarchive" else "Archive") to ConversationControlAction.TOGGLE_ARCHIVE)
                add(
                    (if (conversation.unread > 0) "Mark as read" else "Mark as unread") to
                        ConversationControlAction.TOGGLE_READ,
                )
                if (conversation.recipients.size <= 1) {
                    add(
                        (if (conversation.blocked) "Unblock number" else "Block number") to
                            ConversationControlAction.TOGGLE_BLOCK,
                    )
                }
            }.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        menuExpanded = false
                        onAction(action)
                    },
                )
            }
        }
    }
}

@Composable
private fun ConversationDeck(
    conversation: Conversation,
    smsRevision: Int,
    onBack: () -> Unit,
    onTrustedActivityResultPendingChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var secureRevision by remember(conversation.smsAddress) { mutableIntStateOf(0) }
    var attachmentRevision by remember(conversation.smsAddress) { mutableIntStateOf(0) }
    var attachmentBusy by remember(conversation.smsAddress) { mutableStateOf(false) }
    var selectedMessage by remember(conversation.id) { mutableStateOf<DemoMessage?>(null) }
    var selectedImageAction by remember(conversation.id) { mutableStateOf<ImageActionTarget?>(null) }
    var pendingImageSave by remember(conversation.id) { mutableStateOf<ImageActionTarget?>(null) }
    var forwardingMessage by remember(conversation.id) { mutableStateOf<DemoMessage?>(null) }
    var deleteMessageCandidate by remember(conversation.id) { mutableStateOf<DemoMessage?>(null) }
    var showDeleteConversation by remember(conversation.id) { mutableStateOf(false) }
    var showSecureResetConfirmation by remember(conversation.id) { mutableStateOf(false) }
    var conversationSearchVisible by rememberSaveable(conversation.id) { mutableStateOf(false) }
    var conversationSearchQuery by rememberSaveable(conversation.id) { mutableStateOf("") }
    var messageLimit by remember(conversation.id) { mutableIntStateOf(INITIAL_CONVERSATION_MESSAGES) }
    var loadedMessageLimit by remember(conversation.id) { mutableIntStateOf(0) }
    var messagePageLoading by remember(conversation.id) { mutableStateOf(false) }
    val lateMmsAttachments = remember(conversation.id) {
        mutableStateMapOf<Long, CarrierMmsAttachment>()
    }
    val demoMessages = remember(conversation.id) {
        mutableStateListOf(
            DemoMessage(1, "Can you still see the harbor lights?", false, "22:04", conversation.transport),
            DemoMessage(2, "Barely. The fog is rolling in.", true, "22:05", conversation.transport),
            DemoMessage(3, "Ping me when you reach the north pier.", false, "22:06", conversation.transport),
        )
    }
    val isRealSms = conversation.smsAddress != null
    var resolvedThreadId by remember(conversation.id) { mutableStateOf(conversation.threadId) }
    val secureLane = conversation.transport == Transport.SECURE
    val securePeer = remember(conversation.smsAddress, smsRevision, secureRevision) {
        if (isRealSms && secureLane) {
            SecureRepository.peer(context, conversation.smsAddress.orEmpty())
        } else {
            null
        }
    }
    val imageSavePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { uri ->
        onTrustedActivityResultPendingChange(false)
        val target = pendingImageSave
        pendingImageSave = null
        if (uri != null && target != null) {
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    when (target) {
                        is ImageActionTarget.Secure -> SecureAttachmentRepository.saveDownloadedImage(
                            context,
                            target.descriptor,
                            uri,
                        )
                        is ImageActionTarget.Carrier -> CarrierMmsRepository.saveStoredImage(
                            context,
                            target.attachment,
                            uri,
                        )
                    }
                }
                result.onSuccess {
                    Toast.makeText(context, "Image saved", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(context, "Could not save image: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val realMessagePage by produceState(
        initialValue = LoadedConversationMessages(),
        conversation.smsAddress,
        smsRevision,
        secureRevision,
        attachmentRevision,
        messageLimit,
    ) {
        messagePageLoading = true
        val requestedLimit = messageLimit
        val effectiveThreadId = if (isRealSms) {
            withContext(Dispatchers.IO) {
                SmsRepository.resolveConversationThreadId(
                    context,
                    resolvedThreadId,
                    conversation.smsAddress.orEmpty(),
                )
            }
        } else {
            resolvedThreadId
        }
        if (effectiveThreadId != null && effectiveThreadId != resolvedThreadId) {
            resolvedThreadId = effectiveThreadId
        }
        val cacheKey = ConversationPageCacheKey(
            threadId = effectiveThreadId,
            address = conversation.smsAddress.orEmpty(),
            secureLane = secureLane,
            smsRevision = smsRevision,
            secureRevision = secureRevision,
            attachmentRevision = attachmentRevision,
            limit = requestedLimit,
        )
        ConversationPageMemoryCache.get(cacheKey)?.let { cached ->
            value = cached
            loadedMessageLimit = requestedLimit
            Log.i(
                "EutherPingPerf",
                "conversation memory cache hit (${cached.messages.size} visible, limit=$requestedLimit)",
            )
            PerformanceDiagnostics.record(
                context,
                event = "conversation_cache_hit",
                durationMs = 0L,
                values = mapOf("visible" to cached.messages.size.toLong(), "limit" to requestedLimit.toLong()),
            )
        }
        value = if (isRealSms) {
            withContext(Dispatchers.IO) {
                val startedAt = SystemClock.elapsedRealtime()
                SmsRepository.loadMessagePage(
                    context = context,
                    threadId = effectiveThreadId,
                    address = conversation.smsAddress.orEmpty(),
                    limit = requestedLimit,
                    secureLane = secureLane,
                ).getOrElse { error ->
                    Log.e("EutherPingPerf", "Conversation load failed", error)
                    return@withContext LoadedConversationMessages()
                }.let { page ->
                    LoadedConversationMessages(
                        hasOlder = page.hasOlder,
                        messages = page.messages.mapNotNull { message ->
                            val decoded = if (message.isMms) null else SecureRepository.decodeForDisplay(
                                context = context,
                                address = conversation.smsAddress.orEmpty(),
                                body = message.body,
                                incoming = message.incoming,
                            )
                            if (secureLane != (decoded?.isSecure == true)) return@mapNotNull null
                            DemoMessage(
                                id = message.id,
                                text = decoded?.text ?: message.body,
                                outgoing = !message.incoming,
                                time = formatMessageTime(message.timestamp),
                                transport = conversation.transport,
                                attachment = decoded?.attachment,
                                carrierMmsAttachment = message.mmsAttachment,
                                wireBody = message.body,
                                isMms = message.isMms,
                                deliveryState = message.deliveryState(),
                                subscriptionId = message.subscriptionId,
                            )
                        },
                    ).also {
                        val elapsed = SystemClock.elapsedRealtime() - startedAt
                        Log.i(
                            "EutherPingPerf",
                            "conversation loaded in $elapsed ms " +
                                "(${it.messages.size} visible, limit=$requestedLimit)",
                        )
                        PerformanceDiagnostics.record(
                            context,
                            event = "conversation_page",
                            durationMs = elapsed,
                            values = mapOf(
                                "visible" to it.messages.size.toLong(),
                                "limit" to requestedLimit.toLong(),
                                "secure" to if (secureLane) 1L else 0L,
                            ),
                        )
                    }
                }
            }
        } else {
            LoadedConversationMessages()
        }
        ConversationPageMemoryCache.put(cacheKey, value)
        loadedMessageLimit = requestedLimit
        messagePageLoading = false
    }
    LaunchedEffect(realMessagePage.messages, smsRevision) {
        val unresolved = realMessagePage.messages
            .filter { it.isMms && it.carrierMmsAttachment == null && it.id !in lateMmsAttachments }
            .mapTo(mutableSetOf()) { it.id }
        repeat(6) { attempt ->
            if (unresolved.isEmpty()) return@LaunchedEffect
            if (attempt > 0) delay(400L shl (attempt - 1))
            val resolved = withContext(Dispatchers.IO) {
                unresolved.mapNotNull { messageId ->
                    SmsRepository.loadMmsAttachment(context, messageId)?.let { messageId to it }
                }
            }
            resolved.forEach { (messageId, attachment) ->
                lateMmsAttachments[messageId] = attachment
                unresolved.remove(messageId)
            }
        }
    }
    val messages = if (isRealSms) {
        realMessagePage.messages.map { message ->
            val lateAttachment = lateMmsAttachments[message.id]
            if (message.isMms && message.carrierMmsAttachment == null && lateAttachment != null) {
                message.copy(carrierMmsAttachment = lateAttachment)
            } else {
                message
            }
        }
    } else {
        demoMessages
    }
    val carrierRecipients = remember(conversation.id, conversation.recipients) {
        conversation.recipients.ifEmpty { listOfNotNull(conversation.smsAddress) }
    }
    val carrierConversationKey = remember(conversation.id, resolvedThreadId, carrierRecipients) {
        CarrierSubscriptionRepository.conversationKey(resolvedThreadId, carrierRecipients)
    }
    val carrierPermissionReady = SmsRepository.hasCarrierIdentityPermissions(context)
    val carrierSubscriptions = remember(conversation.id, smsRevision, carrierPermissionReady) {
        CarrierSubscriptionRepository.active(context)
    }
    var selectedSubscriptionId by rememberSaveable(conversation.id) {
        mutableStateOf(CarrierSubscriptionRepository.selected(context, carrierConversationKey))
    }
    LaunchedEffect(messages, carrierSubscriptions, carrierConversationKey) {
        if (selectedSubscriptionId != null && carrierSubscriptions.isNotEmpty() &&
            carrierSubscriptions.none { it.id == selectedSubscriptionId }
        ) {
            selectedSubscriptionId = null
        }
        if (selectedSubscriptionId == null) {
            selectedSubscriptionId = CarrierSubscriptionRepository.selected(
                context,
                carrierConversationKey,
                messages.lastOrNull()?.subscriptionId,
            )
        } else {
            CarrierSubscriptionRepository.remember(
                context,
                carrierConversationKey,
                checkNotNull(selectedSubscriptionId),
            )
        }
    }
    fun selectSubscription(subscriptionId: Int) {
        selectedSubscriptionId = subscriptionId
        CarrierSubscriptionRepository.remember(context, carrierConversationKey, subscriptionId)
        carrierRecipients.forEach { recipient ->
            CarrierSubscriptionRepository.remember(
                context,
                CarrierSubscriptionRepository.conversationKey(null, listOf(recipient)),
                subscriptionId,
            )
        }
    }
    val displayedMessages = remember(messages, conversationSearchQuery) {
        val query = conversationSearchQuery.trim()
        if (query.isBlank()) messages else messages.filter { message ->
            message.text.contains(query, ignoreCase = true)
        }
    }
    val messageListState = rememberLazyListState()
    val isNearLatestMessage by remember(messageListState) {
        derivedStateOf {
            messageListState.firstVisibleItemIndex <= 1
        }
    }
    var positionedAtLatestMessage by remember(conversation.id) { mutableStateOf(false) }
    var observedLatestMessageId by remember(conversation.id) { mutableStateOf<Long?>(null) }
    var awaitingSentMessage by remember(conversation.id) { mutableStateOf(false) }
    var explicitLatestScrollRequest by remember(conversation.id) { mutableIntStateOf(0) }

    LaunchedEffect(displayedMessages.lastOrNull()?.id, conversationSearchQuery) {
        if (conversationSearchQuery.isNotBlank()) return@LaunchedEffect
        val latest = displayedMessages.lastOrNull() ?: return@LaunchedEffect
        val latestChanged = observedLatestMessageId != latest.id
        val sentMessageArrived = latestChanged && awaitingSentMessage && latest.outgoing
        if (!positionedAtLatestMessage || isNearLatestMessage || sentMessageArrived) {
            messageListState.animateScrollToItem(0)
            positionedAtLatestMessage = true
        }
        if (latestChanged) {
            observedLatestMessageId = latest.id
            if (sentMessageArrived) awaitingSentMessage = false
        }
    }

    LaunchedEffect(explicitLatestScrollRequest) {
        if (explicitLatestScrollRequest > 0 && conversationSearchQuery.isBlank() && displayedMessages.isNotEmpty()) {
            messageListState.animateScrollToItem(0)
            positionedAtLatestMessage = true
        }
    }
    var composerTransport by rememberSaveable(conversation.id) {
        mutableStateOf(conversation.transport)
    }
    val storedDraft = remember(conversation.id, conversation.smsAddress, secureLane) {
        DraftRepository.load(context, conversation.smsAddress.orEmpty(), secureLane)
    }
    var draft by rememberSaveable(conversation.id) { mutableStateOf(storedDraft.text) }
    var pendingCarrierMmsUri by rememberSaveable(conversation.id) {
        mutableStateOf(storedDraft.carrierImageUri)
    }
    LaunchedEffect(draft, pendingCarrierMmsUri, conversation.id) {
        if (!isRealSms) return@LaunchedEffect
        delay(350)
        withContext(Dispatchers.IO) {
            DraftRepository.save(
                context,
                conversation.smsAddress.orEmpty(),
                secureLane,
                draft,
                pendingCarrierMmsUri,
            )
        }
    }
    fun clearStoredDraft() {
        DraftRepository.clear(context, conversation.smsAddress.orEmpty(), secureLane)
    }
    val focusManager = LocalFocusManager.current
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onTrustedActivityResultPendingChange(false)
        if (uri != null && isRealSms && (!secureLane || securePeer?.canEncrypt == true)) {
            if (!secureLane) {
                pendingCarrierMmsUri?.let { previous ->
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            previous,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                pendingCarrierMmsUri = uri
                return@rememberLauncherForActivityResult
            }
            attachmentBusy = true
            coroutineScope.launch {
                val result = try {
                    withContext(Dispatchers.IO) {
                        SecureAttachmentRepository.prepareOutgoing(
                            context,
                            conversation.smsAddress.orEmpty(),
                            uri,
                        ).flatMap { prepared ->
                            SmsRepository.sendText(
                                context,
                                conversation.smsAddress.orEmpty(),
                                prepared.wireBody,
                                selectedSubscriptionId,
                            ).map {
                                "Encrypted attachment offered over ${prepared.descriptor.transportLabel.lowercase()}"
                            }
                        }
                    }
                } catch (error: Throwable) {
                    Result.failure(error)
                } finally {
                    attachmentBusy = false
                }
                result.onSuccess { successMessage ->
                    AppSounds.play(context, AppSound.SECURE_SEALED)
                    awaitingSentMessage = true
                    explicitLatestScrollRequest++
                    attachmentRevision++
                    Toast.makeText(
                        context,
                        successMessage,
                        Toast.LENGTH_LONG,
                    ).show()
                }.onFailure {
                    AppSounds.play(context, AppSound.TERMINAL_ERROR)
                    Log.e("EutherPingAttachment", "Attachment send failed", it)
                    Toast.makeText(
                        context,
                        if (secureLane) {
                            "Attachment failed: ${it.message}"
                        } else {
                            "Carrier MMS failed: ${it.message}"
                        },
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(resolvedThreadId) {
        if (isRealSms) SmsRepository.markThreadRead(context, resolvedThreadId)
    }

    fun sendMessage() {
        if (attachmentBusy) return
        if (!secureLane && carrierSubscriptions.size > 1 && selectedSubscriptionId == null) {
            Toast.makeText(context, "Select a SIM before sending", Toast.LENGTH_SHORT).show()
            return
        }
        val text = draft.trim()
        val carrierMmsUri = pendingCarrierMmsUri
        if (!secureLane && isRealSms && carrierMmsUri != null) {
            attachmentBusy = true
            coroutineScope.launch {
                val result = try {
                    withContext(Dispatchers.IO) {
                        CarrierMmsRepository.sendImage(
                            context,
                            carrierRecipients,
                            text,
                            carrierMmsUri,
                            selectedSubscriptionId,
                        )
                    }
                } catch (error: Throwable) {
                    Result.failure(error)
                } finally {
                    attachmentBusy = false
                }
                result.onSuccess { messageUri ->
                    AppSounds.play(context, AppSound.SIGNAL_SENT)
                    awaitingSentMessage = true
                    explicitLatestScrollRequest++
                    resolvedThreadId = SmsRepository.threadIdForMessage(context, messageUri) ?: resolvedThreadId
                    clearStoredDraft()
                    draft = ""
                    pendingCarrierMmsUri = null
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            carrierMmsUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    attachmentRevision++
                    focusManager.clearFocus()
                    Toast.makeText(context, "Carrier MMS queued", Toast.LENGTH_LONG).show()
                }.onFailure {
                    AppSounds.play(context, AppSound.TERMINAL_ERROR)
                    Log.e("EutherPingAttachment", "Carrier MMS send failed", it)
                    Toast.makeText(
                        context,
                        "Carrier MMS failed: ${it.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        } else if (text.isNotEmpty()) {
            if (isRealSms) {
                val automaticTextMms = !secureLane &&
                    (carrierRecipients.size > 1 || SmsRepository.smsPartCount(context, text) > 1)
                val outgoing = if (secureLane) {
                    SecureRepository.encryptMessage(context, conversation.smsAddress.orEmpty(), text)
                } else {
                    Result.success(text)
                }
                outgoing.flatMap { wireBody ->
                    if (automaticTextMms) {
                        CarrierMmsRepository.sendText(
                            context,
                            carrierRecipients,
                            wireBody,
                            selectedSubscriptionId,
                        )
                    } else {
                        SmsRepository.sendText(
                            context,
                            conversation.smsAddress.orEmpty(),
                            wireBody,
                            selectedSubscriptionId,
                        )
                    }
                }
                    .onSuccess { messageUri ->
                        AppSounds.play(
                            context,
                            if (secureLane) AppSound.SECURE_SEALED else AppSound.SIGNAL_SENT,
                        )
                        awaitingSentMessage = true
                        explicitLatestScrollRequest++
                        resolvedThreadId = SmsRepository.threadIdForMessage(context, messageUri) ?: resolvedThreadId
                        clearStoredDraft()
                        draft = ""
                        focusManager.clearFocus()
                        Toast.makeText(
                            context,
                            if (secureLane) {
                                "Encrypted Secure Ping queued"
                            } else if (carrierRecipients.size > 1) {
                                "Group MMS queued for ${carrierRecipients.size} recipients"
                            } else if (automaticTextMms) {
                                "Long message queued as MMS"
                            } else {
                                "SMS queued"
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    .onFailure {
                        AppSounds.play(context, AppSound.TERMINAL_ERROR)
                        Log.e("EutherPingSms", "Message send failed", it)
                        Toast.makeText(context, "Send failed: ${it.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                awaitingSentMessage = true
                explicitLatestScrollRequest++
                demoMessages += DemoMessage(
                    id = (demoMessages.maxOfOrNull { it.id } ?: 0) + 1,
                    text = text,
                    outgoing = true,
                    time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    transport = composerTransport,
                )
                draft = ""
                focusManager.clearFocus()
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ConversationHeader(
                conversation = conversation,
                securePeerState = securePeer?.state,
                searchVisible = conversationSearchVisible,
                searchQuery = conversationSearchQuery,
                onSearchQueryChange = { conversationSearchQuery = it },
                onToggleSearch = {
                    conversationSearchVisible = !conversationSearchVisible
                    if (conversationSearchVisible) {
                        messageLimit = maxOf(messageLimit, 200)
                    } else {
                        conversationSearchQuery = ""
                    }
                },
                onDeleteConversation = { showDeleteConversation = true },
                onBack = onBack,
            )
        },
        bottomBar = {
            Composer(
                draft = draft,
                onDraftChange = { draft = it },
                transport = composerTransport,
                onTransportChange = { composerTransport = it },
                secureOnly = secureLane,
                enabled = !secureLane || securePeer?.canEncrypt == true,
                attachmentBusy = attachmentBusy,
                attachmentEnabled = isRealSms,
                carrierMmsUri = pendingCarrierMmsUri.takeUnless { secureLane },
                carrierSubscriptions = carrierSubscriptions,
                selectedSubscriptionId = selectedSubscriptionId,
                onSelectSubscription = ::selectSubscription,
                groupRecipientCount = carrierRecipients.size,
                onRemoveCarrierMms = {
                    pendingCarrierMmsUri?.let { uri ->
                        runCatching {
                            context.contentResolver.releasePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                    }
                    pendingCarrierMmsUri = null
                },
                onAttachment = {
                    onTrustedActivityResultPendingChange(true)
                    runCatching {
                        attachmentPicker.launch(arrayOf(if (secureLane) "*/*" else "image/*"))
                    }.onFailure { error ->
                        onTrustedActivityResultPendingChange(false)
                        Toast.makeText(
                            context,
                            "Could not open attachment picker: ${error.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                onSend = ::sendMessage,
            )
        },
        modifier = Modifier.safeDrawingPadding(),
    ) { insets ->
        Box(modifier = Modifier.fillMaxSize().padding(insets)) {
            Image(
                painter = painterResource(R.drawable.sonar_abyss),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.09f),
            )
            LazyColumn(
                state = messageListState,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
            ) {
                if (isRealSms && secureLane) {
                    item {
                        SecurePairingCard(
                            peerState = securePeer?.state ?: SecurePeerState.NONE,
                            protocol = securePeer?.protocol ?: SecureProtocol.LEGACY_EP1,
                            safetyNumber = SecureRepository.safetyNumber(
                                context,
                                conversation.smsAddress.orEmpty(),
                            ),
                            onInvite = {
                                SecureRepository.createInvitation(
                                    context,
                                    conversation.smsAddress.orEmpty(),
                                ).flatMap { SmsRepository.sendText(context, conversation.smsAddress.orEmpty(), it) }
                                    .onSuccess {
                                        AppSounds.play(context, AppSound.SECURE_SEALED)
                                        secureRevision++
                                        Toast.makeText(context, "Secure invitation sent", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure {
                                        AppSounds.play(context, AppSound.TERMINAL_ERROR)
                                        Toast.makeText(context, "Invite failed: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                            },
                            onUpgrade = {
                                SecureRepository.createInvitation(
                                    context,
                                    conversation.smsAddress.orEmpty(),
                                ).flatMap { SmsRepository.sendText(context, conversation.smsAddress.orEmpty(), it) }
                                    .onSuccess {
                                        AppSounds.play(context, AppSound.SECURE_SEALED)
                                        secureRevision++
                                        Toast.makeText(context, "EP3 upgrade invitation sent", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure {
                                        AppSounds.play(context, AppSound.TERMINAL_ERROR)
                                        Toast.makeText(context, "EP3 upgrade failed: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                            },
                            onAccept = {
                                SecureRepository.acceptInvitation(
                                    context,
                                    conversation.smsAddress.orEmpty(),
                                ).flatMap { SmsRepository.sendText(context, conversation.smsAddress.orEmpty(), it) }
                                    .onSuccess {
                                        AppSounds.play(context, AppSound.SECURE_VERIFIED)
                                        composerTransport = Transport.SECURE
                                        secureRevision++
                                        Toast.makeText(context, "Secure channel accepted", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure {
                                        AppSounds.play(context, AppSound.TERMINAL_ERROR)
                                        Toast.makeText(context, "Accept failed: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                            },
                            onVerify = {
                                SecureRepository.markVerified(context, conversation.smsAddress.orEmpty())
                                AppSounds.play(context, AppSound.SECURE_VERIFIED)
                                secureRevision++
                            },
                            onAcceptIdentityChange = {
                                if (securePeer?.pendingRatchetPublication != null) {
                                    AppSounds.play(context, AppSound.IDENTITY_WARNING)
                                    showSecureResetConfirmation = true
                                } else {
                                    SecureRepository.acceptIdentityChange(
                                        context,
                                        conversation.smsAddress.orEmpty(),
                                    ).flatMap { response ->
                                        if (response == null) {
                                            Result.success(Unit)
                                        } else {
                                            SmsRepository.sendText(
                                                context,
                                                conversation.smsAddress.orEmpty(),
                                                response,
                                            )
                                        }
                                    }.onSuccess {
                                        secureRevision++
                                        Toast.makeText(
                                            context,
                                            "New identity ready for safety-code verification",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }.onFailure {
                                        Toast.makeText(
                                            context,
                                            "Identity review failed: ${it.message}",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            },
                            onRejectIdentityChange = {
                                SecureRepository.rejectIdentityChange(context, conversation.smsAddress.orEmpty())
                                secureRevision++
                            },
                        )
                    }
                }
                if (conversationSearchQuery.isNotBlank() && displayedMessages.isEmpty()) {
                    item(key = "no-search-results") {
                        Text(
                            "NO MATCHES IN THE LOADED MESSAGE ARRAY",
                            color = Muted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        )
                    }
                }
                items(displayedMessages.asReversed(), key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        attachmentRevision = attachmentRevision + smsRevision,
                        secureAddress = conversation.smsAddress.takeIf { secureLane },
                        onLongPress = { selectedMessage = message },
                        onSecureImageLongPress = { selectedImageAction = ImageActionTarget.Secure(it) },
                        onCarrierImageLongPress = { selectedImageAction = ImageActionTarget.Carrier(it) },
                        onAttachment = { descriptor ->
                            if (!descriptor.incoming) {
                                SecureAttachmentRepository.openDownloaded(context, descriptor)
                                    .onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            "Could not open encrypted attachment: ${error.message}",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                            } else {
                                attachmentBusy = true
                                coroutineScope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        SecureAttachmentRepository.downloadIncoming(
                                            context,
                                            conversation.smsAddress.orEmpty(),
                                            descriptor,
                                        )
                                    }
                                    attachmentBusy = false
                                    result.onSuccess {
                                        attachmentRevision++
                                        SecureAttachmentRepository.openDownloaded(context, descriptor)
                                            .onFailure { openError ->
                                                Toast.makeText(
                                                    context,
                                                    "Downloaded, but no app could open it: ${openError.message}",
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                    }.onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            "Secure attachment transfer failed: ${error.message}",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            }
                        },
                    )
                }
                if (isRealSms && realMessagePage.hasOlder) {
                    item(key = "automatic-older-page") {
                        LaunchedEffect(loadedMessageLimit, realMessagePage.messages.size) {
                            if (!messagePageLoading && loadedMessageLimit == messageLimit) {
                                messageLimit += CONVERSATION_PAGE_INCREMENT
                            }
                        }
                        Text(
                            if (messagePageLoading) "LOADING OLDER MESSAGES…" else "SCROLL FOR OLDER MESSAGES",
                            color = Muted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        )
                    }
                }
                item {
                    Text(
                        if (secureLane) {
                            "VESSEL CHANNEL // ENCRYPTED TEXT OVER SMS // FILES OVER WIFI OR BLUETOOTH"
                        } else if (isRealSms) {
                            "ANDROID TELEPHONY // ORDINARY CARRIER SMS + MMS // NOT ENCRYPTED"
                        } else {
                            "SECURE CHANNEL // VISUAL PROTOCOL PREVIEW"
                        },
                        color = if (secureLane) Violet.copy(alpha = 0.7f) else Amber.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 0.7.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
        }
    }

    selectedMessage?.let { message ->
        MessageActionsDialog(
            message = message,
            failureDescription = if (message.deliveryState == MessageDeliveryState.FAILED) {
                SmsRepository.failureDescription(context, message.id, message.isMms)
            } else {
                null
            },
            onDismiss = { selectedMessage = null },
            onCopy = {
                clipboard.setText(AnnotatedString(message.text))
                selectedMessage = null
                Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
            },
            onForward = {
                selectedMessage = null
                forwardingMessage = message
            },
            onDelete = {
                selectedMessage = null
                deleteMessageCandidate = message
            },
            onRetry = if (message.outgoing && message.deliveryState == MessageDeliveryState.FAILED) {
                {
                    selectedMessage = null
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            if (message.isMms) {
                                CarrierMmsRepository.retryFailedMms(context, message.id).map { Unit }
                            } else {
                                SmsRepository.retryFailedText(context, message.id)
                            }
                        }
                        result.onSuccess {
                            attachmentRevision++
                            Toast.makeText(context, "Message queued again", Toast.LENGTH_SHORT).show()
                        }.onFailure { error ->
                            Toast.makeText(context, "Retry failed: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                null
            },
        )
    }
    selectedImageAction?.let { target ->
        ImageActionsDialog(
            secure = target is ImageActionTarget.Secure,
            onDismiss = { selectedImageAction = null },
            onOpen = {
                selectedImageAction = null
                val result = when (target) {
                    is ImageActionTarget.Secure -> SecureAttachmentRepository.openDownloaded(
                        context,
                        target.descriptor,
                    )
                    is ImageActionTarget.Carrier -> CarrierMmsRepository.openStoredImage(
                        context,
                        target.attachment,
                    )
                }
                result.onFailure { error ->
                    Toast.makeText(context, "Could not open image: ${error.message}", Toast.LENGTH_LONG).show()
                }
            },
            onSave = {
                pendingImageSave = target
                selectedImageAction = null
                onTrustedActivityResultPendingChange(true)
                runCatching {
                    imageSavePicker.launch(safeImageName(target.suggestedName))
                }.onFailure { error ->
                    onTrustedActivityResultPendingChange(false)
                    pendingImageSave = null
                    Toast.makeText(
                        context,
                        "Could not open image saver: ${error.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }
    forwardingMessage?.let { message ->
        ForwardMessageDialog(
            secure = secureLane,
            onDismiss = { forwardingMessage = null },
            onForward = { address ->
                forwardingMessage = null
                coroutineScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        val body = if (secureLane) {
                            SecureRepository.encryptMessage(context, address, message.text)
                        } else {
                            Result.success(message.text)
                        }
                        body.flatMap {
                            if (!secureLane && SmsRepository.smsPartCount(context, it) > 1) {
                                CarrierMmsRepository.sendText(context, address, it)
                            } else {
                                SmsRepository.sendText(context, address, it)
                            }
                        }
                    }
                    result.onSuccess {
                        Toast.makeText(context, "Message forwarded", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, "Forward failed: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }
    deleteMessageCandidate?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteMessageCandidate = null },
            title = { Text("Delete message?") },
            text = { Text("This removes the message from this phone's Android message history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteMessageCandidate = null
                        coroutineScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                SmsRepository.deleteMessage(context, message.id, message.isMms).onSuccess {
                                    if (!message.outgoing) return@onSuccess
                                    SecureRepository.forgetOutgoingPlaintext(context, message.wireBody)
                                }
                            }
                            result.onSuccess { attachmentRevision++ }
                                .onFailure {
                                    Toast.makeText(context, "Delete failed: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteMessageCandidate = null }) { Text("Cancel") }
            },
        )
    }
    if (showDeleteConversation) {
        AlertDialog(
            onDismissRequest = { showDeleteConversation = false },
            title = { Text("Delete conversation?") },
            text = { Text("All SMS and MMS in this conversation will be removed from this phone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConversation = false
                        val threadId = resolvedThreadId ?: return@TextButton
                        coroutineScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                SmsRepository.deleteThread(context, threadId)
                            }
                            result.onSuccess { onBack() }
                                .onFailure {
                                    Toast.makeText(context, "Delete failed: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                        }
                    },
                ) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConversation = false }) { Text("Cancel") }
            },
        )
    }
    if (showSecureResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showSecureResetConfirmation = false },
            title = { Text("Reset Secure pairing?") },
            text = {
                Text(
                    "The other phone has a new Secure identity. This removes the old EP3 ratchet only for this contact, sends a new acceptance, and requires both phones to compare the new safety code. Continue only if the other person intentionally reset or reinstalled EutherPing.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSecureResetConfirmation = false
                        coroutineScope.launch {
                            val address = conversation.smsAddress.orEmpty()
                            val result = withContext(Dispatchers.IO) {
                                SecureRepository.resetPendingEp3IdentityForRePairing(context, address)
                                    .flatMap { SecureRepository.acceptInvitation(context, address) }
                                    .flatMap { acceptance ->
                                        SmsRepository.sendText(context, address, acceptance)
                                    }
                            }
                            result.onSuccess {
                                secureRevision++
                                Toast.makeText(
                                    context,
                                    "New Secure pairing accepted — compare safety codes",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }.onFailure {
                                secureRevision++
                                Toast.makeText(
                                    context,
                                    "Secure reset failed: ${it.message}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                ) { Text("Reset & re-pair") }
            },
            dismissButton = {
                TextButton(onClick = { showSecureResetConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun MessageActionsDialog(
    message: DemoMessage,
    failureDescription: String?,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message actions") },
        text = {
            Column {
                Text(
                    message.text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (failureDescription != null) {
                    Text(
                        failureDescription,
                        color = Amber,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (onRetry != null) {
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                        Text("Retry send")
                    }
                }
                TextButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                    Text("Copy text")
                }
                TextButton(onClick = onForward, modifier = Modifier.fillMaxWidth()) {
                    Text("Forward${if (message.transport == Transport.SECURE) " securely" else ""}")
                }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete from this phone")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ForwardMessageDialog(
    secure: Boolean,
    onDismiss: () -> Unit,
    onForward: (String) -> Unit,
) {
    var address by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (secure) "Forward securely" else "Forward message") },
        text = {
            Column {
                Text(
                    if (secure) {
                        "Enter a verified Vessel address. The text will be encrypted again for that recipient."
                    } else {
                        "Enter the recipient phone number."
                    },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Phone number") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = address.isNotBlank(),
                onClick = { onForward(address.trim()) },
            ) { Text("Forward") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ImageActionsDialog(
    secure: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Image actions") },
        text = {
            Column {
                if (secure) {
                    Text(
                        "A saved copy is decrypted and is no longer protected by the Vessel.",
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text("Open image")
                }
                TextButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Text("Save image…")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ConversationHeader(
    conversation: Conversation,
    securePeerState: SecurePeerState?,
    searchVisible: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onDeleteConversation: () -> Unit,
    onBack: () -> Unit,
) {
    val accent = if (conversation.transport == Transport.SECURE) Violet else Amber
    val clipboard = LocalClipboardManager.current
    var menuExpanded by remember(conversation.id) { mutableStateOf(false) }
    Column(modifier = Modifier.background(Deep)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Toxic)
            }
            VesselAvatar(conversation.initials, accent, size = 38)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(conversation.name, color = Mist, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    when (securePeerState) {
                        SecurePeerState.VERIFIED -> "SECURE BETA // IDENTITY VERIFIED"
                        SecurePeerState.ACTIVE_UNVERIFIED -> "SECURE BETA // COMPARE SAFETY CODE"
                        SecurePeerState.INVITE_RECEIVED -> "SECURE INVITATION RECEIVED"
                        SecurePeerState.INVITE_SENT -> "SECURE INVITATION IN TRANSIT"
                        else -> if (conversation.transport == Transport.SECURE) {
                            "VESSEL NOT PAIRED"
                        } else if (conversation.recipients.size > 1) {
                            "GROUP MMS // ${conversation.recipients.size} PARTICIPANTS // REPLY ALL"
                        } else {
                            "CELLULAR CONTACT"
                        }
                    },
                    color = accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                )
            }
            IconButton(onClick = onToggleSearch) {
                Icon(Icons.Default.Search, contentDescription = "Search this conversation", tint = accent)
            }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Conversation options", tint = Muted)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(if (conversation.recipients.size > 1) "Copy participants" else "Copy address") },
                    onClick = {
                        clipboard.setText(AnnotatedString(conversation.recipients.joinToString(", ")))
                        menuExpanded = false
                    },
                )
                if (conversation.threadId != null) {
                    DropdownMenuItem(
                        text = { Text("Delete conversation") },
                        onClick = {
                            menuExpanded = false
                            onDeleteConversation()
                        },
                    )
                }
            }
        }
        AnimatedVisibility(visible = searchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("Search loaded messages") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        HorizontalDivider(color = accent.copy(alpha = 0.2f))
    }
}

@Composable
private fun MessageBubble(
    message: DemoMessage,
    attachmentRevision: Int,
    secureAddress: String?,
    onLongPress: () -> Unit,
    onSecureImageLongPress: (SecureAttachmentDescriptor) -> Unit,
    onCarrierImageLongPress: (CarrierMmsAttachment) -> Unit,
    onAttachment: (SecureAttachmentDescriptor) -> Unit,
) {
    val context = LocalContext.current
    val simLabel = remember(message.subscriptionId) {
        CarrierSubscriptionRepository.label(context, message.subscriptionId)
    }
    val lightTheme = LocalLightTheme.current
    val accent = if (message.outgoing) {
        if (message.transport == Transport.SECURE) Violet else Amber
    } else {
        Toxic
    }
    val bubbleColor = if (message.outgoing) {
        accent.copy(alpha = if (lightTheme) 0.17f else 0.24f)
    } else {
        Toxic.copy(alpha = if (lightTheme) 0.11f else 0.16f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
        horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (message.outgoing) 18.dp else 4.dp,
                        bottomEnd = if (message.outgoing) 4.dp else 18.dp,
                    ),
                )
                .background(bubbleColor)
                .border(
                    1.dp,
                    accent.copy(alpha = if (message.outgoing) 0.62f else 0.42f),
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            LinkifiedMessageText(message.text)
            message.attachment?.let { attachment ->
                val previewableSecureImage = remember(attachment.id, attachment.plaintextSize) {
                    SecureAttachmentRepository.canPreviewInMemory(attachment)
                }
                val downloaded = remember(attachment.id, attachmentRevision) {
                    SecureAttachmentRepository.downloadedCiphertext(context, attachment.id) != null
                }
                var secureImagePreviewRequested by remember(attachment.id) { mutableStateOf(false) }
                var secureImagePreviewError by remember(attachment.id) { mutableStateOf<String?>(null) }
                val secureImagePreview by produceState<Bitmap?>(
                    initialValue = null,
                    attachment.id,
                    attachmentRevision,
                    secureAddress,
                    secureImagePreviewRequested,
                ) {
                    if (secureAddress != null && secureImagePreviewRequested &&
                        previewableSecureImage
                    ) {
                        val stored = !attachment.incoming ||
                            SecureAttachmentRepository.downloadedCiphertext(context, attachment.id) != null
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                if (attachment.incoming && !stored) {
                                    SecureAttachmentRepository.downloadIncoming(
                                        context,
                                        secureAddress,
                                        attachment,
                                    ).getOrThrow()
                                }
                                SecureAttachmentRepository.loadImagePreview(context, attachment).getOrThrow()
                            }
                        }
                        result.onSuccess { value = it }
                            .onFailure { error ->
                                Log.w("EutherPingAttachment", "On-demand Vessel image preview failed", error)
                                secureImagePreviewError = error.message ?: "Could not decrypt image"
                                secureImagePreviewRequested = false
                            }
                    }
                }
                secureImagePreview?.let { bitmap ->
                    DisposableEffect(bitmap) {
                        onDispose { bitmap.recycle() }
                    }
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Decrypted Vessel image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(
                                (bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
                                    .coerceIn(0.72f, 1.8f),
                            )
                            .heightIn(max = 260.dp)
                            .padding(top = 9.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { onAttachment(attachment) },
                                onLongClick = { onSecureImageLongPress(attachment) },
                            ),
                    )
                }
                secureImagePreviewError?.let { error ->
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
                Button(
                    onClick = {
                        if (previewableSecureImage && secureImagePreview == null
                        ) {
                            secureImagePreviewError = null
                            secureImagePreviewRequested = true
                        } else {
                            onAttachment(attachment)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Violet.copy(alpha = 0.2f),
                        contentColor = Violet,
                    ),
                    modifier = Modifier.padding(top = 9.dp),
                ) {
                    Text(
                        when {
                            previewableSecureImage &&
                                secureImagePreview == null -> "DECRYPT // SHOW IMAGE"
                            !attachment.incoming -> "OPEN VERIFIED FILE"
                            downloaded || secureImagePreview != null -> "OPEN VERIFIED FILE"
                            else -> "DOWNLOAD // ${attachment.transportLabel}"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                    )
                }
            }
            if (
                message.carrierMmsAttachment != null ||
                (message.isMms && message.text == "📷 Carrier MMS")
            ) {
                val attachment = message.carrierMmsAttachment
                val preview by produceState<Bitmap?>(
                    initialValue = null,
                    attachment?.uri,
                    attachmentRevision,
                ) {
                    value = null
                    attachment ?: return@produceState
                    repeat(4) { attempt ->
                        val loaded = withContext(Dispatchers.IO) {
                            CarrierMmsRepository.loadPreview(context, attachment)
                        }
                        if (loaded.isSuccess) {
                            value = loaded.getOrNull()
                            return@produceState
                        }
                        if (attempt < 3) delay(350L shl attempt)
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(top = 9.dp)
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Void.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = preview
                    if (bitmap != null && attachment != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Carrier MMS image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .combinedClickable(
                                    onClick = {
                                        CarrierMmsRepository.openStoredImage(context, attachment).onFailure { error ->
                                            Toast.makeText(
                                                context,
                                                "Could not open MMS image: ${error.message}",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    },
                                    onLongClick = { onCarrierImageLongPress(attachment) },
                                ),
                        )
                    } else {
                        Text(
                            if (attachment == null) "WAITING FOR MMS IMAGE…" else "LOADING MMS IMAGE…",
                            color = Muted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                        )
                    }
                }
                if (attachment != null) Button(
                    onClick = {
                        CarrierMmsRepository.openStoredImage(context, attachment).onFailure { error ->
                            Toast.makeText(
                                context,
                                "Could not open MMS image: ${error.message}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Amber.copy(alpha = 0.2f),
                        contentColor = Amber,
                    ),
                    modifier = Modifier.padding(top = 9.dp),
                ) {
                    Text("OPEN MMS IMAGE // CARRIER", fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildString {
                        append(if (message.outgoing) "YOU" else "THEM")
                        append(
                            when {
                                message.transport == Transport.SECURE -> " // ECHO"
                                message.isMms -> " // MMS // CARRIER"
                                else -> " // CELL"
                            },
                        )
                        simLabel?.let { append(" // ").append(it) }
                    },
                    color = accent.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    letterSpacing = 0.7.sp,
                )
                Text(
                    "  ${message.time}",
                    color = Mist.copy(alpha = 0.62f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                )
                message.deliveryState?.let { state ->
                    Text(
                        "  // ${state.label}",
                        color = if (state == MessageDeliveryState.FAILED) Amber else accent.copy(alpha = 0.82f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkifiedMessageText(text: String) {
    val linkColor = Toxic
    val links = remember(text) { findMessageUrls(text) }
    val annotated = remember(text, linkColor) {
        buildAnnotatedString {
            var cursor = 0
            links.forEach { link ->
                append(text.substring(cursor, link.start))
                withLink(
                    LinkAnnotation.Url(
                        url = link.browserUrl,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                    ),
                ) {
                    append(text.substring(link.start, link.endExclusive))
                }
                cursor = link.endExclusive
            }
            append(text.substring(cursor))
        }
    }
    SelectionContainer {
        Text(annotated, color = Mist, fontSize = 15.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun MessageSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    secure: Boolean,
    contacts: List<PhoneContact>,
    state: MessageSearchState,
    onBack: () -> Unit,
    onOpen: (SmsSearchHit) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Toxic)
            }
            Text(
                if (secure) "SEARCH VESSEL ECHOES" else "SEARCH CELL MESSAGES",
                color = if (secure) Violet else Amber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Text, contact, number or date") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        )
        if (secure) {
            Text(
                "Only a bounded recent set is decrypted for this on-device search. Search text is never cached.",
                color = Violet.copy(alpha = 0.72f),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 9.dp),
            )
        }
        when {
            state.loading -> Text("SEARCHING MESSAGE ARRAY…", color = Muted, fontFamily = FontFamily.Monospace)
            state.error != null -> Text(state.error, color = Amber, fontSize = 13.sp)
            query.isNotBlank() && state.hits.isEmpty() ->
                Text("NO MATCHES", color = Muted, fontFamily = FontFamily.Monospace)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            items(state.hits, key = { it.messageId }) { hit ->
                val accent = if (secure) Violet else Amber
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(hit) },
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(13.dp)) {
                        Row {
                            Text(
                                ContactRepository.displayName(contacts, hit.address) ?: hit.address,
                                color = accent,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(formatMessageTime(hit.timestamp), color = Muted, fontSize = 10.sp)
                        }
                        Text(
                            hit.text,
                            color = Mist,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactSearchScreen(
    contacts: List<PhoneContact>,
    loading: Boolean,
    error: String?,
    secureMode: Boolean,
    onBack: () -> Unit,
    onContactSelected: (PhoneContact) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val filteredContacts = remember(contacts, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            contacts
        } else {
            val phoneQuery = android.telephony.PhoneNumberUtils.normalizeNumber(normalizedQuery)
            contacts.filter { contact ->
                contact.name.contains(normalizedQuery, ignoreCase = true) ||
                    contact.phoneNumber.contains(normalizedQuery, ignoreCase = true) ||
                    (
                        phoneQuery.isNotEmpty() &&
                            android.telephony.PhoneNumberUtils.normalizeNumber(contact.phoneNumber)
                                .contains(phoneQuery)
                    )
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Toxic)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                placeholder = { Text("Search name or number") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Toxic)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Toxic,
                    unfocusedBorderColor = Toxic.copy(alpha = 0.3f),
                    focusedTextColor = Mist,
                    unfocusedTextColor = Mist,
                    cursorColor = Toxic,
                ),
            )
        }
        Text(
            if (secureMode) {
                "VESSEL SONAR // ${filteredContacts.size} CONTACTS // SELECT TO PAIR"
            } else {
                "PHONEBOOK SONAR // ${filteredContacts.size} CONTACTS"
            },
            color = if (secureMode) Violet else Toxic.copy(alpha = 0.72f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp,
                end = 14.dp,
                bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filteredContacts, key = { it.id }) { contact ->
                ContactResultRow(contact, onClick = { onContactSelected(contact) })
            }
            if (filteredContacts.isEmpty()) {
                item {
                    Text(
                        when {
                            loading -> "READING PHONE CONTACTS…"
                            error != null -> "CONTACT PROVIDER ERROR // $error"
                            contacts.isEmpty() -> "NO PHONE CONTACTS DETECTED"
                            else -> "NO MATCHING VESSEL"
                        },
                        color = Muted,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactResultRow(contact: PhoneContact, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, Toxic.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(15.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VesselAvatar(nameInitials(contact.name), Toxic, size = 42)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, color = Mist, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    contact.phoneNumber,
                    color = Toxic.copy(alpha = 0.72f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Text("PING ›", color = Amber, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SecurePairingCard(
    peerState: SecurePeerState,
    protocol: SecureProtocol,
    safetyNumber: String?,
    onInvite: () -> Unit,
    onUpgrade: () -> Unit,
    onAccept: () -> Unit,
    onVerify: () -> Unit,
    onAcceptIdentityChange: () -> Unit,
    onRejectIdentityChange: () -> Unit,
) {
    val active = peerState in setOf(SecurePeerState.ACTIVE_UNVERIFIED, SecurePeerState.VERIFIED)
    Card(
        colors = CardDefaults.cardColors(containerColor = Violet.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, Violet.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(15.dp)) {
            Text(
                when (peerState) {
                    SecurePeerState.NONE -> "SECURE PING // NOT PAIRED"
                    SecurePeerState.INVITE_SENT -> "SECURE PING // AWAITING ECHO"
                    SecurePeerState.INVITE_RECEIVED -> "SECURE PING // INVITE DETECTED"
                    SecurePeerState.ACTIVE_UNVERIFIED -> "SECURE BETA // KEY UNVERIFIED"
                    SecurePeerState.VERIFIED -> if (protocol == SecureProtocol.RATCHET_EP3) {
                        "EP3 RATCHET BETA // IDENTITY VERIFIED"
                    } else {
                        "SECURE BETA // LEGACY IDENTITY VERIFIED"
                    }
                    SecurePeerState.IDENTITY_CHANGE_PENDING -> "SECURE WARNING // IDENTITY CHANGED"
                },
                color = Violet,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.7.sp,
            )
            Text(
                when (peerState) {
                    SecurePeerState.NONE -> "Send a signed EP3 invitation with this phone's ratchet pre-key. It can use several SMS parts."
                    SecurePeerState.INVITE_SENT -> "The other phone must open this thread and accept the invitation."
                    SecurePeerState.INVITE_RECEIVED -> "Accept to return this phone's public keys and enable encrypted SMS capsules."
                    SecurePeerState.ACTIVE_UNVERIFIED -> "Compare this safety code on both phones before marking the identity verified."
                    SecurePeerState.VERIFIED -> if (protocol == SecureProtocol.RATCHET_EP3) {
                        "Text and attachment manifests ratchet through Vodozemac. Files stay encrypted over direct Wi-Fi or Bluetooth."
                    } else {
                        "Legacy HPKE remains readable. Upgrade explicitly to start a new EP3 ratchet session."
                    }
                    SecurePeerState.IDENTITY_CHANGE_PENDING -> "A different identity arrived. Secure sending is locked. Compare the new safety code before trusting it."
                },
                color = Mist,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 7.dp),
            )
            if ((active || peerState == SecurePeerState.IDENTITY_CHANGE_PENDING) && safetyNumber != null) {
                Text(
                    safetyNumber,
                    color = Toxic,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.9.sp,
                    modifier = Modifier.padding(top = 11.dp),
                )
            }
            Text(
                if (protocol == SecureProtocol.RATCHET_EP3) {
                    "RATCHET BETA: Vodozemac Olm v1 // external review pending."
                } else {
                    "LEGACY SECURE BETA: no forward secrecy."
                },
                color = Amber.copy(alpha = 0.82f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 9.dp),
            )
            when (peerState) {
                SecurePeerState.NONE -> Button(
                    onClick = onInvite,
                    colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color.White),
                    modifier = Modifier.padding(top = 11.dp),
                ) { Text("SEND SECURE INVITE", fontWeight = FontWeight.Black) }
                SecurePeerState.INVITE_SENT -> TextButton(onClick = onInvite) {
                    Text("RESEND SIGNED INVITE", color = Violet)
                }
                SecurePeerState.INVITE_RECEIVED -> Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color.White),
                    modifier = Modifier.padding(top = 11.dp),
                ) { Text("ACCEPT SECURE PING", fontWeight = FontWeight.Black) }
                SecurePeerState.ACTIVE_UNVERIFIED -> Button(
                    onClick = onVerify,
                    colors = ButtonDefaults.buttonColors(containerColor = Toxic, contentColor = Void),
                    modifier = Modifier.padding(top = 11.dp),
                ) { Text("CODES MATCH — VERIFY", fontWeight = FontWeight.Black) }
                SecurePeerState.VERIFIED -> if (protocol == SecureProtocol.LEGACY_EP1) {
                    Button(
                        onClick = onUpgrade,
                        colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color.White),
                        modifier = Modifier.padding(top = 11.dp),
                    ) { Text("UPGRADE TO EP3", fontWeight = FontWeight.Black) }
                } else {
                    Unit
                }
                SecurePeerState.IDENTITY_CHANGE_PENDING -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 11.dp),
                ) {
                    Button(
                        onClick = onAcceptIdentityChange,
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Void),
                    ) {
                        Text(
                            if (protocol == SecureProtocol.RATCHET_EP3) {
                                "RESET & RE-PAIR"
                            } else {
                                "REVIEW NEW ID"
                            },
                            fontWeight = FontWeight.Black,
                        )
                    }
                    TextButton(onClick = onRejectIdentityChange) {
                        Text("KEEP OLD ID", color = Mist)
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    transport: Transport,
    onTransportChange: (Transport) -> Unit,
    secureOnly: Boolean,
    enabled: Boolean,
    attachmentBusy: Boolean,
    attachmentEnabled: Boolean,
    carrierMmsUri: Uri?,
    carrierSubscriptions: List<CarrierSubscription>,
    selectedSubscriptionId: Int?,
    onSelectSubscription: (Int) -> Unit,
    groupRecipientCount: Int,
    onRemoveCarrierMms: () -> Unit,
    onAttachment: () -> Unit,
    onSend: () -> Unit,
) {
    val context = LocalContext.current
    val accent = if (transport == Transport.SECURE) Violet else Amber
    val carrierMmsPreview by produceState<Bitmap?>(initialValue = null, carrierMmsUri) {
        value = null
        val uri = carrierMmsUri ?: return@produceState
        repeat(2) { attempt ->
            val loaded = withContext(Dispatchers.IO) {
                CarrierMmsRepository.loadSourcePreview(context, uri)
            }
            if (loaded.isSuccess) {
                value = loaded.getOrNull()
                return@produceState
            }
            if (attempt == 0) delay(250L)
        }
    }
    Column(
        modifier = Modifier
            .background(Deep)
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (if (secureOnly) listOf(Transport.SECURE) else listOf(Transport.SMS)).forEach { option ->
                val selected = option == transport
                StatusChip(
                    text = option.label,
                    color = if (option == Transport.SECURE) Violet else Amber,
                    selected = selected,
                    modifier = Modifier.clickable(enabled = enabled) { onTransportChange(option) },
                )
            }
        }
        AnimatedVisibility(visible = secureOnly && !enabled) {
            Text(
                "Complete the vessel handshake and verify the safety code before sending.",
                color = Violet.copy(alpha = 0.78f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        AnimatedVisibility(visible = transport == Transport.SMS) {
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    if (groupRecipientCount > 1) {
                        "GROUP MMS // $groupRecipientCount recipients // reply all"
                    } else {
                        "Carrier charges may apply. Secure messages never fall back silently."
                    },
                    color = Amber.copy(alpha = 0.84f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
                if (carrierSubscriptions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        carrierSubscriptions.forEach { subscription ->
                            StatusChip(
                                text = subscription.label,
                                color = Amber,
                                selected = selectedSubscriptionId == subscription.id,
                                modifier = Modifier.clickable(enabled = enabled && !attachmentBusy) {
                                    onSelectSubscription(subscription.id)
                                },
                            )
                        }
                    }
                    if (carrierSubscriptions.size > 1 && selectedSubscriptionId == null) {
                        Text(
                            "SELECT A SIM BEFORE SENDING",
                            color = Amber,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
        }
        carrierMmsPreview?.let { bitmap ->
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "MMS draft image preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(carrierMmsUri, "image/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                )
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    "Could not open MMS image: ${error.message}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                )
                TextButton(
                    onClick = onRemoveCarrierMms,
                    enabled = !attachmentBusy,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("REMOVE MMS IMAGE", color = Amber, fontFamily = FontFamily.Monospace)
                }
            }
        }
        if (carrierMmsUri != null && carrierMmsPreview == null) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
                Text(
                    "PREPARING MMS PREVIEW… If this remains, remove the image and choose it again.",
                    color = Amber,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                )
                TextButton(
                    onClick = onRemoveCarrierMms,
                    enabled = !attachmentBusy,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("REMOVE MMS IMAGE", color = Amber, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAttachment, enabled = enabled && !attachmentBusy && attachmentEnabled) {
                Icon(Icons.Default.Add, contentDescription = "Attachment", tint = accent)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                enabled = enabled,
                placeholder = {
                    Text(
                        when {
                            secureOnly && !enabled -> "Verify vessel to emit secure pings…"
                            secureOnly -> "Emit secure ping…"
                            carrierMmsUri != null -> "Write an MMS caption…"
                            else -> "Send SMS or add an MMS image…"
                        },
                        color = Muted,
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = accent.copy(alpha = 0.28f),
                    focusedTextColor = Mist,
                    unfocusedTextColor = Mist,
                    cursorColor = accent,
                ),
            )
            val simReady = carrierSubscriptions.size <= 1 || selectedSubscriptionId != null
            val canSend = (draft.isNotBlank() || carrierMmsPreview != null) && simReady
            IconButton(onClick = onSend, enabled = enabled && !attachmentBusy && canSend) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (!enabled || attachmentBusy || !canSend) Muted.copy(alpha = 0.4f) else accent,
                )
            }
        }
    }
}

@Composable
private fun VesselsScreen(
    isDefaultSmsApp: Boolean,
    hasSmsPermissions: Boolean,
    vessels: List<Conversation>,
    historyLoading: Boolean,
    historyError: String?,
    onRetryHistory: () -> Unit,
    onRequestSmsSetup: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    showArchived: Boolean,
    onConversationAction: (Conversation, ConversationControlAction) -> Unit,
) {
    val realSmsReady = isDefaultSmsApp && hasSmsPermissions
    val displayedVessels = if (realSmsReady) vessels.filter { showArchived || !it.archived } else vessels
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!realSmsReady) {
            item {
                SmsSetupBanner(
                    isDefaultSmsApp = isDefaultSmsApp,
                    onRequestSmsSetup = onRequestSmsSetup,
                )
            }
        } else if (historyLoading || historyError != null) {
            item {
                HistoryStatusCard(
                    loading = historyLoading,
                    error = historyError,
                    contentAvailable = vessels.isNotEmpty(),
                    onRetry = onRetryHistory,
                )
            }
        }
        item {
            Text(
                "VESSELS IN RANGE",
                color = Violet,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        item {
            Text(
                "Use the search icon above to find a phonebook contact and establish a Secure Ping identity.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
        if (realSmsReady && !historyLoading && historyError == null && displayedVessels.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Violet.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, Violet.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        "NO VESSELS YET // SEARCH FOR A CONTACT TO SEND A SECURE INVITE",
                        color = Violet,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
        items(displayedVessels, key = { it.id }) { conversation ->
            ConversationRow(
                conversation,
                onClick = { onOpenConversation(conversation) },
                onAction = { onConversationAction(conversation, it) },
            )
        }
    }
}

private fun securePeerLabel(state: SecurePeerState): String = when (state) {
    SecurePeerState.NONE -> "NOT PAIRED"
    SecurePeerState.INVITE_SENT -> "AWAITING ECHO"
    SecurePeerState.INVITE_RECEIVED -> "INVITE RECEIVED"
    SecurePeerState.ACTIVE_UNVERIFIED -> "VERIFY SAFETY CODE"
    SecurePeerState.VERIFIED -> "IDENTITY VERIFIED"
    SecurePeerState.IDENTITY_CHANGE_PENDING -> "IDENTITY CHANGED"
}

private fun securePeerPreview(state: SecurePeerState): String = when (state) {
    SecurePeerState.NONE -> "Ready to establish a secure vessel"
    SecurePeerState.INVITE_SENT -> "Secure invitation sent"
    SecurePeerState.INVITE_RECEIVED -> "Secure invitation waiting for acceptance"
    SecurePeerState.ACTIVE_UNVERIFIED -> "Compare the safety code on both phones"
    SecurePeerState.VERIFIED -> "Secure Ping channel ready"
    SecurePeerState.IDENTITY_CHANGE_PENDING -> "Secure sending locked until identity review"
}

@Composable
private fun VesselBiometricGateScreen(
    attempt: Int,
    error: String?,
    onAuthenticationMessage: (String) -> Unit,
    onAuthenticated: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(context, attempt) {
        val session = VesselBiometricGate.authenticate(
            activity = context as MainActivity,
            onSuccess = onAuthenticated,
            onFailure = onAuthenticationMessage,
        )
        onDispose { session?.cancel() }
    }
    val transition = rememberInfiniteTransition(label = "vessel-biometric-scan")
    val scan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "fingerprint-scan-position",
    )
    val toxicColor = Toxic
    val violetColor = Violet
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xF8020604)) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "VESSEL BIOMETRIC SEAL",
                color = Violet,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                letterSpacing = 1.6.sp,
            )
            Spacer(Modifier.height(28.dp))
            Canvas(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF07120D))
                    .border(1.dp, Violet.copy(alpha = 0.65f), CircleShape),
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(violetColor.copy(alpha = 0.08f), radius = size.minDimension * 0.43f, center = center)
                drawCircle(toxicColor.copy(alpha = 0.12f), radius = size.minDimension * 0.31f, center = center)
                val widths = listOf(0.13f, 0.22f, 0.31f, 0.40f, 0.49f, 0.58f)
                widths.forEachIndexed { index, fraction ->
                    val diameter = size.minDimension * fraction
                    drawArc(
                        color = if (index % 2 == 0) toxicColor.copy(alpha = 0.9f) else violetColor.copy(alpha = 0.9f),
                        startAngle = 205f + index * 5f,
                        sweepAngle = 225f - index * 9f,
                        useCenter = false,
                        topLeft = Offset(center.x - diameter / 2f, center.y - diameter / 2f),
                        size = androidx.compose.ui.geometry.Size(diameter, diameter * 1.22f),
                        style = Stroke(width = 3.2f),
                    )
                }
                val scanY = size.height * (0.18f + scan * 0.64f)
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, violetColor, toxicColor, violetColor, Color.Transparent),
                    ),
                    start = Offset(size.width * 0.12f, scanY),
                    end = Offset(size.width * 0.88f, scanY),
                    strokeWidth = 4f,
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                if (error == null) "SCANNING ENROLLED BIOMETRIC…" else "SEAL REMAINS LOCKED",
                color = if (error == null) Toxic else Amber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
            Text(
                error ?: "Android verifies your fingerprint. EutherPing never receives biometric data.",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onCancel) { Text("BACK TO SIGNALS") }
                if (error != null) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = Violet),
                    ) {
                        Text("SCAN AGAIN", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemScreen(
    isDefaultSmsApp: Boolean,
    hasSmsPermissions: Boolean,
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    biometricGateEnabled: Boolean,
    onBiometricGateEnabledChange: (Boolean) -> Unit,
    onRequestSmsSetup: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var propSoundsEnabled by remember { mutableStateOf(AppSounds.isEnabled(context)) }
    var notificationPrivacy by remember {
        mutableStateOf(ConversationControlsRepository.notificationPrivacy(context))
    }
    val secureIdentity = remember { SecureRepository.ensureIdentity(context) }
    var bluetoothRevision by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) bluetoothRevision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        bluetoothRevision++
        if (BluetoothAttachmentTransport.hasPermission(context)) {
            BluetoothAttachmentTransport.ensureServerStarted(context)
        }
    }
    val diagnosticsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) coroutineScope.launch {
            withContext(Dispatchers.IO) { PerformanceDiagnostics.export(context, uri) }
                .onSuccess { Toast.makeText(context, "Diagnostics saved", Toast.LENGTH_SHORT).show() }
                .onFailure { error ->
                    Toast.makeText(
                        context,
                        "Could not save diagnostics: ${error.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }
    val bluetoothStatus = remember(bluetoothRevision) {
        BluetoothAttachmentTransport.status(context)
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThemePickerCard(appTheme = appTheme, onThemeChange = onThemeChange)
        SystemCard(
            "TERMINAL SOUNDS",
            if (propSoundsEnabled) "ARMED // LOW" else "SILENT",
            if (propSoundsEnabled) Toxic else Muted,
            "Subtle local interface sounds for navigation, sending and Secure actions. " +
                "Incoming-message notification sounds remain under Android's channel settings.",
            actionLabel = if (propSoundsEnabled) "MUTE TERMINAL" else "ARM TERMINAL",
            onAction = {
                propSoundsEnabled = !propSoundsEnabled
                AppSounds.setEnabled(context, propSoundsEnabled)
                if (propSoundsEnabled) AppSounds.play(context, AppSound.SECURE_VERIFIED)
            },
        )
        NotificationPrivacyCard(
            privacy = notificationPrivacy,
            onPrivacyChange = { selected ->
                notificationPrivacy = selected
                ConversationControlsRepository.setNotificationPrivacy(context, selected)
            },
        )
        SystemCard(
            "EUTHERPING VIBRATION",
            "SHORT · SHORT · LONG",
            Toxic,
            "Incoming SMS, MMS and private Vessel alerts use a recognizable vibration. " +
                "Android's vibration, silent and Do Not Disturb settings always remain in control.",
            actionLabel = "ANDROID CHANNEL SETTINGS",
            onAction = {
                IncomingMessageNotifier.ensureChannel(context)
                context.startActivity(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        putExtra(Settings.EXTRA_CHANNEL_ID, IncomingMessageNotifier.CHANNEL_ID)
                    },
                )
            },
        )
        val biometricAvailability = remember(bluetoothRevision) {
            VesselBiometricGate.availability(context)
        }
        SystemCard(
            "VESSEL BIOMETRIC SEAL",
            when {
                !biometricGateEnabled -> "DISABLED"
                biometricAvailability == VesselBiometricAvailability.READY -> "ARMED"
                biometricAvailability == VesselBiometricAvailability.NOT_ENROLLED -> "ENROLL FINGERPRINT"
                else -> "UNAVAILABLE"
            },
            if (biometricGateEnabled && biometricAvailability == VesselBiometricAvailability.READY) Violet else Amber,
            when (biometricAvailability) {
                VesselBiometricAvailability.READY ->
                    "Require Android biometric authentication whenever Vessels is opened after EutherPing leaves the foreground."
                VesselBiometricAvailability.NOT_ENROLLED ->
                    "The seal is enabled, but Android has no enrolled biometric. Enroll a fingerprint in Android Settings first."
                VesselBiometricAvailability.NO_HARDWARE ->
                    "This phone has no Android biometric sensor supported by EutherPing."
                VesselBiometricAvailability.UNAVAILABLE ->
                    "Android's biometric service is temporarily unavailable."
            },
            actionLabel = if (biometricGateEnabled) "DISABLE SEAL" else "ENABLE SEAL",
            onAction = { onBiometricGateEnabledChange(!biometricGateEnabled) },
        )
        SystemCard(
            "SMS ARRAY",
            when {
                !isDefaultSmsApp -> "ROLE REQUIRED"
                !hasSmsPermissions -> "ACCESS REQUIRED"
                else -> "ONLINE"
            },
            if (isDefaultSmsApp && hasSmsPermissions) Toxic else Amber,
            when {
                !isDefaultSmsApp -> "Select EutherPing as Android's default SMS handler to connect the carrier channel."
                !hasSmsPermissions -> "The SMS role is active. Grant SMS, MMS and WAP-push access to continue."
                else -> "Live Android Telephony SMS and MMS transport is connected."
            },
            actionLabel = if (isDefaultSmsApp) "GRANT ACCESS" else "CONNECT SMS",
            onAction = if (isDefaultSmsApp && hasSmsPermissions) null else onRequestSmsSetup,
        )
        SystemCard(
            "ECHO PROTOCOL",
            if (secureIdentity.isSuccess) "SECURE BETA READY" else "KEYSTORE ERROR",
            if (secureIdentity.isSuccess) Violet else Amber,
            secureIdentity.fold(
                onSuccess = {
                    "HPKE X25519 + Ed25519 identity is protected by Android Keystore. Device fingerprint: $it. Double Ratchet is not implemented yet."
                },
                onFailure = { "Secure Ping remains disabled: ${it.message}" },
            ),
        )
        SystemCard(
            "BLUETOOTH VESSEL",
            bluetoothStatus,
            if (bluetoothStatus == "READY") Toxic else Amber,
            "Optional fallback for encrypted Vessel attachments. Grant Nearby devices, then pair " +
                "the two phones in Android Bluetooth settings. Only already paired devices are tried; " +
                "message text and ordinary SMS never move to Bluetooth.",
            actionLabel = if (!BluetoothAttachmentTransport.hasPermission(context)) {
                "GRANT NEARBY DEVICES"
            } else {
                "OPEN BLUETOOTH SETTINGS"
            },
            onAction = {
                if (!BluetoothAttachmentTransport.hasPermission(context)) {
                    bluetoothPermissionLauncher.launch(BluetoothAttachmentTransport.runtimePermissions)
                } else {
                    context.startActivity(BluetoothAttachmentTransport.settingsIntent())
                }
            },
        )
        SystemCard(
            "LOCAL VAULT",
            if (isDefaultSmsApp && hasSmsPermissions) "ANDROID PROVIDER" else "DEMO ONLY",
            Toxic,
            if (isDefaultSmsApp && hasSmsPermissions) {
                "SMS messages live in Android's system Telephony provider. Cloud backup is disabled."
            } else {
                "Secure private keys and outgoing plaintext copies are encrypted under Android Keystore."
            },
        )
        SystemCard(
            "LOCAL PERFORMANCE DIAGNOSTICS",
            "ON DEVICE",
            Toxic,
            "Export startup, provider-page, MMS-preview and dropped-frame measurements. " +
                "The report excludes contacts, phone numbers, message text, attachment names, keys and endpoints.",
            actionLabel = "EXPORT REPORT",
            onAction = { diagnosticsExportLauncher.launch("EutherPing-diagnostics.txt") },
        )
    }
}

@Composable
private fun NotificationPrivacyCard(
    privacy: NotificationPrivacy,
    onPrivacyChange: (NotificationPrivacy) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, Violet.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "NOTIFICATION PRIVACY",
                color = Mist,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Controls ordinary SMS/MMS previews. Secure Vessels always hide sender and plaintext.",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 7.dp, bottom = 12.dp),
            )
            NotificationPrivacy.entries.forEach { option ->
                val label = when (option) {
                    NotificationPrivacy.SENDER_AND_PREVIEW -> "SENDER + PREVIEW"
                    NotificationPrivacy.SENDER_ONLY -> "SENDER ONLY"
                    NotificationPrivacy.PRIVATE -> "PRIVATE"
                }
                Button(
                    onClick = { onPrivacyChange(option) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (privacy == option) Violet else Panel,
                        contentColor = if (privacy == option) Color.White else Mist,
                    ),
                    border = BorderStroke(1.dp, Violet.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                ) {
                    Text(label, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ThemePickerCard(appTheme: AppTheme, onThemeChange: (AppTheme) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, Toxic.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "DISPLAY ARRAY",
                color = Mist,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Choose a bright daytime sonar or the original nocturnal terminal.",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 7.dp, bottom = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AppTheme.entries.forEach { theme ->
                    val selected = appTheme == theme
                    val label = if (theme == AppTheme.LIGHT) "LIGHT" else "COOL DARK"
                    Button(
                        onClick = { onThemeChange(theme) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) {
                                if (theme == AppTheme.LIGHT) Amber else Violet
                            } else {
                                Panel
                            },
                            contentColor = if (selected) Color.White else Mist,
                        ),
                        border = BorderStroke(
                            1.dp,
                            (if (theme == AppTheme.LIGHT) Amber else Violet).copy(alpha = 0.6f),
                        ),
                    ) {
                        Text(label, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemCard(
    title: String,
    state: String,
    color: Color,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(title, color = Mist, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(state, color = color, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            Text(description, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Void),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(actionLabel, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DeckNavigation(selected: SignalTab, onSelected: (SignalTab) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Deep)
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SignalTab.entries.forEach { tab ->
            val active = tab == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (tab != selected) AppSounds.play(context, AppSound.TERMINAL_TICK)
                        onSelected(tab)
                    }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (active) 7.dp else 4.dp)
                        .background(if (active) Toxic else Muted, CircleShape),
                )
                Text(
                    tab.label,
                    color = if (active) Toxic else Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun VesselAvatar(initials: String, accent: Color, size: Int = 46) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(
                Brush.radialGradient(listOf(accent.copy(alpha = 0.23f), Color.Transparent)),
                CircleShape,
            )
            .border(1.dp, accent.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = accent,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = (size * 0.28f).sp,
        )
    }
}

@Composable
private fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = true,
) {
    Box(
        modifier = modifier
            .drawBehind {
                if (selected) {
                    drawRoundRect(
                        color = color.copy(alpha = 0.1f),
                        cornerRadius = CornerRadius(size.height / 2f),
                        style = Stroke(width = 4.dp.toPx()),
                    )
                }
            }
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = if (selected) 0.14f else 0.04f))
            .border(1.dp, color.copy(alpha = if (selected) 0.62f else 0.2f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            color = color.copy(alpha = if (selected) 1f else 0.48f),
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun PulseDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse-alpha",
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .graphicsLayer { alpha = pulse }
            .background(color, CircleShape),
    )
}

@Composable
private fun MiniSonar(modifier: Modifier = Modifier) {
    val toxic = Toxic
    val amber = Amber
    val violet = Violet
    val transition = rememberInfiniteTransition(label = "sonar")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4_500, easing = LinearEasing)),
        label = "sonar-sweep",
    )
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        drawCircle(toxic.copy(alpha = 0.72f), radius, style = Stroke(1.5f))
        drawCircle(toxic.copy(alpha = 0.35f), radius * 0.64f, style = Stroke(1f))
        drawCircle(toxic.copy(alpha = 0.35f), radius * 0.3f, style = Stroke(1f))
        val angle = Math.toRadians(rotation.toDouble())
        val end = Offset(
            center.x + kotlin.math.cos(angle).toFloat() * radius,
            center.y + kotlin.math.sin(angle).toFloat() * radius,
        )
        drawLine(toxic, center, end, 2f)
        val beam = Path().apply {
            moveTo(center.x, center.y)
            lineTo(end.x, end.y)
            val tailAngle = angle - 0.45
            lineTo(
                center.x + kotlin.math.cos(tailAngle).toFloat() * radius,
                center.y + kotlin.math.sin(tailAngle).toFloat() * radius,
            )
            close()
        }
        drawPath(beam, toxic.copy(alpha = 0.11f))
        drawCircle(amber, 2.2f, Offset(center.x + radius * 0.42f, center.y - radius * 0.18f))
        drawCircle(violet, 2.2f, Offset(center.x - radius * 0.28f, center.y + radius * 0.45f))
    }
}

private fun sampleConversations() = listOf(
    Conversation(1, "Mira Voss", "MV", "Ping me when you reach the north pier.", "22:06", Transport.SECURE, 2, "0.8 NM"),
    Conversation(2, "Jonas", "JN", "Tar med kaffe. Är där om tio.", "21:41", Transport.SMS, 0, "CELL TOWER 4"),
    Conversation(3, "Lina // ORCA", "LO", "Echo received. All clear below.", "20:18", Transport.SECURE, 1, "2.4 NM"),
    Conversation(4, "Verkstan", "VX", "Din beställning är klar att hämtas.", "18:52", Transport.SMS, 0, "CELL TOWER 2"),
    Conversation(5, "Niko", "NK", "Secure channel waiting for verification.", "YESTERDAY", Transport.SECURE, 0, "5.1 NM"),
)

private fun cellConversation(
    address: String,
    threadId: Long?,
    contactName: String? = null,
): Conversation = cellConversation(listOf(address), threadId, contactName)

private fun cellConversation(
    recipients: List<String>,
    threadId: Long?,
    contactName: String? = null,
): Conversation {
    val destinations = recipients.map(String::trim).filter(String::isNotBlank).distinct()
    val address = destinations.joinToString(", ")
    return Conversation(
    id = threadId?.let { (100_000L + it).toInt() } ?: address.hashCode(),
    name = contactName ?: address,
    initials = contactName?.let(::nameInitials) ?: addressInitials(address),
    preview = "New SMS channel",
    time = "NOW",
    transport = Transport.SMS,
    distance = "ANDROID TELEPHONY",
    smsAddress = address,
    threadId = threadId,
    recipients = destinations,
)
}

private fun secureConversation(
    address: String,
    threadId: Long?,
    contactName: String? = null,
): Conversation = Conversation(
    id = threadId?.let { (200_000L + it).toInt() } ?: (address.hashCode() xor 0x40000000),
    name = contactName ?: address,
    initials = contactName?.let(::nameInitials) ?: addressInitials(address),
    preview = "New Secure Ping vessel",
    time = "NOW",
    transport = Transport.SECURE,
    distance = "NOT PAIRED",
    smsAddress = address,
    threadId = threadId,
)

private fun nameInitials(name: String): String = name
    .trim()
    .split(Regex("\\s+"))
    .filter { it.isNotBlank() }
    .take(2)
    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
    .joinToString("")
    .ifBlank { "??" }

private fun addressInitials(address: String): String {
    val compact = address.filter { it.isLetterOrDigit() }
    return compact.takeLast(2).uppercase().ifBlank { "??" }
}

private inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> = fold(
    onSuccess = transform,
    onFailure = { Result.failure(it) },
)

private fun formatMessageTime(timestamp: Long): String {
    val message = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    val now = Instant.now().atZone(ZoneId.systemDefault())
    return if (message.toLocalDate() == now.toLocalDate()) {
        message.format(DateTimeFormatter.ofPattern("HH:mm"))
    } else {
        message.format(DateTimeFormatter.ofPattern("MMM d"))
    }
}
