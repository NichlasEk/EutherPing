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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import se.apothictech.eutherping.sms.SmsRepository

private val Void = Color(0xFF020604)
private val Deep = Color(0xFF06100B)
private val Panel = Color(0xE80A1510)
private val Toxic = Color(0xFF8BFF62)
private val ToxicSoft = Color(0xFF4FB847)
private val Amber = Color(0xFFFF9D32)
private val Violet = Color(0xFFC87CFF)
private val Mist = Color(0xFFD7F7DC)
private val Muted = Color(0xFF78927F)

enum class Transport(val label: String) {
    SECURE("SECURE PING"),
    SMS("CELL // SMS"),
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
)

class MainActivity : ComponentActivity() {
    private var requestedAddress by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedAddress = intent.smsAddress()
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
                onAddressConsumed = { requestedAddress = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedAddress = intent.smsAddress()
    }
}

@Composable
private fun EutherPingApp(requestedAddress: String?, onAddressConsumed: () -> Unit) {
    val context = LocalContext.current
    var activeConversation by remember { mutableStateOf<Conversation?>(null) }
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

    LaunchedEffect(requestedAddress) {
        val address = requestedAddress?.trim().orEmpty()
        if (address.isNotEmpty()) {
            activeConversation = cellConversation(address, null)
            onAddressConsumed()
        }
    }
    val scheme = darkColorScheme(
        primary = Toxic,
        onPrimary = Void,
        secondary = Amber,
        tertiary = Violet,
        background = Void,
        surface = Deep,
        onBackground = Mist,
        onSurface = Mist,
    )

    MaterialTheme(colorScheme = scheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = Void) {
            AbyssBackground {
                if (activeConversation == null) {
                    SignalDeck(
                        isDefaultSmsApp = isDefaultSmsApp,
                        hasSmsPermissions = hasSmsPermissions,
                        smsRevision = smsRevision,
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF102719), Void, Color.Black),
                    center = Offset(250f, 180f),
                    radius = 1_500f,
                ),
            )
            .drawBehind {
                var y = 0f
                while (y < size.height) {
                    drawLine(Color.White.copy(alpha = 0.018f), Offset(0f, y), Offset(size.width, y), 1f)
                    y += 8f
                }
            },
    ) {
        content()
    }
}

@Composable
private fun SignalDeck(
    isDefaultSmsApp: Boolean,
    hasSmsPermissions: Boolean,
    smsRevision: Int,
    onRequestSmsSetup: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(SignalTab.SIGNALS) }
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            DeckNavigation(selectedTab, onSelected = { selectedTab = it })
        },
        modifier = Modifier.safeDrawingPadding(),
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            DeckHeader()
            when (selectedTab) {
                SignalTab.SIGNALS -> SignalsScreen(
                    isDefaultSmsApp = isDefaultSmsApp,
                    hasSmsPermissions = hasSmsPermissions,
                    smsRevision = smsRevision,
                    onRequestSmsSetup = onRequestSmsSetup,
                    onOpenConversation = onOpenConversation,
                )
                SignalTab.CONTACTS -> VesselsScreen(onOpenConversation)
                SignalTab.SYSTEM -> SystemScreen(
                    isDefaultSmsApp = isDefaultSmsApp,
                    hasSmsPermissions = hasSmsPermissions,
                    onRequestSmsSetup = onRequestSmsSetup,
                )
            }
        }
    }
}

