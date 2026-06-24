package com.example

import android.annotation.SuppressLint
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import com.example.data.IPTVChannel
import com.example.data.M3UPlaylist
import com.example.ui.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: IptvViewModel
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    // Retro visual UI states
    private var showSettings by mutableStateOf(false)
    private var showChannelGuide by mutableStateOf(false)
    private var showExitDialog by mutableStateOf(false)
    private var crtState by mutableStateOf(CrtScreenState.IDLE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            viewModel = viewModel()
            
            // Observe the CRT screen off animations
            LaunchedEffect(crtState) {
                if (crtState == CrtScreenState.OFF) {
                    finish()
                }
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. Core ExoPlayer screen
                    val activeChannel by viewModel.activeChannel.collectAsState()
                    val isStaticActive by viewModel.isStaticActive.collectAsState()

                    val isStreamError by viewModel.isStreamError.collectAsState()
                    val streamErrorMessage by viewModel.streamErrorMessage.collectAsState()
                    val isStreamBuffering by viewModel.isStreamBuffering.collectAsState()

                    if (activeChannel != null && crtState == CrtScreenState.IDLE) {
                        AndroidVideoPlayer(
                            url = activeChannel!!.url,
                            viewModel = viewModel,
                            onPlayerCreated = { player ->
                                exoPlayer = player
                                // Setup media session
                                mediaSession = MediaSession.Builder(this@MainActivity, player).build()
                            },
                            onPlayerReleased = {
                                mediaSession?.release()
                                mediaSession = null
                                exoPlayer = null
                            }
                        )
                    } else if (activeChannel == null && !isStaticActive) {
                        // Empty State / Initial screen
                        EmptyTVState()
                    }

                    // 2. Retro Static Noise overlay (ঝিরঝির)
                    if (isStaticActive || viewModel.isTuning.collectAsState().value || isStreamError) {
                        CrtStaticNoise()
                    }

                    // 2b. Stream Error Warning Box Overlay (CRT No Signal)
                    if (isStreamError && !isStaticActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .border(2.dp, Color.Red, RoundedCornerShape(4.dp))
                                    .background(Color(0xFF2A0C0C))
                                    .padding(24.dp)
                            ) {
                                Text(
                                    text = "⚠️ NO SIGNAL ⚠️",
                                    color = Color.Red,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "CHECK PLAYLIST SOURCE OR NETWORK",
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                                if (streamErrorMessage.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = streamErrorMessage.uppercase(),
                                        color = Color.Yellow,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    // 2c. Stream Buffering / "Locking Signal" Overlay indicator
                    if (isStreamBuffering && !isStaticActive && !isStreamError) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(Color(0xFF1A120A), RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0xFFFFB000), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFFFFB000), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "LOCKING SIGNAL...",
                                    color = Color(0xFFFFB000),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 3. Vintage CRT Scanline and Screen bulge filters
                    CrtScanlineOverlay()

                    // 4. Custom Retro OSD Indicators
                    IptvOsdOverlay(viewModel = viewModel)

                    // 5. Gear icon (top gear Settings trigger)
                    SettingsTriggerButton(
                        onTrigger = { showSettings = true },
                        visible = !showSettings && !showExitDialog && !showChannelGuide
                    )

                    // 6. Sliding vintage Set-Top Box Settings menu
                    AnimatedVisibility(
                        visible = showSettings,
                        enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        RetroSettingsMenu(
                            viewModel = viewModel,
                            onClose = { showSettings = false }
                        )
                    }

                    // 7. Channel list drawer (slides in from left)
                    AnimatedVisibility(
                        visible = showChannelGuide,
                        enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
                        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it }),
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        RetroChannelDrawer(
                            viewModel = viewModel,
                            onClose = { showChannelGuide = false },
                            onOpenSettings = {
                                showChannelGuide = false
                                showSettings = true
                            }
                        )
                    }

                    // 8. Exit confirmation box
                    if (showExitDialog) {
                        RetroExitDialog(
                            onConfirm = {
                                showExitDialog = false
                                crtState = CrtScreenState.COLLAPSING_Y
                            },
                            onCancel = {
                                showExitDialog = false
                            }
                        )
                    }

                    // 9. CRT Screen Off Effect overlay
                    CrtScreenOffOverlay(
                        state = crtState,
                        onAnimationFinished = {
                            crtState = when (crtState) {
                                CrtScreenState.COLLAPSING_Y -> CrtScreenState.COLLAPSING_X
                                CrtScreenState.COLLAPSING_X -> CrtScreenState.OFF
                                else -> CrtScreenState.IDLE
                            }
                        }
                    )
                }
            }
        }
    }

    /**
     * Intercept physical TV remote key-press dispatchings.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)
        if (crtState != CrtScreenState.IDLE) return true

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (showSettings || showExitDialog || showChannelGuide) {
                    return super.onKeyDown(keyCode, event)
                } else {
                    viewModel.zapNextChannel()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (showSettings || showExitDialog || showChannelGuide) {
                    return super.onKeyDown(keyCode, event)
                } else {
                    viewModel.zapPreviousChannel()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!showSettings && !showExitDialog && !showChannelGuide) {
                    showChannelGuide = true
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!showSettings && !showExitDialog && !showChannelGuide) {
                    showSettings = true
                    return true
                }
            }
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9 -> {
                if (!showSettings && !showExitDialog) {
                    val digit = keyCode - KeyEvent.KEYCODE_0
                    viewModel.pressNumericKey(digit)
                    return true
                }
            }
            KeyEvent.KEYCODE_NUMPAD_0, KeyEvent.KEYCODE_NUMPAD_1, KeyEvent.KEYCODE_NUMPAD_2, KeyEvent.KEYCODE_NUMPAD_3,
            KeyEvent.KEYCODE_NUMPAD_4, KeyEvent.KEYCODE_NUMPAD_5, KeyEvent.KEYCODE_NUMPAD_6, KeyEvent.KEYCODE_NUMPAD_7,
            KeyEvent.KEYCODE_NUMPAD_8, KeyEvent.KEYCODE_NUMPAD_9 -> {
                if (!showSettings && !showExitDialog) {
                    val digit = keyCode - KeyEvent.KEYCODE_NUMPAD_0
                    viewModel.pressNumericKey(digit)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (!showSettings && !showExitDialog && !showChannelGuide) {
                    // Immediate channel confirmation if user is typing a channel index
                    if (viewModel.inputBufferText.value.isNotEmpty()) {
                        viewModel.commitNumpadInput()
                        return true
                    } else {
                        // Toggle Channel Guide / Settings Drawer
                        showChannelGuide = true
                        return true
                    }
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                viewModel.adjustVolume(increment = true)
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                viewModel.adjustVolume(increment = false)
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (showSettings) {
                    showSettings = false
                    return true
                } else if (showChannelGuide) {
                    showChannelGuide = false
                    return true
                } else if (showExitDialog) {
                    showExitDialog = false
                    return true
                } else {
                    showExitDialog = true
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

/**
 * Android Video Player Composable wrapping ExoPlayer.
 */
