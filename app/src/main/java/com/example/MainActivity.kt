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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
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
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: IptvViewModel
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    // Retro visual UI states
    private var showSettings by mutableStateOf(false)
    private var showChannelGuide by mutableStateOf(false)
    private var showExitDialog by mutableStateOf(false)
    private var crtState by mutableStateOf(CrtScreenState.IDLE)

    // Retro Bitmap Fonts
    private val retroFonts = listOf(
        FontFamily.Monospace,
        FontFamily.Serif,
        FontFamily.SansSerif
    )

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
                    val isKeyboardVisible by viewModel.isKeyboardVisible.collectAsState()

                    val isGridViewVisible by viewModel.isGridViewVisible.collectAsState()

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

                    // Grid View Overlay
                    if (isGridViewVisible) {
                        RetroGridView(
                            viewModel = viewModel,
                            onClose = { viewModel.toggleGridView() }
                        )
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
                                    .border(2.dp, Color.Red, RoundedCornerShape(0.5.vw()))
                                    .background(Color(0xFF2A0C0C))
                                    .padding(4.vw())
                            ) {
                                Text(
                                    text = if (streamErrorMessage.contains("RECONNECTING")) "📡 SIGNAL LOST 📡" else "⚠️ NO SIGNAL ⚠️",
                                    color = Color.Red,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 2.5.vh().value.sp
                                )
                                Spacer(modifier = Modifier.height(1.vh()))
                                Text(
                                    text = "CHECK PLAYLIST SOURCE OR NETWORK",
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 1.5.vh().value.sp
                                )
                                if (streamErrorMessage.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(0.8.vh()))
                                    Text(
                                        text = streamErrorMessage.uppercase(),
                                        color = Color.Yellow,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 1.2.vh().value.sp
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
                                .padding(4.vw()),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(Color(0xFF1A120A), RoundedCornerShape(0.4.vw()))
                                    .border(1.dp, Color(0xFFFFB000), RoundedCornerShape(0.4.vw()))
                                    .padding(horizontal = 2.vw(), vertical = 1.vh())
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

                    // 2d. Retro Audio Spectrum Visualizer
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.BottomStart) {
                        AudioSpectrumVisualizer(
                            isBuffering = isStreamBuffering,
                            isError = isStreamError || isStaticActive,
                            isPlaying = activeChannel != null
                        )
                    }

                    // 2e. Technical Stats Overlay
                    TechnicalStatsOverlay(viewModel = viewModel)

                    // 2f. Parental PIN Entry
                    PinEntryDialog(viewModel = viewModel)

                    // 2g. CRT Power-On Effect
                    CrtPowerOnEffect(viewModel = viewModel)

                    // 3. Vintage CRT Scanline and Screen bulge filters
                    CrtScanlineOverlay()

                    // 4. Custom Retro OSD Indicators
                    IptvOsdOverlay(viewModel = viewModel)

                    // 4b. Tuning Static Transition Effect
                    TuningStaticOverlay(isActive = isStaticActive)

                    // 4c. Retro Search Keyboard
                    if (isKeyboardVisible) {
                        RetroKeyboard(
                            viewModel = viewModel,
                            onClose = { viewModel.toggleKeyboard(false) }
                        )
                    }

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

                    // 9. Playlist Importing overlay
                    PlaylistImportingOverlay(viewModel = viewModel)

                    // 10. CRT Screen Off Effect overlay
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

        val isLocked = viewModel.isQuickLocked.value

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isLocked) return true
                if (showSettings || showExitDialog || showChannelGuide) {
                    return super.onKeyDown(keyCode, event)
                } else {
                    viewModel.zapNextChannel()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isLocked) return true
                if (showSettings || showExitDialog || showChannelGuide) {
                    return super.onKeyDown(keyCode, event)
                } else {
                    viewModel.zapPreviousChannel()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isLocked) return true
                if (!showSettings && !showExitDialog && !showChannelGuide) {
                    showChannelGuide = true
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isLocked) return true
                if (!showSettings && !showExitDialog && !showChannelGuide) {
                    showSettings = true
                    return true
                }
            }
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9 -> {
                if (isLocked) return true
                if (!showSettings && !showExitDialog) {
                    val digit = keyCode - KeyEvent.KEYCODE_0
                    viewModel.pressNumericKey(digit)
                    return true
                }
            }
            KeyEvent.KEYCODE_NUMPAD_0, KeyEvent.KEYCODE_NUMPAD_1, KeyEvent.KEYCODE_NUMPAD_2, KeyEvent.KEYCODE_NUMPAD_3,
            KeyEvent.KEYCODE_NUMPAD_4, KeyEvent.KEYCODE_NUMPAD_5, KeyEvent.KEYCODE_NUMPAD_6, KeyEvent.KEYCODE_NUMPAD_7,
            KeyEvent.KEYCODE_NUMPAD_8, KeyEvent.KEYCODE_NUMPAD_9 -> {
                if (isLocked) return true
                if (!showSettings && !showExitDialog) {
                    val digit = keyCode - KeyEvent.KEYCODE_NUMPAD_0
                    viewModel.pressNumericKey(digit)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (!showSettings && !showExitDialog && !showChannelGuide) {
                    event.startTracking()
                    return true
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
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_M -> {
                if (viewModel.isGridViewVisible.value) {
                    viewModel.toggleGridView()
                    viewModel.toggleTechnicalStats() // Tech stats on
                } else if (viewModel.technicalStatsVisible.value) {
                    viewModel.toggleTechnicalStats() // Tech stats off
                } else {
                    viewModel.toggleGridView() // Grid view on
                }
                return true
            }
            KeyEvent.KEYCODE_LAST_CHANNEL -> {
                viewModel.recallChannel()
                return true
            }
            KeyEvent.KEYCODE_PROG_RED -> {
                // Cycle sleep timer: null -> 30 -> 60 -> 90 -> null
                val current = viewModel.sleepTimerMinutes.value
                val next = when (current) {
                    null -> 30
                    30 -> 60
                    60 -> 90
                    else -> null
                }
                viewModel.setSleepTimer(next)
                return true
            }
            KeyEvent.KEYCODE_PROG_GREEN -> {
                viewModel.toggleKeyboard(!viewModel.isKeyboardVisible.value)
                return true
            }
            KeyEvent.KEYCODE_PROG_YELLOW -> {
                viewModel.updateKeyboardInput("")
                return true
            }
            KeyEvent.KEYCODE_PROG_BLUE -> {
                viewModel.toggleAspectRatio()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (showSettings) {
                    showSettings = false
                    return true
                } else if (showChannelGuide) {
                    showChannelGuide = false
                    return true
                } else if (viewModel.isGridViewVisible.value) {
                    viewModel.toggleGridView()
                    return true
                } else if (viewModel.technicalStatsVisible.value) {
                    viewModel.toggleTechnicalStats()
                    return true
                } else if (showExitDialog) {
                    showExitDialog = false
                    return true
                } else {
                    // Recall feature mapped to Back when no menus are open
                    viewModel.recallChannel()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event?.isTracking == true && !event.isLongPress) {
                if (viewModel.inputBufferText.value.isNotEmpty()) {
                    viewModel.commitNumpadInput()
                } else {
                    showChannelGuide = true
                }
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            viewModel.toggleQuickLock()
            val state = if (viewModel.isQuickLocked.value) "LOCKED" else "UNLOCKED"
            Toast.makeText(this, "REMOTE NAVIGATION $state", Toast.LENGTH_SHORT).show()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
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
                    15000, // minBufferMs: total buffer to keep
                    50000, // maxBufferMs
                    1000,  // bufferForPlaybackMs: wait for 1s of data before starting
                    2500   // bufferForPlaybackAfterRebufferMs
                )
                .build()

            val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setConnectTimeoutMs(30000) // Increased to 30s
                .setReadTimeoutMs(30000)    // Increased to 30s
                .setAllowCrossProtocolRedirects(true)
            
            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)

            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            val player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
                .build().apply {
                    playWhenReady = true
                    repeatMode = ExoPlayer.REPEAT_MODE_ONE
                    videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                }

            var retryCount = 0
            val maxRetries = 3

            player.addListener(object : androidx.media3.common.Player.Listener {
                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    val format = player.videoFormat
                    val codec = format?.sampleMimeType?.substringAfterLast("/") ?: "N/A"
                    val bitrateKbps = if (format != null && format.bitrate != androidx.media3.common.Format.NO_VALUE) {
                        "${format.bitrate / 1000} KBPS"
                    } else "N/A"
                    viewModel.updateStreamStats(videoSize.width, videoSize.height, codec, bitrateKbps)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        androidx.media3.common.Player.STATE_BUFFERING -> {
                            viewModel.setStreamBuffering(true)
                            viewModel.setStreamError(false)
                        }
                        androidx.media3.common.Player.STATE_READY -> {
                            viewModel.setStreamBuffering(false)
                            viewModel.setStreamError(false)
                            retryCount = 0
                        }
                        androidx.media3.common.Player.STATE_ENDED -> {
                            viewModel.setStreamBuffering(false)
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("AndroidVideoPlayer", "ExoPlayer error: ${error.message}", error)
                    if (retryCount < maxRetries) {
                        retryCount++
                        android.util.Log.d("AndroidVideoPlayer", "Retrying stream (Attempt $retryCount of $maxRetries)...")
                        viewModel.setStreamError(true, "RECONNECTING... ($retryCount/$maxRetries)")
                        player.prepare()
                    } else {
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

    val currentVolume by viewModel.currentVolume.collectAsState()
    LaunchedEffect(currentVolume) {
        playerInstance?.volume = currentVolume / 10f
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
 * Full-screen visual loading state with percentage indicator
 * Appears when playlist is being fetched/parsed
 */
@Composable
fun PlaylistImportingOverlay(viewModel: IptvViewModel) {
    val isImporting by viewModel.isImporting.collectAsState()
    val importProgressPercent by viewModel.importProgressPercent.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()
    val importChannelCount by viewModel.importChannelCount.collectAsState()

    if (isImporting) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .border(2.dp, Color(0xFF00FF00), RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A1A0A))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "FETCHING PLAYLIST DATA",
                    color = Color(0xFF00FF00),
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "$importProgressPercent%",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                val totalTicks = 20
                val filledTicks = (importProgressPercent * totalTicks / 100).coerceIn(0, totalTicks)
                val emptyTicks = totalTicks - filledTicks
                val progressBarString = "[" + "█".repeat(filledTicks) + "░".repeat(emptyTicks) + "]"
                
                Text(
                    text = progressBarString,
                    color = Color(0xFF00FF00),
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = importStatus.uppercase(),
                    color = Color.Yellow,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "CHANNELS FOUND: $importChannelCount",
                    color = Color(0xFF00FF00),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Retro Audio Spectrum Visualizer
 */
@Composable
fun AudioSpectrumVisualizer(
    modifier: Modifier = Modifier,
    isBuffering: Boolean,
    isError: Boolean,
    isPlaying: Boolean // Can be inferred from not buffering and not erroring, plus active channel
) {
    if (isBuffering || isError || !isPlaying) return

    val barCount = 12
    val randoms = remember { List(barCount) { mutableStateOf(0f) } }

    LaunchedEffect(Unit) {
        while (true) {
            for (i in 0 until barCount) {
                // Peak is at the middle, edges are lower
                val base = 1f - kotlin.math.abs(i - barCount / 2f) / (barCount / 2f)
                val noise = (0..40).random() / 100f // 0.0 to 0.4
                randoms[i].value = (base * 0.4f + noise).coerceIn(0.1f, 1f)
            }
            delay(100) // Fast 10fps update for retro feel
        }
    }

    Row(
        modifier = modifier
            .height(24.dp)
            .padding(4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (i in 0 until barCount) {
            val barHeight by animateFloatAsState(
                targetValue = randoms[i].value,
                animationSpec = tween(durationMillis = 80),
                label = "barHeight"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(barHeight)
                    .background(Color(0xFF00FF00).copy(alpha = 0.8f))
            )
        }
    }
}

@Composable
fun Number.vw(): androidx.compose.ui.unit.Dp = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * this.toFloat() / 100f).dp

@Composable
fun Number.vh(): androidx.compose.ui.unit.Dp = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * this.toFloat() / 100f).dp

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
    val signalStrength by viewModel.signalStrength.collectAsState()
    val sleepTimer by viewModel.sleepTimerMinutes.collectAsState()
    val viewModelTime by viewModel.currentTime.collectAsState()
    val isLocked by viewModel.isQuickLocked.collectAsState()
    val fontIndex by viewModel.selectedFontIndex.collectAsState()

    val currentFont = when(fontIndex) {
        1 -> FontFamily.Serif
        2 -> FontFamily.SansSerif
        else -> FontFamily.Monospace
    }

    var osdVisible by remember { mutableStateOf(false) }

    LaunchedEffect(activeChannel) {
        if (activeChannel != null) {
            osdVisible = true
            delay(4000) // Show for 4 seconds
            osdVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(4.vw())
    ) {
        // Top-Right: Real-time Clock (7-segment style)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = if (activeChannel != null && !isTuning) 8.vh() else 0.dp) // Avoid overlapping signal bars
                .background(Color.Black.copy(alpha = 0.7f))
                .border(1.dp, Color(0xFF00FF00).copy(alpha = 0.3f))
                .padding(horizontal = 2.vw(), vertical = 0.5.vh())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLocked) {
                    Text(
                        text = "🔒",
                        color = Color.Red,
                        fontSize = 1.8.vh().value.sp,
                        modifier = Modifier.padding(end = 1.vw())
                    )
                }
                Text(
                    text = viewModelTime,
                    color = Color(0xFF00FF00),
                    fontFamily = currentFont,
                    fontSize = 2.5.vh().value.sp,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color(0xFF00FF00),
                            offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                            blurRadius = 8f
                        )
                    )
                )
            }
        }

        // Top-Left: Sleep Timer
        if (sleepTimer != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(1.vw())
            ) {
                Text(
                    text = "SLEEP: $sleepTimer MIN",
                    color = Color(0xFFFFB000), // Amber
                    fontFamily = currentFont,
                    fontSize = 1.8.vh().value.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Top-Right: Signal Strength Indicator
        if (activeChannel != null && !isTuning) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(1.vw()),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "SIGNAL",
                    color = Color(0xFF00FF00),
                    fontFamily = currentFont,
                    fontSize = 1.2.vh().value.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    val bars = 5
                    for (i in 1..bars) {
                        val barHeight = 0.5.vh() + (0.5.vh() * i.toFloat())
                        val isActive = signalStrength >= (i.toFloat() / bars)
                        Box(
                            modifier = Modifier
                                .width(0.8.vw())
                                .height(barHeight)
                                .padding(horizontal = 0.1.vw())
                                .background(if (isActive) Color(0xFF00FF00) else Color.DarkGray)
                        )
                    }
                }
                Text(
                    text = "${(signalStrength * 100).toInt()}%",
                    color = Color(0xFF00FF00),
                    fontFamily = currentFont,
                    fontSize = 1.2.vh().value.sp
                )
            }
        }

        // Bottom-Right: Monospace Green Channel Info & Time (AV-1, CH xx)
        if (activeChannel != null && !isTuning && osdVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(1.vw()),
                horizontalAlignment = Alignment.End
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "AV-1",
                        color = Color(0xFF00FF00),
                        fontFamily = currentFont,
                        fontSize = 2.2.vh().value.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 1.vw())
                    )
                    Text(
                        text = viewModelTime.take(5), // Show HH:mm only
                        color = Color(0xFF00FF00),
                        fontFamily = currentFont,
                        fontSize = 2.2.vh().value.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "CH ${String.format("%02d", activeChannel!!.channelNumber)}",
                    color = Color(0xFF00FF00),
                    fontFamily = currentFont,
                    fontSize = 3.vh().value.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = activeChannel!!.name.uppercase(),
                    color = Color(0xFF00FF00),
                    fontFamily = currentFont,
                    fontSize = 1.8.vh().value.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!activeChannel!!.groupTitle.isNullOrEmpty()) {
                    Text(
                        text = activeChannel!!.groupTitle!!.uppercase(),
                        color = Color(0xFF00AA00), // Slightly darker green for description
                        fontFamily = currentFont,
                        fontSize = 1.5.vh().value.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 0.2.vh())
                    )
                }
                
                Spacer(modifier = Modifier.height(0.8.vh()))
                
                // EPG "Now Playing" block
                Column(
                    modifier = Modifier
                        .background(Color(0xFF0A1F0A).copy(alpha = 0.8f))
                        .border(1.dp, Color(0xFF00FF00).copy(alpha = 0.5f))
                        .padding(0.8.vw()),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "NOW PLAYING",
                        color = Color(0xFFFFB000), // Amber for EPG header
                        fontFamily = currentFont,
                        fontSize = 1.2.vh().value.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "NO EPG DATA AVAILABLE", // Placeholder for XMLTV fetcher
                        color = Color(0xFF00FF00),
                        fontFamily = currentFont,
                        fontSize = 1.5.vh().value.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Center: Debounced Numeric Input Display (e.g. "GOTO CH 12_")
        if (numpadBuffer.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(2.dp, Color(0xFF00FF00), RoundedCornerShape(0.4.vw()))
                    .padding(3.vw())
            ) {
                Text(
                    text = "TUNING CH: $numpadBuffer-",
                    color = Color(0xFF00FF00),
                    fontFamily = currentFont,
                    fontSize = 4.vh().value.sp,
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
                    .padding(1.5.vw())
            ) {
                Text(
                    text = "VOLUME: $volumeLevel/10",
                    color = Color(0xFF00FF00),
                    fontFamily = currentFont,
                    fontSize = 1.8.vh().value.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 0.5.vh())
                )
                Row {
                    for (i in 1..10) {
                        Text(
                            text = if (i <= volumeLevel) "▰" else "▱",
                            color = Color(0xFF00FF00),
                            fontSize = 2.2.vh().value.sp,
                            fontFamily = currentFont
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
                    .border(2.dp, Color(0xFFFFFF00), RoundedCornerShape(1.vw()))
                    .padding(2.vw()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tuningStatus,
                    color = Color(0xFF00FF00),
                    fontFamily = currentFont,
                    fontSize = 2.vh().value.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(1.5.vh()))

                // Frequency pointer track (45.25 MHz to 800 MHz)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.vh())
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

                Spacer(modifier = Modifier.height(0.8.vh()))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("VL (45.25 MHz)", color = Color(0xFF888888), fontSize = 1.2.vh().value.sp, fontFamily = currentFont)
                    Text("VH (175.25 MHz)", color = Color(0xFF888888), fontSize = 1.2.vh().value.sp, fontFamily = currentFont)
                    Text("UHF (800.00 MHz)", color = Color(0xFF888888), fontSize = 1.2.vh().value.sp, fontFamily = currentFont)
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

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val name = if (playlistName.isNotEmpty()) playlistName else "LOCAL M3U"
            viewModel.importPlaylistFromFile(name, uri, context)
        }
    }

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

            Spacer(modifier = Modifier.height(8.dp))

            // Quick ADD Sources
            Text(
                text = "QUICK ADD SOURCES (AUTO-UPDATE):",
                color = amber,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        playlistName = "BD CHANNELS"
                        playlistUrl = "https://iptv-org.github.io/iptv/countries/bd.m3u"
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = phosphorGreen),
                    border = BorderStroke(1.dp, phosphorGreen)
                ) {
                    Text("BD IPTV", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                OutlinedButton(
                    onClick = {
                        playlistName = "WORLD CHANNELS"
                        playlistUrl = "https://iptv-org.github.io/iptv/index.m3u"
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = phosphorGreen),
                    border = BorderStroke(1.dp, phosphorGreen)
                ) {
                    Text("WORLD IPTV", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // INTERFACE SETTINGS
            Text(
                text = "INTERFACE CUSTOMIZATION:",
                color = amber,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.cycleFont() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = phosphorGreen),
                    border = BorderStroke(1.dp, phosphorGreen)
                ) {
                    val fontIndex by viewModel.selectedFontIndex.collectAsState()
                    val fontName = when(fontIndex) {
                        1 -> "SERIF"
                        2 -> "SANS"
                        else -> "MONO"
                    }
                    Text("FONT: $fontName", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                OutlinedButton(
                    onClick = { viewModel.toggleQuickLock() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (viewModel.isQuickLocked.collectAsState().value) Color.Red else phosphorGreen),
                    border = BorderStroke(1.dp, if (viewModel.isQuickLocked.collectAsState().value) Color.Red else phosphorGreen)
                ) {
                    Text(if (viewModel.isQuickLocked.collectAsState().value) "UNLOCK NAV" else "LOCK NAV", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }

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
                var isFileButtonFocused by remember { mutableStateOf(false) }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            .weight(1f)
                            .onFocusChanged { isLoadButtonFocused = it.isFocused }
                            .border(1.dp, phosphorGreen),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "IMPORT URL",
                            color = if (isLoadButtonFocused) Color.Black else phosphorGreen,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFileButtonFocused) amber else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { isFileButtonFocused = it.isFocused }
                            .border(1.dp, amber),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "LOCAL M3U",
                            color = if (isFileButtonFocused) Color.Black else amber,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
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

    var searchQuery by remember { mutableStateOf("") }
    var showOnlyFavorites by remember { mutableStateOf(false) }
    val keyboardInput by viewModel.keyboardInput.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    val filteredChannels = remember(channels, searchQuery, showOnlyFavorites, keyboardInput, selectedCategory) {
        channels.filter { channel ->
            val effectiveSearch = if (keyboardInput.isNotEmpty()) keyboardInput else searchQuery
            val matchesSearch = if (effectiveSearch.isBlank()) true else channel.name.contains(effectiveSearch, ignoreCase = true)
            val matchesFav = if (showOnlyFavorites) channel.isFavorite else true
            val matchesCategory = if (selectedCategory == null) true else channel.groupTitle == selectedCategory
            matchesSearch && matchesFav && matchesCategory
        }
    }

    val categories = remember(channels) {
        listOf(null) + channels.mapNotNull { it.groupTitle }.distinct().sorted()
    }

    val bgColor = Color(0xFF0C120C)
    val phosphorGreen = Color(0xFF00FF00)
    val amber = Color(0xFFFFB000)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(420.dp) // Slightly wider to accommodate logo
            .background(bgColor)
            .border(2.dp, phosphorGreen)
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STB CHANNEL LIST",
                    color = phosphorGreen,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = { showOnlyFavorites = !showOnlyFavorites }) {
                    Icon(
                        imageVector = if (showOnlyFavorites) Icons.Default.Star else Icons.Outlined.Star,
                        contentDescription = "Toggle Favorites Filter",
                        tint = if (showOnlyFavorites) amber else Color.Gray
                    )
                }
            }

            // Category Filter Row
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    var isCatFocused by remember { mutableStateOf(false) }
                    val isSelected = selectedCategory == category
                    
                    Box(
                        modifier = Modifier
                            .background(if (isSelected) phosphorGreen else if (isCatFocused) Color(0xFF122212) else Color.Transparent)
                            .border(1.dp, phosphorGreen)
                            .onFocusChanged { isCatFocused = it.isFocused }
                            .clickable { viewModel.setCategory(category) }
                            .focusable()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = category?.uppercase() ?: "ALL",
                            color = if (isSelected) Color.Black else if (isCatFocused) Color.White else phosphorGreen,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("FILTER", color = phosphorGreen, fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = phosphorGreen,
                    unfocusedBorderColor = phosphorGreen.copy(alpha = 0.5f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    cursorColor = phosphorGreen
                ),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(filteredChannels, key = { it.id }) { channel ->
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
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                            viewModel.setActiveChannel(channel)
                                            onClose()
                                            true
                                        }
                                        Key.F, Key.I -> {
                                            viewModel.toggleFavorite(channel)
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
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
                        
                        // Dynamic Channel Logo
                        if (!channel.logoUrl.isNullOrEmpty()) {
                            coil.compose.AsyncImage(
                                model = channel.logoUrl,
                                contentDescription = "${channel.name} logo",
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(end = 8.dp)
                                    .background(Color.Black),
                                error = coil.compose.rememberAsyncImagePainter(android.R.drawable.ic_dialog_info), // Fallback
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(end = 8.dp)
                                    .background(Color.DarkGray)
                                    .border(1.dp, Color.Gray),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "TV",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Text(
                            text = channel.name.uppercase(),
                            color = if (isFocused) Color.White else if (isPlaying) phosphorGreen else Color.LightGray,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (channel.isFavorite) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Favorite",
                                tint = amber,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 4.dp)
                                    .clickable { viewModel.toggleFavorite(channel) }
                            )
                        } else if (isFocused) {
                            Icon(
                                imageVector = Icons.Outlined.Star,
                                contentDescription = "Add to Favorites",
                                tint = Color.Gray,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 4.dp)
                                    .clickable { viewModel.toggleFavorite(channel) }
                            )
                        }

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

@Composable
fun RetroGridView(
    viewModel: IptvViewModel,
    onClose: () -> Unit
) {
    val channels by viewModel.channels.collectAsState()
    val activeChannel by viewModel.activeChannel.collectAsState()
    val phosphorGreen = Color(0xFF00FF00)
    val bgColor = Color(0xFF0C120C)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(0.9f)
                .background(bgColor)
                .border(2.dp, phosphorGreen)
                .padding(24.dp)
                .clickable(enabled = false) { }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STB GRID SELECTION",
                    color = phosphorGreen,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "3X3 NAV MODE",
                    color = phosphorGreen.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(channels.take(18)) { channel -> // Limit to 18 for demo/performance
                    GridViewItem(
                        channel = channel,
                        isPlaying = activeChannel?.id == channel.id,
                        onClick = {
                            viewModel.setActiveChannel(channel)
                            onClose()
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "USE DPAD TO NAVIGATE | PRESS MENU TO EXIT",
                color = phosphorGreen,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun GridViewItem(
    channel: IPTVChannel,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val phosphorGreen = Color(0xFF00FF00)

    Box(
        modifier = Modifier
            .aspectRatio(16f / 10f)
            .background(if (isFocused) phosphorGreen.copy(alpha = 0.2f) else Color.Black)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) phosphorGreen else Color.DarkGray
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .focusable()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Thumbnail / Logo
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.logoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(0.7f),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.Red, RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Channel Name / EPG Placeholder
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isFocused) phosphorGreen else Color.Black)
                    .padding(4.dp)
            ) {
                Text(
                    text = channel.name.uppercase(),
                    color = if (isFocused) Color.Black else phosphorGreen,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "NO EPG DATA",
                    color = if (isFocused) Color.Black.copy(alpha = 0.7f) else Color.Gray,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun TuningStaticOverlay(isActive: Boolean) {
    if (!isActive) return

    val infiniteTransition = rememberInfiniteTransition(label = "static")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(50, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val dotSize = 4f
        
        // Draw random white/gray dots
        for (i in 0 until 500) {
            val x = (0..width.toInt()).random().toFloat()
            val y = (0..height.toInt()).random().toFloat()
            val gray = (100..255).random()
            drawRect(
                color = Color(gray, gray, gray).copy(alpha = alpha),
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(dotSize, dotSize)
            )
        }
    }
}

@Composable
fun RetroKeyboard(
    viewModel: IptvViewModel,
    onClose: () -> Unit
) {
    val keyboardInput by viewModel.keyboardInput.collectAsState()
    val phosphorGreen = Color(0xFF00FF00)
    val bgColor = Color(0xFF0C120C)
    
    val keys = listOf(
        listOf("A", "B", "C", "D", "E", "F"),
        listOf("G", "H", "I", "J", "K", "L"),
        listOf("M", "N", "O", "P", "Q", "R"),
        listOf("S", "T", "U", "V", "W", "X"),
        listOf("Y", "Z", "0", "1", "2", "3"),
        listOf("4", "5", "6", "7", "8", "9"),
        listOf("SPACE", "BACK", "CLEAR", "DONE")
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(450.dp)
                .background(bgColor)
                .border(2.dp, phosphorGreen)
                .padding(24.dp)
                .clickable(enabled = false) { }
        ) {
            Text(
                text = "RETRO SEARCH KEYBOARD",
                color = phosphorGreen,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Input display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(Color.Black)
                    .border(1.dp, phosphorGreen)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "$keyboardInput|",
                    color = phosphorGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Keys Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { key ->
                            var isFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .background(if (isFocused) phosphorGreen else Color.Transparent)
                                    .border(1.dp, phosphorGreen)
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .onKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && 
                                            (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)) {
                                            when (key) {
                                                "SPACE" -> viewModel.updateKeyboardInput(keyboardInput + " ")
                                                "BACK" -> if (keyboardInput.isNotEmpty()) viewModel.updateKeyboardInput(keyboardInput.dropLast(1))
                                                "CLEAR" -> viewModel.updateKeyboardInput("")
                                                "DONE" -> onClose()
                                                else -> viewModel.updateKeyboardInput(keyboardInput + key)
                                            }
                                            true
                                        } else false
                                    }
                                    .clickable {
                                        when (key) {
                                            "SPACE" -> viewModel.updateKeyboardInput(keyboardInput + " ")
                                            "BACK" -> if (keyboardInput.isNotEmpty()) viewModel.updateKeyboardInput(keyboardInput.dropLast(1))
                                            "CLEAR" -> viewModel.updateKeyboardInput("")
                                            "DONE" -> onClose()
                                            else -> viewModel.updateKeyboardInput(keyboardInput + key)
                                        }
                                    }
                                    .focusable(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = key,
                                    color = if (isFocused) Color.Black else phosphorGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TechnicalStatsOverlay(viewModel: IptvViewModel) {
    val visible by viewModel.technicalStatsVisible.collectAsState()
    if (!visible) return

    val resolution by viewModel.streamResolution.collectAsState()
    val codec by viewModel.streamCodec.collectAsState()
    val bitrate by viewModel.streamBitrate.collectAsState()
    val phosphorGreen = Color(0xFF00FF00)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.85f))
                .border(2.dp, phosphorGreen)
                .padding(24.dp)
        ) {
            Text(
                text = "STREAM TECHNICAL DATA",
                color = phosphorGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            val stats = listOf(
                "RESOLUTION" to resolution,
                "CODEC" to codec,
                "BITRATE" to bitrate,
                "TUNER" to "ANALOG-STB-9000",
                "SYNC" to "VERTICAL-LOCK"
            )

            stats.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth(0.4f), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "$label:", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Text(text = value, color = phosphorGreen, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PinEntryDialog(viewModel: IptvViewModel) {
    val visible by viewModel.isPinEntryVisible.collectAsState()
    if (!visible) return

    val pinBuffer by viewModel.pinBuffer.collectAsState()
    val phosphorGreen = Color(0xFF00FF00)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "PARENTAL LOCK",
                color = Color.Red,
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "ENTER 4-DIGIT PIN",
                color = phosphorGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (i in 0 until 4) {
                    val digit = if (i < pinBuffer.length) "*" else "_"
                    Text(
                        text = digit,
                        color = phosphorGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Hidden D-pad helper
            Box(modifier = Modifier.size(1.dp).onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val digit = when (event.key) {
                        Key.Zero, Key.NumPad0 -> 0
                        Key.One, Key.NumPad1 -> 1
                        Key.Two, Key.NumPad2 -> 2
                        Key.Three, Key.NumPad3 -> 3
                        Key.Four, Key.NumPad4 -> 4
                        Key.Five, Key.NumPad5 -> 5
                        Key.Six, Key.NumPad6 -> 6
                        Key.Seven, Key.NumPad7 -> 7
                        Key.Eight, Key.NumPad8 -> 8
                        Key.Nine, Key.NumPad9 -> 9
                        else -> -1
                    }
                    if (digit != -1) {
                        viewModel.pressPinDigit(digit)
                        true
                    } else false
                } else false
            }.focusable()) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Box(modifier = Modifier.focusRequester(focusRequester).focusable())
            }
        }
    }
}

@Composable
fun CrtPowerOnEffect(viewModel: IptvViewModel) {
    val isActive by viewModel.isPowerOnEffectActive.collectAsState()
    if (!isActive) return

    var animStarted by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (animStarted) 100f else 0f,
        animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing),
        label = "powerOnScale"
    )

    LaunchedEffect(Unit) {
        delay(300)
        animStarted = true
        delay(1000)
        viewModel.dismissPowerOnEffect()
    }

    if (scale < 100f) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = center
            val radius = (size.minDimension / 2) * (scale / 100f)
            
            // Draw background black
            drawRect(color = Color.Black)
            
            // Draw expanding white circle/dot
            if (scale > 0) {
                drawCircle(
                    color = Color.White,
                    radius = radius.coerceAtLeast(4f),
                    center = center
                )
            }
        }
    }
}