@Composable
private fun DeckHeader() {
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
                "ACOUSTIC MESSAGE TERMINAL 0.2",
                color = Toxic.copy(alpha = 0.48f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 0.7.sp,
            )
        }
        IconButton(onClick = {}) {
            Icon(Icons.Default.Search, contentDescription = "Search", tint = Muted)
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
    onRequestSmsSetup: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
) {
    val context = LocalContext.current
    var showNewSignal by rememberSaveable { mutableStateOf(false) }
    val realSmsReady = isDefaultSmsApp && hasSmsPermissions
    val smsConversations = remember(realSmsReady, smsRevision) {
        if (realSmsReady) {
            SmsRepository.loadThreads(context).map { thread ->
                Conversation(
                    id = (100_000L + thread.threadId).toInt(),
                    name = thread.address,
                    initials = addressInitials(thread.address),
                    preview = thread.body,
                    time = formatMessageTime(thread.timestamp),
                    transport = Transport.SMS,
                    unread = thread.unread,
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
            smsConversations + sampleConversations().filter { it.transport == Transport.SECURE }
        } else {
            sampleConversations()
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
        item { SonarHero() }
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
                    "${conversations.count { it.transport == Transport.SECURE }} SECURE",
                    color = Violet,
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
private fun SonarHero() {
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
                    PulseDot(Toxic)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "SONAR ARRAY ONLINE",
                        color = Toxic,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                    )
                }
                Text(
                    "3 secure vessels within range",
                    color = Mist,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "Secure transport is a visual preview — no cryptographic channel is active yet.",
                    color = Muted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            StatusChip(
                text = "DEMO ARRAY",
                color = Amber,
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
    val demoMessages = remember(conversation.id) {
        mutableStateListOf(
            DemoMessage(1, "Can you still see the harbor lights?", false, "22:04", Transport.SECURE),
            DemoMessage(2, "Barely. The fog is rolling in.", true, "22:05", Transport.SECURE),
            DemoMessage(3, "Ping me when you reach the north pier.", false, "22:06", Transport.SECURE),
        )
    }
    val isRealSms = conversation.smsAddress != null
    val realMessages = remember(conversation.threadId, conversation.smsAddress, smsRevision) {
        if (isRealSms) {
            SmsRepository.loadMessages(
                context = context,
                threadId = conversation.threadId,
                address = conversation.smsAddress.orEmpty(),
            ).map { message ->
                DemoMessage(
                    id = message.id,
                    text = message.body,
                    outgoing = !message.incoming,
                    time = formatMessageTime(message.timestamp),
                    transport = Transport.SMS,
                )
            }
        } else {
            emptyList()
        }
    }
    val messages = if (isRealSms) realMessages else demoMessages
    var composerTransport by rememberSaveable(conversation.id) {
        mutableStateOf(if (isRealSms) Transport.SMS else conversation.transport)
    }
    var draft by rememberSaveable(conversation.id) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(conversation.threadId) {
        if (isRealSms) SmsRepository.markThreadRead(context, conversation.threadId)
    }

    fun sendMessage() {
        val text = draft.trim()
        if (text.isNotEmpty()) {
            if (isRealSms) {
                SmsRepository.sendText(context, conversation.smsAddress.orEmpty(), text)
                    .onSuccess {
                        draft = ""
                        focusManager.clearFocus()
                        Toast.makeText(context, "SMS queued", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure {
                        Log.e("EutherPingSms", "SMS send failed", it)
                        Toast.makeText(context, "SMS failed: ${it.message}", Toast.LENGTH_LONG).show()
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
        topBar = { ConversationHeader(conversation, onBack) },
        bottomBar = {
            Composer(
                draft = draft,
                onDraftChange = { draft = it },
                transport = composerTransport,
                onTransportChange = { composerTransport = it },
                allowSecureTransport = !isRealSms,
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
                items(messages.asReversed(), key = { it.id }) { message ->
                    MessageBubble(message)
                }
                item {
                    Text(
                        if (isRealSms) "ANDROID TELEPHONY // LIVE SMS THREAD" else "SECURE CHANNEL // VISUAL PROTOCOL PREVIEW",
                        color = if (isRealSms) Amber.copy(alpha = 0.7f) else Violet.copy(alpha = 0.7f),
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
private fun ConversationHeader(conversation: Conversation, onBack: () -> Unit) {
    val accent = if (conversation.transport == Transport.SECURE) Violet else Amber
    Column(modifier = Modifier.background(Color(0xF2050B08))) {
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
                    if (conversation.transport == Transport.SECURE) "IN SECURE RANGE // KEY UNVERIFIED" else "CELLULAR CONTACT",
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
private fun MessageBubble(message: DemoMessage) {
    val accent = if (message.transport == Transport.SECURE) Violet else Amber
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (message.outgoing) 18.dp else 4.dp,
                        bottomEnd = if (message.outgoing) 4.dp else 18.dp,
                    ),
                )
                .background(if (message.outgoing) accent.copy(alpha = 0.16f) else Panel)
                .border(
                    1.dp,
                    accent.copy(alpha = if (message.outgoing) 0.45f else 0.2f),
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(message.text, color = Mist, fontSize = 15.sp, lineHeight = 20.sp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (message.transport == Transport.SECURE) "ECHO" else "CELL",
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
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    transport: Transport,
    onTransportChange: (Transport) -> Unit,
    allowSecureTransport: Boolean,
    onSend: () -> Unit,
) {
    val accent = if (transport == Transport.SECURE) Violet else Amber
    Column(
        modifier = Modifier
            .background(Color(0xFB050B08))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Transport.entries.filter { allowSecureTransport || it == Transport.SMS }.forEach { option ->
                val selected = option == transport
                StatusChip(
                    text = option.label,
                    color = if (option == Transport.SECURE) Violet else Amber,
                    selected = selected,
                    modifier = Modifier.clickable { onTransportChange(option) },
                )
            }
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
            IconButton(onClick = {}) {
                Icon(Icons.Default.Add, contentDescription = "Attachment", tint = accent)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = {
                    Text(
                        if (transport == Transport.SECURE) "Emit secure ping…" else "Send ordinary SMS…",
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
            IconButton(onClick = onSend, enabled = draft.isNotBlank()) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (draft.isBlank()) Muted.copy(alpha = 0.4f) else accent,
                )
            }
        }
    }
}

@Composable
private fun VesselsScreen(onOpenConversation: (Conversation) -> Unit) {
    val vessels = remember { sampleConversations().filter { it.transport == Transport.SECURE } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
        items(vessels, key = { it.id }) { ConversationRow(it) { onOpenConversation(it) } }
    }
}

@Composable
private fun SystemScreen(
    isDefaultSmsApp: Boolean,
    hasSmsPermissions: Boolean,
    onRequestSmsSetup: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                else -> "Live Android Telephony inbox and SMS transport are connected."
            },
            actionLabel = if (isDefaultSmsApp) "GRANT ACCESS" else "CONNECT SMS",
            onAction = if (isDefaultSmsApp && hasSmsPermissions) null else onRequestSmsSetup,
        )
        SystemCard("ECHO PROTOCOL", "DESIGN PHASE", Violet, "No encryption claim is made by this prototype.")
        SystemCard(
            "LOCAL VAULT",
            if (isDefaultSmsApp && hasSmsPermissions) "ANDROID PROVIDER" else "DEMO ONLY",
            Toxic,
            if (isDefaultSmsApp && hasSmsPermissions) {
                "SMS messages live in Android's system Telephony provider. Cloud backup is disabled."
            } else {
                "Secure preview messages disappear when the app process is reset."
            },
        )
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
            .background(Color(0xF7050B08))
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
    val transition = rememberInfiniteTransition(label = "sonar")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4_500, easing = LinearEasing)),
        label = "sonar-sweep",
    )
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        drawCircle(Toxic.copy(alpha = 0.72f), radius, style = Stroke(1.5f))
        drawCircle(Toxic.copy(alpha = 0.35f), radius * 0.64f, style = Stroke(1f))
        drawCircle(Toxic.copy(alpha = 0.35f), radius * 0.3f, style = Stroke(1f))
        val angle = Math.toRadians(rotation.toDouble())
        val end = Offset(
            center.x + kotlin.math.cos(angle).toFloat() * radius,
            center.y + kotlin.math.sin(angle).toFloat() * radius,
        )
        drawLine(Toxic, center, end, 2f)
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
        drawPath(beam, Toxic.copy(alpha = 0.11f))
        drawCircle(Amber, 2.2f, Offset(center.x + radius * 0.42f, center.y - radius * 0.18f))
        drawCircle(Violet, 2.2f, Offset(center.x - radius * 0.28f, center.y + radius * 0.45f))
    }
}

private fun sampleConversations() = listOf(
    Conversation(1, "Mira Voss", "MV", "Ping me when you reach the north pier.", "22:06", Transport.SECURE, 2, "0.8 NM"),
    Conversation(2, "Jonas", "JN", "Tar med kaffe. Är där om tio.", "21:41", Transport.SMS, 0, "CELL TOWER 4"),
    Conversation(3, "Lina // ORCA", "LO", "Echo received. All clear below.", "20:18", Transport.SECURE, 1, "2.4 NM"),
    Conversation(4, "Verkstan", "VX", "Din beställning är klar att hämtas.", "18:52", Transport.SMS, 0, "CELL TOWER 2"),
    Conversation(5, "Niko", "NK", "Secure channel waiting for verification.", "YESTERDAY", Transport.SECURE, 0, "5.1 NM"),
)

private fun cellConversation(address: String, threadId: Long?): Conversation = Conversation(
    id = threadId?.let { (100_000L + it).toInt() } ?: address.hashCode(),
    name = address,
    initials = addressInitials(address),
    preview = "New SMS channel",
    time = "NOW",
    transport = Transport.SMS,
    distance = "ANDROID TELEPHONY",
    smsAddress = address,
    threadId = threadId,
)

private fun addressInitials(address: String): String {
    val compact = address.filter { it.isLetterOrDigit() }
    return compact.takeLast(2).uppercase().ifBlank { "??" }
}

private fun formatMessageTime(timestamp: Long): String {
    val message = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    val now = Instant.now().atZone(ZoneId.systemDefault())
    return if (message.toLocalDate() == now.toLocalDate()) {
        message.format(DateTimeFormatter.ofPattern("HH:mm"))
    } else {
        message.format(DateTimeFormatter.ofPattern("MMM d"))
    }
}