@Composable
fun AndroidVideoPlayer(
    url: String,
    viewModel: com.example.ui.IptvViewModel,
    onPlayerCreated: (ExoPlayer) -> Unit,
    onPlayerReleased: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var playerInstance by remember { mutableStateOf<ExoPlayer?>(null) }
    val aspectRatioMode by viewModel.aspectRatioMode.collectAsState()

    val resizeMode = when (aspectRatioMode) {
        "FIT" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        "ZOOM" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
    }

    LaunchedEffect(url) {
        if (playerInstance == null) {
            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    2500, // minBufferMs
                    5000, // maxBufferMs
                    500,  // bufferForPlaybackMs
                    1000  // bufferForPlaybackAfterRebufferMs
                )
                .build()

            val player = ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build().apply {
                    playWhenReady = true
                    repeatMode = ExoPlayer.REPEAT_MODE_ONE
                }

            player.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        androidx.media3.common.Player.STATE_BUFFERING -> {
                            viewModel.setStreamBuffering(true)
                            viewModel.setStreamError(false)
                        }
                        androidx.media3.common.Player.STATE_READY -> {
                            viewModel.setStreamBuffering(false)
                            viewModel.setStreamError(false)
                        }
                        androidx.media3.common.Player.STATE_ENDED -> {
                            viewModel.setStreamBuffering(false)
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("AndroidVideoPlayer", "ExoPlayer error: ${error.message}", error)
                    viewModel.setStreamBuffering(false)
                    val cleanMsg = when (error.errorCode) {
                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "CONNECTION TIMEOUT"
                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "BAD HTTP STATUS"
                        androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "UNSUPPORTED CODEC"
                        else -> "SIGNAL INTERRUPTED"
                    }
                    viewModel.setStreamError(true, "$cleanMsg (${error.errorCodeName})")
                }
            })

            playerInstance = player
            onPlayerCreated(player)
        }

        playerInstance?.let { player ->
            val mediaItem = androidx.media3.common.MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerInstance?.release()
            onPlayerReleased()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = playerInstance
                useController = false
                this.resizeMode = resizeMode
            }
        },
        update = { view ->
            view.player = playerInstance
            view.resizeMode = resizeMode
        },
        modifier = modifier.fillMaxSize()
    )
}

