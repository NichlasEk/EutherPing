package se.apothictech.eutherping

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.core.view.WindowCompat
import se.apothictech.eutherping.contacts.ContactRepository
import se.apothictech.eutherping.contacts.PhoneContact
import se.apothictech.eutherping.secure.SecurePeerState
import se.apothictech.eutherping.secure.SecureAttachmentDescriptor
import se.apothictech.eutherping.secure.SecureAttachmentRepository
import se.apothictech.eutherping.secure.SecureRepository
import se.apothictech.eutherping.sms.CarrierMmsAttachment
import se.apothictech.eutherping.sms.CarrierMmsRepository
import se.apothictech.eutherping.sms.SmsRepository
import kotlinx.coroutines.Dispatchers
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

private const val PREFERENCES_NAME = "eutherping_preferences"
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

enum class Transport(val label: String) {
    SECURE("SECURE PING"),
    SMS("CELL // SMS + MMS"),
}

private enum class SignalTab(val label: String) {
    SIGNALS("SIGNALS"),
    CONTACTS("VESSELS"),
    SYSTEM("SYSTEM"),
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
)

private data class DemoMessage(
    val id: Long,
    val text: String,
    val outgoing: Boolean,
    val time: String,
    val transport: Transport,
    val attachment: SecureAttachmentDescriptor? = null,
    val carrierMmsAttachment: CarrierMmsAttachment? = null,
)

class MainActivity : ComponentActivity() {
    private var requestedAddress by mutableStateOf<String?>(null)
    private var requestedSecureLane by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedAddress = intent.smsAddress()
        requestedSecureLane = intent.getBooleanExtra(EXTRA_SECURE_LANE, false)
        SecureAttachmentRepository.clearTransientPlaintext(this)
        SecureAttachmentRepository.ensureServerStarted(this).onFailure {
            Log.w("EutherPingAttachment", "Direct Wi-Fi attachment server is unavailable", it)
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedAddress = intent.smsAddress()
        requestedSecureLane = intent.getBooleanExtra(EXTRA_SECURE_LANE, false)
    }