/**
 * Renders the OSD overlays (Monospace font channel indexes, block volumes, and auto-tuning).
 */
@Composable
fun IptvOsdOverlay(
    viewModel: IptvViewModel,
    modifier: Modifier = Modifier
) {
    val activeChannel by viewModel.activeChannel.collectAsState()
    val isTuning by viewModel.isTuning.collectAsState()
    val tuningFreq by viewModel.tuningFrequency.collectAsState()
    val tuningStatus by viewModel.tuningStatusText.collectAsState()
    val volumeLevel by viewModel.currentVolume.collectAsState()
    val isVolumeVisible by viewModel.volumeDisplayVisible.collectAsState()
    val numpadBuffer by viewModel.inputBufferText.collectAsState()

    val monospaceFont = FontFamily.Monospace

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp)
    ) {
        // Top-Right: Monospace Green Channel Info (AV-1, CH xx)
        if (activeChannel != null && !isTuning) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "AV-1",
                    color = Color(0xFF00FF00),
                    fontFamily = monospaceFont,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "CH ${String.format("%02d", activeChannel!!.channelNumber)}",
                    color = Color(0xFF00FF00),
                    fontFamily = monospaceFont,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = activeChannel!!.name.uppercase(),
                    color = Color(0xFF00FF00),
                    fontFamily = monospaceFont,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Center: Debounced Numeric Input Display (e.g. "GOTO CH 12_")
        if (numpadBuffer.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(2.dp, Color(0xFF00FF00), RoundedCornerShape(4.dp))
                    .padding(24.dp)
            ) {
                Text(
                    text = "TUNING CH: $numpadBuffer-",
                    color = Color(0xFF00FF00),
                    fontFamily = monospaceFont,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom-Left: Volume bar block OSD (▰▰▰▰▰▰▰▱▱▱)
        if (isVolumeVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color(0xFF00FF00))
                    .padding(12.dp)
            ) {
                Text(
                    text = "VOLUME: $volumeLevel/10",
                    color = Color(0xFF00FF00),
                    fontFamily = monospaceFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row {
                    for (i in 1..10) {
                        Text(
                            text = if (i <= volumeLevel) "▰" else "▱",
                            color = Color(0xFF00FF00),
                            fontSize = 18.sp,
                            fontFamily = monospaceFont
                        )
                    }
                }
            }
        }

        // Bottom Center: Auto-Tuning scale & frequency needle
        if (isTuning) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.8f)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(2.dp, Color(0xFFFFFF00), RoundedCornerShape(8.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tuningStatus,
                    color = Color(0xFF00FF00),
                    fontFamily = monospaceFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Frequency pointer track (45.25 MHz to 800 MHz)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .background(Color(0xFF112211))
                        .border(1.dp, Color(0xFF00FF00))
                ) {
                    val scalePercent = (tuningFreq - 45.25f) / (800.00f - 45.25f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(scalePercent.coerceIn(0f, 1f))
                            .background(Color(0xFFFFB000)) // Amber needle
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("VL (45.25 MHz)", color = Color(0xFF888888), fontSize = 10.sp, fontFamily = monospaceFont)
                    Text("VH (175.25 MHz)", color = Color(0xFF888888), fontSize = 10.sp, fontFamily = monospaceFont)
                    Text("UHF (800.00 MHz)", color = Color(0xFF888888), fontSize = 10.sp, fontFamily = monospaceFont)
                }
            }
        }
    }
}