    companion object {
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
    var appTheme by rememberSaveable { mutableStateOf(loadAppTheme(context)) }
    var activeConversation by remember { mutableStateOf<Conversation?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(SignalTab.SIGNALS) }
    var setupRevision by remember { mutableIntStateOf(0) }
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
    val smsRevision = rememberSmsRevision(isDefaultSmsApp && hasSmsPermissions)

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

    LaunchedEffect(requestedAddress, requestedSecureLane) {
        val address = requestedAddress?.trim().orEmpty()
        if (address.isNotEmpty()) {
            if (requestedSecureLane) {
                selectedTab = SignalTab.CONTACTS
                activeConversation = secureConversation(address, null)
            } else {
                selectedTab = SignalTab.SIGNALS
                activeConversation = cellConversation(address, null)
            }
            onAddressConsumed()
        }
    }
    BackHandler(enabled = activeConversation != null) {
        activeConversation = null
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
                    if (activeConversation == null) {
                        SignalDeck(
                            selectedTab = selectedTab,
                            onSelectedTabChange = { selectedTab = it },
                            isDefaultSmsApp = isDefaultSmsApp,
                            hasSmsPermissions = hasSmsPermissions,
                            smsRevision = smsRevision,
                            appTheme = appTheme,
                            onThemeChange = { selectedTheme ->
                                appTheme = selectedTheme
                                saveAppTheme(context, selectedTheme)
                            },
                            onRequestSmsSetup = ::requestSmsSetup,
                            onOpenConversation = { activeConversation = it },
                        )
                    } else {
                        ConversationDeck(
                            conversation = activeConversation!!,
                            smsRevision = smsRevision,
                            onBack = { activeConversation = null },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSmsRevision(enabled: Boolean): Int {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(context, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                revision++
            }
        }
        context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        context.contentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
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
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    onRequestSmsSetup: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
) {
    val context = LocalContext.current
    var showContactSearch by rememberSaveable { mutableStateOf(false) }
    var contactPermissionRevision by remember { mutableIntStateOf(0) }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        contactPermissionRevision++
        if (granted) showContactSearch = true
    }
    val hasContactsPermission = remember(contactPermissionRevision) {
        ContactRepository.hasPermission(context)
    }
    val phoneContacts = remember(hasContactsPermission, contactPermissionRevision) {
        if (hasContactsPermission) ContactRepository.loadPhoneContacts(context) else emptyList()
    }

    fun openContactSearch() {
        if (ContactRepository.hasPermission(context)) {
            showContactSearch = true
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    BackHandler(enabled = showContactSearch || selectedTab != SignalTab.SIGNALS) {
        if (showContactSearch) {
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
            if (showContactSearch) {
                ContactSearchScreen(
                    contacts = phoneContacts,
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
                        smsRevision = smsRevision,
                        phoneContacts = phoneContacts,
                        onRequestSmsSetup = onRequestSmsSetup,
                        onOpenConversation = onOpenConversation,
                    )
                    SignalTab.CONTACTS -> VesselsScreen(
                        isDefaultSmsApp = isDefaultSmsApp,
                        hasSmsPermissions = hasSmsPermissions,
                        smsRevision = smsRevision,
                        phoneContacts = phoneContacts,
                        onRequestSmsSetup = onRequestSmsSetup,
                        onOpenConversation = onOpenConversation,
                    )
                    SignalTab.SYSTEM -> SystemScreen(
                        isDefaultSmsApp = isDefaultSmsApp,
                        hasSmsPermissions = hasSmsPermissions,
                        appTheme = appTheme,
                        onThemeChange = onThemeChange,
                        onRequestSmsSetup = onRequestSmsSetup,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckHeader(onSearch: (() -> Unit)?, searchDescription: String) {
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
                "ACOUSTIC MESSAGE TERMINAL 0.6.0",
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
        IconButton(onClick = {}) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Muted)
        }
    }
}

@Composable
private fun SignalsScreen(
    isDefaultSmsApp: Boolean,
    hasSmsPermissions: Boolean,
    smsRevision: Int,
    phoneContacts: List<PhoneContact>,
    onRequestSmsSetup: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
) {
    val context = LocalContext.current
    var showNewSignal by rememberSaveable { mutableStateOf(false) }
    val realSmsReady = isDefaultSmsApp && hasSmsPermissions
    val smsConversations = remember(realSmsReady, smsRevision, phoneContacts) {
        if (realSmsReady) {
            SmsRepository.loadThreads(context).mapNotNull { thread ->
                val ordinaryMessages = SmsRepository.loadMessages(
                    context = context,
                    threadId = thread.threadId,
                    address = thread.address,
                ).filter { message ->
                    message.isMms || SecureRepository.decodeForDisplay(
                        context = context,
                        address = thread.address,
                        body = message.body,
                        incoming = message.incoming,
                    ) == null
                }
                val latest = ordinaryMessages.lastOrNull() ?: return@mapNotNull null
                Conversation(
                    id = (100_000L + thread.threadId).toInt(),
                    name = ContactRepository.displayName(phoneContacts, thread.address) ?: thread.address,
                    initials = ContactRepository.displayName(phoneContacts, thread.address)
                        ?.let(::nameInitials) ?: addressInitials(thread.address),
                    preview = latest.body,
                    time = formatMessageTime(latest.timestamp),
                    transport = Transport.SMS,
                    unread = ordinaryMessages.count { it.incoming && !it.read },
                    distance = "ANDROID TELEPHONY",
                    smsAddress = thread.address,
                    threadId = thread.threadId,
                )
            }
        } else {
            emptyList()
        }
    }
    val conversations = remember(realSmsReady, smsConversations) {
        if (realSmsReady) {
            smsConversations
        } else {
            sampleConversations().filter { it.transport == Transport.SMS }
        }
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
        }
        item {
            SonarHero(
                smsReady = realSmsReady,
                activeSignals = conversations.size,
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
                    "${conversations.size} CELL",
                    color = Amber,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            }
        }
        items(conversations, key = { it.id }) { conversation ->
            ConversationRow(conversation, onClick = { onOpenConversation(conversation) })
        }
    }
    if (showNewSignal) {
        NewSmsDialog(
            onDismiss = { showNewSignal = false },
            onOpen = { address ->
                showNewSignal = false
                onOpenConversation(cellConversation(address, null))
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
private fun NewSmsDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Deep,
        title = {
            Text("NEW CELL SIGNAL", color = Amber, fontFamily = FontFamily.Monospace)
        },
        text = {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.filter { character -> character.isDigit() || character in "+*# " } },
                label = { Text("Phone number") },
                singleLine = true,
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
            TextButton(onClick = { onOpen(address.trim()) }, enabled = address.isNotBlank()) {
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
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
    val accent = if (conversation.transport == Transport.SECURE) Violet else Amber
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
                        conversation.preview,
                        color = if (conversation.unread > 0) Mist else Muted,
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
}

@Composable
private fun ConversationDeck(conversation: Conversation, smsRevision: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var secureRevision by remember(conversation.smsAddress) { mutableIntStateOf(0) }
    var attachmentRevision by remember(conversation.smsAddress) { mutableIntStateOf(0) }
    var attachmentBusy by remember(conversation.smsAddress) { mutableStateOf(false) }
    val demoMessages = remember(conversation.id) {
        mutableStateListOf(
            DemoMessage(1, "Can you still see the harbor lights?", false, "22:04", conversation.transport),
            DemoMessage(2, "Barely. The fog is rolling in.", true, "22:05", conversation.transport),
            DemoMessage(3, "Ping me when you reach the north pier.", false, "22:06", conversation.transport),
        )
    }
    val isRealSms = conversation.smsAddress != null
    val secureLane = conversation.transport == Transport.SECURE
    val securePeer = remember(conversation.smsAddress, smsRevision, secureRevision) {
        if (isRealSms && secureLane) {
            SecureRepository.peer(context, conversation.smsAddress.orEmpty())
        } else {
            null
        }
    }
    val realMessages = remember(
        conversation.threadId,
        conversation.smsAddress,
        smsRevision,
        secureRevision,
        attachmentRevision,
    ) {
        if (isRealSms) {
            SmsRepository.loadMessages(
                context = context,
                threadId = conversation.threadId,
                address = conversation.smsAddress.orEmpty(),
            ).mapNotNull { message ->
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
                )
            }
        } else {
            emptyList()
        }
    }
    val messages = if (isRealSms) realMessages else demoMessages
    var composerTransport by rememberSaveable(conversation.id) {
        mutableStateOf(conversation.transport)
    }
    var draft by rememberSaveable(conversation.id) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && isRealSms && (!secureLane || securePeer?.canEncrypt == true)) {
            attachmentBusy = true
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    if (secureLane) {
                        SecureAttachmentRepository.prepareOutgoing(
                            context,
                            conversation.smsAddress.orEmpty(),
                            uri,
                        ).flatMap { prepared ->
                            SmsRepository.sendText(
                                context,
                                conversation.smsAddress.orEmpty(),
                                prepared.wireBody,
                            )
                        }
                    } else {
                        CarrierMmsRepository.sendImage(
                            context,
                            conversation.smsAddress.orEmpty(),
                            draft,
                            uri,
                        )
                    }
                }
                attachmentBusy = false
                result.onSuccess {
                    if (!secureLane) draft = ""
                    attachmentRevision++
                    Toast.makeText(
                        context,
                        if (secureLane) {
                            "Encrypted attachment offered over direct Wi-Fi"
                        } else {
                            "Carrier MMS queued"
                        },
                        Toast.LENGTH_LONG,
                    ).show()
                }.onFailure {
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

    LaunchedEffect(conversation.threadId) {
        if (isRealSms) SmsRepository.markThreadRead(context, conversation.threadId)
    }

    fun sendMessage() {
        val text = draft.trim()
        if (text.isNotEmpty()) {
            if (isRealSms) {
                val outgoing = if (secureLane) {
                    SecureRepository.encryptMessage(context, conversation.smsAddress.orEmpty(), text)
                } else {
                    Result.success(text)
                }
                outgoing.flatMap { wireBody ->
                    SmsRepository.sendText(context, conversation.smsAddress.orEmpty(), wireBody)
                }
                    .onSuccess {
                        draft = ""
                        focusManager.clearFocus()
                        Toast.makeText(
                            context,
                            if (secureLane) {
                                "Encrypted Secure Ping queued"
                            } else {
                                "SMS queued"
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    .onFailure {
                        Log.e("EutherPingSms", "Message send failed", it)
                        Toast.makeText(context, "Send failed: ${it.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
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
        topBar = { ConversationHeader(conversation, securePeer?.state, onBack) },
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
                onAttachment = {
                    attachmentPicker.launch(arrayOf(if (secureLane) "*/*" else "image/*"))
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
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
            ) {
                if (isRealSms && secureLane) {
                    item {
                        SecurePairingCard(
                            peerState = securePeer?.state ?: SecurePeerState.NONE,
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
                                        secureRevision++
                                        Toast.makeText(context, "Secure invitation sent", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure {
                                        Toast.makeText(context, "Invite failed: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                            },
                            onAccept = {
                                SecureRepository.acceptInvitation(
                                    context,
                                    conversation.smsAddress.orEmpty(),
                                ).flatMap { SmsRepository.sendText(context, conversation.smsAddress.orEmpty(), it) }
                                    .onSuccess {
                                        composerTransport = Transport.SECURE
                                        secureRevision++
                                        Toast.makeText(context, "Secure channel accepted", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure {
                                        Toast.makeText(context, "Accept failed: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                            },
                            onVerify = {
                                SecureRepository.markVerified(context, conversation.smsAddress.orEmpty())
                                secureRevision++
                            },
                        )
                    }
                }
                items(messages.asReversed(), key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        attachmentRevision = attachmentRevision,
                        onAttachment = { descriptor ->
                            if (!descriptor.incoming) {
                                Toast.makeText(
                                    context,
                                    "Encrypted payload is waiting on this device",
                                    Toast.LENGTH_SHORT,
                                ).show()
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
                                            "Direct Wi-Fi download failed: ${error.message}",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            }
                        },
                    )
                }
                item {
                    Text(
                        if (secureLane) {
                            "VESSEL CHANNEL // ENCRYPTED TEXT OVER SMS // ENCRYPTED FILES OVER DIRECT WIFI"
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
}

@Composable
private fun ConversationHeader(
    conversation: Conversation,
    securePeerState: SecurePeerState?,
    onBack: () -> Unit,
) {
    val accent = if (conversation.transport == Transport.SECURE) Violet else Amber
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
            IconButton(onClick = {}) {
                Icon(Icons.Default.MoreVert, contentDescription = "Conversation options", tint = Muted)
            }
        }
        HorizontalDivider(color = accent.copy(alpha = 0.2f))
    }
}

@Composable
private fun MessageBubble(
    message: DemoMessage,
    attachmentRevision: Int,
    onAttachment: (SecureAttachmentDescriptor) -> Unit,
) {
    val context = LocalContext.current
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
        modifier = Modifier.fillMaxWidth(),
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
            Text(message.text, color = Mist, fontSize = 15.sp, lineHeight = 20.sp)
            message.attachment?.let { attachment ->
                val downloaded = remember(attachment.id, attachmentRevision) {
                    SecureAttachmentRepository.downloadedCiphertext(context, attachment.id) != null
                }
                Button(
                    onClick = { onAttachment(attachment) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Violet.copy(alpha = 0.2f),
                        contentColor = Violet,
                    ),
                    modifier = Modifier.padding(top = 9.dp),
                ) {
                    Text(
                        when {
                            !attachment.incoming -> "ENCRYPTED // WIFI READY"
                            downloaded -> "OPEN VERIFIED FILE"
                            else -> "DOWNLOAD OVER DIRECT WIFI"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                    )
                }
            }
            message.carrierMmsAttachment?.let { attachment ->
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(attachment.uri, attachment.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                },
                            )
                        }.onFailure {
                            Toast.makeText(context, "No app could open this MMS image", Toast.LENGTH_LONG).show()
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
                                message.carrierMmsAttachment != null -> " // MMS // CARRIER"
                                else -> " // CELL"
                            },
                        )
                    },
                    color = accent.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    letterSpacing = 0.7.sp,
                )
                Text(
                    "  ${message.time}",
                    color = Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun ContactSearchScreen(
    contacts: List<PhoneContact>,
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
                        if (contacts.isEmpty()) "NO PHONE CONTACTS DETECTED" else "NO MATCHING VESSEL",
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
    safetyNumber: String?,
    onInvite: () -> Unit,
    onAccept: () -> Unit,
    onVerify: () -> Unit,
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
                    SecurePeerState.VERIFIED -> "SECURE BETA // IDENTITY VERIFIED"
                },
                color = Violet,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.7.sp,
            )
            Text(
                when (peerState) {
                    SecurePeerState.NONE -> "Send an EutherPing key invitation as one ordinary SMS."
                    SecurePeerState.INVITE_SENT -> "The other phone must open this thread and accept the invitation."
                    SecurePeerState.INVITE_RECEIVED -> "Accept to return this phone's public keys and enable encrypted SMS capsules."
                    SecurePeerState.ACTIVE_UNVERIFIED -> "Compare this safety code on both phones before marking the identity verified."
                    SecurePeerState.VERIFIED -> "Messages use authenticated HPKE capsules over carrier SMS. Carrier charges may apply."
                },
                color = Mist,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 7.dp),
            )
            if (active && safetyNumber != null) {
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
                "SECURE BETA: no Double Ratchet or forward-secrecy claim yet.",
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
                    Text("RESEND ONE-SMS INVITE", color = Violet)
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
                SecurePeerState.VERIFIED -> Unit
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
    onAttachment: () -> Unit,
    onSend: () -> Unit,
) {
    val accent = if (transport == Transport.SECURE) Violet else Amber
    Column(
        modifier = Modifier
            .background(Deep)
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
            Text(
                "Carrier charges may apply. Secure messages never fall back silently.",
                color = Amber.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
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
                            else -> "Send SMS or add an MMS image…"
                        },
                        color = Muted,
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = accent.copy(alpha = 0.28f),
                    focusedTextColor = Mist,
                    unfocusedTextColor = Mist,
                    cursorColor = accent,
                ),
            )
            IconButton(onClick = onSend, enabled = enabled && draft.isNotBlank()) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (!enabled || draft.isBlank()) Muted.copy(alpha = 0.4f) else accent,
                )
            }
        }
    }
}

@Composable
private fun VesselsScreen(
    isDefaultSmsApp: Boolean,
    hasSmsPermissions: Boolean,
    smsRevision: Int,
    phoneContacts: List<PhoneContact>,
    onRequestSmsSetup: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
) {
    val context = LocalContext.current
    val realSmsReady = isDefaultSmsApp && hasSmsPermissions
    val vessels = remember(realSmsReady, smsRevision, phoneContacts) {
        if (!realSmsReady) return@remember emptyList()
        SmsRepository.loadThreads(context).mapNotNull { thread ->
            val peer = SecureRepository.peer(context, thread.address)
            val secureMessages = SmsRepository.loadMessages(
                context = context,
                threadId = thread.threadId,
                address = thread.address,
            ).mapNotNull { message ->
                val decoded = if (message.isMms) null else SecureRepository.decodeForDisplay(
                    context = context,
                    address = thread.address,
                    body = message.body,
                    incoming = message.incoming,
                )
                if (decoded?.isSecure == true) message to decoded else null
            }
            if (peer.state == SecurePeerState.NONE && secureMessages.isEmpty()) {
                return@mapNotNull null
            }
            val latest = secureMessages.lastOrNull()
            val contactName = ContactRepository.displayName(phoneContacts, thread.address)
            Conversation(
                id = (200_000L + thread.threadId).toInt(),
                name = contactName ?: thread.address,
                initials = contactName?.let(::nameInitials) ?: addressInitials(thread.address),
                preview = latest?.second?.text ?: securePeerPreview(peer.state),
                time = formatMessageTime(latest?.first?.timestamp ?: thread.timestamp),
                transport = Transport.SECURE,
                unread = secureMessages.count { (message) -> message.incoming && !message.read },
                distance = securePeerLabel(peer.state),
                smsAddress = thread.address,
                threadId = thread.threadId,
            )
        }
    }
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
        if (realSmsReady && vessels.isEmpty()) {
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
        items(vessels, key = { it.id }) { ConversationRow(it) { onOpenConversation(it) } }
    }
}

private fun securePeerLabel(state: SecurePeerState): String = when (state) {
    SecurePeerState.NONE -> "NOT PAIRED"
    SecurePeerState.INVITE_SENT -> "AWAITING ECHO"
    SecurePeerState.INVITE_RECEIVED -> "INVITE RECEIVED"
    SecurePeerState.ACTIVE_UNVERIFIED -> "VERIFY SAFETY CODE"
    SecurePeerState.VERIFIED -> "IDENTITY VERIFIED"
}

private fun securePeerPreview(state: SecurePeerState): String = when (state) {
    SecurePeerState.NONE -> "Ready to establish a secure vessel"
    SecurePeerState.INVITE_SENT -> "Secure invitation sent"
    SecurePeerState.INVITE_RECEIVED -> "Secure invitation waiting for acceptance"
    SecurePeerState.ACTIVE_UNVERIFIED -> "Compare the safety code on both phones"
    SecurePeerState.VERIFIED -> "Secure Ping channel ready"
}

@Composable
private fun SystemScreen(
    isDefaultSmsApp: Boolean,
    hasSmsPermissions: Boolean,
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    onRequestSmsSetup: () -> Unit,
) {
    val context = LocalContext.current
    val secureIdentity = remember { SecureRepository.ensureIdentity(context) }
    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThemePickerCard(appTheme = appTheme, onThemeChange = onThemeChange)
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
                !hasSmsPermissions -> "The SMS role is active. Grant read, receive, send and write access to continue."
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
            "LOCAL VAULT",
            if (isDefaultSmsApp && hasSmsPermissions) "ANDROID PROVIDER" else "DEMO ONLY",
            Toxic,
            if (isDefaultSmsApp && hasSmsPermissions) {
                "SMS messages live in Android's system Telephony provider. Cloud backup is disabled."
            } else {
                "Secure private keys and outgoing plaintext copies are encrypted under Android Keystore."
            },
        )
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
                    .clickable { onSelected(tab) }
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
): Conversation = Conversation(
    id = threadId?.let { (100_000L + it).toInt() } ?: address.hashCode(),
    name = contactName ?: address,
    initials = contactName?.let(::nameInitials) ?: addressInitials(address),
    preview = "New SMS channel",
    time = "NOW",
    transport = Transport.SMS,
    distance = "ANDROID TELEPHONY",
    smsAddress = address,
    threadId = threadId,
)

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