/**
 * Transparent Settings Gear Trigger Button. Focusable via TV D-pad.
 */
@Composable
fun SettingsTriggerButton(
    onTrigger: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // Auto-request focus so it's directly tactile with physical buttons
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.TopStart
    ) {
        IconButton(
            onClick = onTrigger,
            modifier = Modifier
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .background(
                    if (isFocused) Color(0xFF00FF00).copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 2.dp,
                    color = if (isFocused) Color(0xFF00FF00) else Color.Transparent,
                    shape = RoundedCornerShape(24.dp)
                )
                .size(48.dp)
                .testTag("settings_gear_button")
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Open Retro Settings Menu",
                tint = if (isFocused) Color(0xFF00FF00) else Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Slide-out Retro STB Playlist Configuration Settings Panel.
 */
@Composable
fun RetroSettingsMenu(
    viewModel: IptvViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playlists by viewModel.playlists.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()
    val importProgressPercent by viewModel.importProgressPercent.collectAsState()
    val importChannelCount by viewModel.importChannelCount.collectAsState()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var playlistName by remember { mutableStateOf("") }
    var playlistUrl by remember { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }

    // Custom monospace/pixel theme colors
    val bgColor = Color(0xFF0C120C)
    val phosphorGreen = Color(0xFF00FF00)
    val amber = Color(0xFFFFB000)

    // Hide keyboard and clear focus when this screen is dismissed/disposed
    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    // Handle import feedback
    LaunchedEffect(Unit) {
        viewModel.importedPlaylistResult.collectLatest { result ->
            if (result.isSuccess) {
                Toast.makeText(context, "M3U PLAYLIST IMPORTED SUCCESSFULLY!", Toast.LENGTH_LONG).show()
                playlistName = ""
                playlistUrl = ""
                keyboardController?.hide()
                focusManager.clearFocus()
            } else {
                Toast.makeText(context, "ERROR: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(420.dp)
            .background(bgColor)
            .border(2.dp, phosphorGreen)
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Text(
                text = "SYSTEM CONFIGURATION",
                color = phosphorGreen,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Input Fields Row
            Text(
                text = "ADD NEW M3U PLAYLIST URL:",
                color = amber,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                enabled = !isImporting,
                label = { Text("PLAYLIST LABEL", color = phosphorGreen, fontFamily = FontFamily.Monospace) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = phosphorGreen,
                    unfocusedBorderColor = phosphorGreen.copy(alpha = 0.5f),
                    focusedLabelColor = phosphorGreen,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledBorderColor = phosphorGreen.copy(alpha = 0.2f),
                    disabledLabelColor = phosphorGreen.copy(alpha = 0.2f),
                    disabledTextColor = Color.Gray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = playlistUrl,
                onValueChange = { playlistUrl = it },
                enabled = !isImporting,
                label = { Text("M3U URL", color = phosphorGreen, fontFamily = FontFamily.Monospace) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = phosphorGreen,
                    unfocusedBorderColor = phosphorGreen.copy(alpha = 0.5f),
                    focusedLabelColor = phosphorGreen,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledBorderColor = phosphorGreen.copy(alpha = 0.2f),
                    disabledLabelColor = phosphorGreen.copy(alpha = 0.2f),
                    disabledTextColor = Color.Gray
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (playlistName.isNotEmpty() && playlistUrl.isNotEmpty() && !isImporting) {
                            viewModel.importPlaylist(playlistName, playlistUrl)
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Load Playlist button / Progress Indicator
            if (isImporting) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, amber, RoundedCornerShape(4.dp))
                        .background(Color(0xFF161002))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚡ LOADING PLAYLIST...",
                            color = amber,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$importProgressPercent%",
                            color = amber,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Retro-character progress bar [████░░░░░░]
                    val totalTicks = 20
                    val filledTicks = (importProgressPercent * totalTicks / 100).coerceIn(0, totalTicks)
                    val emptyTicks = totalTicks - filledTicks
                    val progressBarString = "[" + "█".repeat(filledTicks) + "░".repeat(emptyTicks) + "]"

                    Text(
                        text = progressBarString,
                        color = amber,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "STATUS: ${importStatus.uppercase()}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "CHANNELS FETCHED: $importChannelCount",
                        color = phosphorGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                var isLoadButtonFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        if (playlistName.isNotEmpty() && playlistUrl.isNotEmpty()) {
                            viewModel.importPlaylist(playlistName, playlistUrl)
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        } else {
                            Toast.makeText(context, "PLEASE FILL IN BOTH FIELDS", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLoadButtonFocused) phosphorGreen else Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isLoadButtonFocused = it.isFocused }
                        .border(1.dp, phosphorGreen),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "IMPORT PLAYLIST SOURCE",
                        color = if (isLoadButtonFocused) Color.Black else phosphorGreen,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "ACTIVE SOURCES:",
                color = amber,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Active sources list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(playlists) { playlist ->
                    var isCardFocused by remember { mutableStateOf(false) }
                    var isDeleteFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side: Clickable/Focusable playlist info button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    1.dp,
                                    if (isCardFocused) phosphorGreen else phosphorGreen.copy(alpha = 0.3f),
                                    RoundedCornerShape(4.dp)
                                )
                                .background(if (isCardFocused) Color(0xFF122212) else Color.Transparent, RoundedCornerShape(4.dp))
                                .onFocusChanged { isCardFocused = it.isFocused }
                                .clickable {
                                    viewModel.selectPlaylist(playlist)
                                    onClose()
                                }
                                .focusable()
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = playlist.name.uppercase(),
                                    color = phosphorGreen,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = playlist.url,
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Right side: Focusable, clickable delete icon
                        IconButton(
                            onClick = { viewModel.deletePlaylist(playlist.id) },
                            modifier = Modifier
                                .size(40.dp)
                                .border(
                                    1.dp,
                                    if (isDeleteFocused) Color.Red else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                )
                                .background(if (isDeleteFocused) Color(0xFF2A0C0C) else Color.Transparent, RoundedCornerShape(4.dp))
                                .onFocusChanged { isDeleteFocused = it.isFocused }
                                .focusable()
                                .testTag("delete_playlist_${playlist.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Wipe Playlist Source",
                                tint = if (isDeleteFocused) Color.Red else Color.Red.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Auto Scan button
            var isTuneBtnFocused by remember { mutableStateOf(false) }
            Button(
                onClick = { viewModel.runAnalogAutoTuning(isFirstLaunch = false) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTuneBtnFocused) phosphorGreen else Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isTuneBtnFocused = it.isFocused }
                    .border(1.dp, phosphorGreen),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "RUN ANALOG AUTO-TUNING",
                    color = if (isTuneBtnFocused) Color.Black else phosphorGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Aspect Ratio configuration
            val aspectRatioMode by viewModel.aspectRatioMode.collectAsState()
            var isAspectBtnFocused by remember { mutableStateOf(false) }
            Button(
                onClick = { viewModel.toggleAspectRatio() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAspectBtnFocused) phosphorGreen else Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isAspectBtnFocused = it.isFocused }
                    .border(1.dp, phosphorGreen),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "CRT ASPECT RATIO: $aspectRatioMode",
                    color = if (isAspectBtnFocused) Color.Black else phosphorGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Close button
            var isCloseBtnFocused by remember { mutableStateOf(false) }
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCloseBtnFocused) amber else Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isCloseBtnFocused = it.isFocused }
                    .border(1.dp, amber),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "CLOSE CONFIGURATION",
                    color = if (isCloseBtnFocused) Color.Black else amber,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * Slide-out Retro STB Channel Drawer (Slides from the left).
 */
@Composable
fun RetroChannelDrawer(
    viewModel: IptvViewModel,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val channels by viewModel.channels.collectAsState()
    val activeChannel by viewModel.activeChannel.collectAsState()
    val focusRequester = remember { FocusRequester() }

    val bgColor = Color(0xFF0C120C)
    val phosphorGreen = Color(0xFF00FF00)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(360.dp)
            .background(bgColor)
            .border(2.dp, phosphorGreen)
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "STB CHANNEL LIST",
                color = phosphorGreen,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(channels) { channel ->
                    var isFocused by remember { mutableStateOf(false) }
                    val isPlaying = activeChannel?.id == channel.id

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(
                                1.dp,
                                if (isFocused) phosphorGreen else if (isPlaying) phosphorGreen.copy(alpha = 0.5f) else Color.Transparent
                            )
                            .background(
                                if (isFocused) Color(0xFF122212) else if (isPlaying) Color(0xFF061106) else Color.Transparent
                            )
                            .onFocusChanged { isFocused = it.isFocused }
                            .clickable {
                                viewModel.setActiveChannel(channel)
                                onClose()
                            }
                            .focusable()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format("%02d", channel.channelNumber),
                            color = if (isFocused || isPlaying) phosphorGreen else Color.Gray,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(36.dp)
                        )

                        Text(
                            text = channel.name.uppercase(),
                            color = if (isFocused) Color.White else if (isPlaying) phosphorGreen else Color.LightGray,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (isPlaying) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Active channel indicator",
                                tint = phosphorGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row (Settings & Dismiss)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Settings Button
                var isSettingsFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSettingsFocused) phosphorGreen else Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isSettingsFocused = it.isFocused }
                        .border(1.dp, phosphorGreen),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "SETTINGS",
                        color = if (isSettingsFocused) Color.Black else phosphorGreen,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Close Button
                var isCloseFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCloseFocused) phosphorGreen else Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isCloseFocused = it.isFocused }
                        .border(1.dp, phosphorGreen),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "DISMISS",
                        color = if (isCloseFocused) Color.Black else phosphorGreen,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * Vintage 90s Exit confirmation dialog overlay.
 */
@Composable
fun RetroExitDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val phosphorGreen = Color(0xFF00FF00)
    val deepGreen = Color(0xFF0A140A)
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .background(deepGreen)
                .border(2.dp, phosphorGreen, RoundedCornerShape(8.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "WARNING",
                color = Color.Red,
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = "EXIT SR IPTV?",
                color = phosphorGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                var isYesFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isYesFocused) phosphorGreen else Color.Transparent
                    ),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { isYesFocused = it.isFocused }
                        .border(1.dp, phosphorGreen),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "YES",
                        color = if (isYesFocused) Color.Black else phosphorGreen,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                var isNoFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNoFocused) phosphorGreen else Color.Transparent
                    ),
                    modifier = Modifier
                        .onFocusChanged { isNoFocused = it.isFocused }
                        .border(1.dp, phosphorGreen),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "NO",
                        color = if (isNoFocused) Color.Black else phosphorGreen,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * Beautiful static placeholder background when there are no channels yet.
 */
@Composable
fun EmptyTVState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050F05)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "AV-1",
                color = Color(0xFF00FF00),
                fontFamily = FontFamily.Monospace,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "NO SIGNAL - PLEASE ADD PLAYLIST IN CONFIGURATION",
                color = Color(0xFF00FF00),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
