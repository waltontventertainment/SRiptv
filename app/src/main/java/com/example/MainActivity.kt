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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import android.app.UiModeManager
import android.content.res.Configuration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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

    // Modern visual UI states
    private var showSettings by mutableStateOf(false)
    private var showChannelGuide by mutableStateOf(false)
    private var showSearchInGuide by mutableStateOf(false)
    private var showExitDialog by mutableStateOf(false)
    private var showPlayerControls by mutableStateOf(false)
    private var crtState by mutableStateOf(CrtScreenState.IDLE)
    private var isTvDevice by mutableStateOf(false)

    // Modern System Fonts
    private val modernFonts = listOf(
        FontFamily.SansSerif,
        FontFamily.Serif,
        FontFamily.Default
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Detect if running on a TV (Television or Leanback feature)
        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        isTvDevice = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                     packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
                     packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION)

        setContent {
            val keyboardController = LocalSoftwareKeyboardController.current
            viewModel = viewModel()
            
            var showSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(4000)
                showSplash = false
            }
            
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
                val context = LocalContext.current
                val audioManager = remember(context) {
                    context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                }
                var accumulatedDragX by remember { mutableStateOf(0f) }
                var accumulatedDragY by remember { mutableStateOf(0f) }

                Box(modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            viewModel.showOsd()
                        }
                    }
                    .pointerInput(isTvDevice) {
                        if (!isTvDevice) {
                            detectDragGestures(
                                onDragStart = {
                                    accumulatedDragX = 0f
                                    accumulatedDragY = 0f
                                },
                                onDragEnd = {
                                    accumulatedDragX = 0f
                                    accumulatedDragY = 0f
                                },
                                onDragCancel = {
                                    accumulatedDragX = 0f
                                    accumulatedDragY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    accumulatedDragX += dragAmount.x
                                    accumulatedDragY += dragAmount.y

                                    // Handle vertical swipes (Up/Down) for channel changes
                                    if (kotlin.math.abs(accumulatedDragY) > 150f) {
                                        if (accumulatedDragY < 0) {
                                            viewModel.zapNextChannel()
                                        } else {
                                            viewModel.zapPreviousChannel()
                                        }
                                        accumulatedDragX = 0f
                                        accumulatedDragY = 0f
                                    }

                                    // Handle horizontal swipes
                                    if (kotlin.math.abs(accumulatedDragX) > 150f) {
                                        if (accumulatedDragX < 0) {
                                            // Swipe Right to Left (Finger moves Left) -> Open Settings
                                            showSettings = true
                                        } else {
                                            // Swipe Left to Right (Finger moves Right)
                                            if (!showChannelGuide) {
                                                // 1st Swipe: Open Categories (Channel Guide)
                                                showChannelGuide = true
                                                showSearchInGuide = false
                                            } else {
                                                // 2nd Swipe: Show Search box inside the guide drawer
                                                showSearchInGuide = true
                                            }
                                        }
                                        accumulatedDragX = 0f
                                        accumulatedDragY = 0f
                                    }
                                }
                            )
                        }
                    }
                ) {
                    // 1. Core ExoPlayer screen
                    val activeChannel by viewModel.activeChannel.collectAsState()
                    val isStaticActive by viewModel.isStaticActive.collectAsState()

                    val isStreamError by viewModel.isStreamError.collectAsState()
                    val streamErrorMessage by viewModel.streamErrorMessage.collectAsState()
                    val isStreamBuffering by viewModel.isStreamBuffering.collectAsState()

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
                        ChannelGridView(
                            viewModel = viewModel,
                            onClose = { viewModel.toggleGridView() }
                        )
                    }

                    // 2. Retro Static Noise overlay (Removed per user request to provide clean video)
                    // if (isStaticActive || viewModel.isTuning.collectAsState().value || isStreamError) {
                    //    CrtStaticNoise()
                    // }

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
                                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .padding(4.vw())
                            ) {
                                Text(
                                    text = if (streamErrorMessage.contains("RECONNECTING")) "CONNECTING..." else "CONTENT UNAVAILABLE",
                                    color = Color.White,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 2.5.vh().value.sp
                                )
                                Spacer(modifier = Modifier.height(1.vh()))
                                Text(
                                    text = "PLEASE CHECK YOUR CONNECTION",
                                    color = Color.LightGray,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 1.5.vh().value.sp
                                )
                                if (streamErrorMessage.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(0.8.vh()))
                                    Text(
                                        text = streamErrorMessage.uppercase(),
                                        color = Color.Red,
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 1.2.vh().value.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // 2c. Stream Buffering Indicator
                    if (isStreamBuffering && !isStaticActive && !isStreamError) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = Color.Cyan,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    // 2d. Retro Audio Spectrum Visualizer (Removed per user request to provide clean video)
                    /*
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.BottomStart) {
                        AudioSpectrumVisualizer(
                            isBuffering = isStreamBuffering,
                            isError = isStreamError || isStaticActive,
                            isPlaying = activeChannel != null
                        )
                    }
                    */

                    // 2e. Technical Stats Overlay
                    TechnicalStatsOverlay(viewModel = viewModel)

                    // 2f. Parental PIN Entry
                    PinEntryDialog(viewModel = viewModel)

                    // 2h. Country/Category Selection Overlay
                    CountrySelectionOverlay(
                        viewModel = viewModel,
                        onCategorySelected = { category ->
                            viewModel.setCategory(category)
                            showChannelGuide = true
                            viewModel.toggleCountryMenu(false)
                        }
                    )

                    // 2g. CRT Power-On Effect (Removed per user request)
                    // CrtPowerOnEffect(viewModel = viewModel)

                    // 3. Vintage CRT Scanline and Screen bulge filters (Removed per user request)
                    // CrtScanlineOverlay()

                    // 4. Custom Retro OSD Indicators
                    IptvOsdOverlay(viewModel = viewModel)

                    // 4b. Tuning Static Transition Effect (Removed per user request)
                    // TuningStaticOverlay(isActive = isStaticActive)

                    // 5. Gear icon (top gear Settings trigger) - Removed per user request since swipe triggers it
                    /*
                    if (!isTvDevice) {
                        SettingsTriggerButton(
                            onTrigger = { showSettings = true },
                            visible = !showSettings && !showExitDialog && !showChannelGuide
                        )
                    }
                    */

                    // 5b. Mobile Navigation Buttons (Removed per user request, replaced by swipe gestures)

                    // 6. Sliding modern Settings menu
                    AnimatedVisibility(
                        visible = showSettings,
                        enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        SettingsMenu(
                            viewModel = viewModel,
                            onClose = { 
                                keyboardController?.hide()
                                showSettings = false 
                            }
                        )
                    }

                    // 7. Sliding modern Channel List drawer
                    AnimatedVisibility(
                        visible = showChannelGuide,
                        enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
                        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it }),
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        ChannelGuideDrawer(
                            viewModel = viewModel,
                            showSearch = showSearchInGuide,
                            onShowSearchChange = { showSearchInGuide = it },
                            onClose = { 
                                keyboardController?.hide()
                                showChannelGuide = false 
                                showSearchInGuide = false
                            },
                            onOpenSettings = {
                                keyboardController?.hide()
                                showChannelGuide = false
                                showSearchInGuide = false
                                showSettings = true
                            }
                        )
                    }

                    // 8. Exit confirmation box
                    if (showExitDialog) {
                        ExitConfirmationDialog(
                            onConfirm = {
                                showExitDialog = false
                                finish()
                            },
                            onCancel = {
                                showExitDialog = false
                            }
                        )
                    }

                    // 9. Playlist Importing overlay
                    PlaylistImportingOverlay(viewModel = viewModel)

                    // 9b. Player Controls Overlay
                    if (showPlayerControls) {
                        PlayerControlsOverlay(
                            player = exoPlayer,
                            onClose = { showPlayerControls = false }
                        )
                    }

                    // 9c. Animated Startup Splash Screen (TV Banner)
                    AnimatedVisibility(
                        visible = showSplash,
                        enter = fadeIn(animationSpec = tween(600)),
                        exit = fadeOut(animationSpec = tween(800))
                    ) {
                        IptvSplashScreen()
                    }

                    // 10. Modern Screen Off Effect overlay
                    // (Removed CRT collapse per user request)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Respecting user request: Pause playback when app is backgrounded
        exoPlayer?.pause()
    }

    override fun onStop() {
        super.onStop()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        exoPlayer?.release()
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
                if (!showSettings && !showExitDialog) {
                    if (!showChannelGuide) {
                        showChannelGuide = true
                        showSearchInGuide = false
                        return true
                    } else {
                        showSearchInGuide = true
                        return true
                    }
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
                if (viewModel.isCountryMenuVisible.value) {
                    val digit = keyCode - KeyEvent.KEYCODE_0
                    if (digit in 1..3) {
                        viewModel.selectCategoryByNumber(digit)
                    }
                    return true
                }
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
                    viewModel.showOsd()
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
                if (!showChannelGuide) {
                    showChannelGuide = true
                    showSearchInGuide = true
                } else {
                    showSearchInGuide = !showSearchInGuide
                }
                return true
            }
            KeyEvent.KEYCODE_PROG_YELLOW -> {
                return true
            }
            KeyEvent.KEYCODE_PROG_BLUE -> {
                viewModel.toggleCountryMenu()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (showSearchInGuide) {
                    showSearchInGuide = false
                    return true
                } else if (showSettings) {
                    showSettings = false
                    return true
                } else if (showPlayerControls) {
                    showPlayerControls = false
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
                    showExitDialog = true
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
                } else if (showSettings || showExitDialog || showChannelGuide || viewModel.isGridViewVisible.value) {
                    // Menus handle their own focus/clicks
                } else if (!isTvDevice) {
                    // Only toggle controls via OK on mobile/touch, on TV it was confusing
                    showPlayerControls = !showPlayerControls
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
    val playlist by viewModel.playbackPlaylist.collectAsState()
    val playlistIndex by viewModel.playbackIndex.collectAsState()

    val resizeMode = when (aspectRatioMode) {
        "FIT" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        "ZOOM" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
    }

    // Initialize Player
    LaunchedEffect(context) {
        if (playerInstance == null) {
            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    2000,  // minBufferMs (extremely low buffer requirements for instant play)
                    5000,  // maxBufferMs (protects low-RAM 500MB devices!)
                    500,   // bufferForPlaybackMs (starts playing with just 500ms of audio/video buffered)
                    1000   // bufferForPlaybackAfterRebufferMs (fast recovery on rebuffer)
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setConnectTimeoutMs(8000)
                .setReadTimeoutMs(8000)
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
                .build().apply {
                    playWhenReady = true
                    repeatMode = ExoPlayer.REPEAT_MODE_OFF
                    videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                }

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
                        }
                        androidx.media3.common.Player.STATE_READY -> {
                            viewModel.setStreamBuffering(false)
                            viewModel.setStreamError(false)
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    viewModel.setStreamBuffering(false)
                    viewModel.setStreamError(true, "SIGNAL INTERRUPTED")
                    viewModel.markCurrentChannelInactive()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    // Sync ViewModel if player transitions internally (e.g. seekToNext)
                    val newIndex = player.currentMediaItemIndex
                    if (newIndex in playlist.indices) {
                        val transitionedChannel = playlist[newIndex]
                        if (transitionedChannel.id != viewModel.activeChannel.value?.id) {
                            viewModel.setActiveChannel(transitionedChannel)
                        }
                    }
                }
            })

            playerInstance = player
            onPlayerCreated(player)
        }
    }

    // Sync Playlist and Position
    LaunchedEffect(playlist, playlistIndex) {
        playerInstance?.let { player ->
            if (playlist.isEmpty() || playlistIndex == -1) return@LaunchedEffect

            val currentMediaItems = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
            val incomingMediaItems = playlist.mapNotNull { ch ->
                if (ch.url.isBlank()) return@mapNotNull null
                try {
                    MediaItem.Builder()
                        .setUri(ch.url)
                        .setMediaId(ch.id.toString())
                        .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(ch.name).build())
                        .build()
                } catch (e: Exception) {
                    android.util.Log.e("AndroidVideoPlayer", "Error creating MediaItem for channel: ${ch.name} with URL: ${ch.url}", e)
                    null
                }
            }

            if (incomingMediaItems.isEmpty()) return@LaunchedEffect

            // Check if playlist has changed significantly or we just need to seek
            val isSamePlaylist = incomingMediaItems.size == currentMediaItems.size && 
                                incomingMediaItems.firstOrNull()?.mediaId == currentMediaItems.firstOrNull()?.mediaId

            if (!isSamePlaylist) {
                player.setMediaItems(incomingMediaItems)
                player.seekTo(playlistIndex, androidx.media3.common.C.TIME_UNSET)
                player.prepare()
            } else if (player.currentMediaItemIndex != playlistIndex) {
                player.seekTo(playlistIndex, androidx.media3.common.C.TIME_UNSET)
            }
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
                this.keepScreenOn = true
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
                    .border(1.dp, Color.Cyan, RoundedCornerShape(12.dp))
                    .background(Color(0xFF121212), RoundedCornerShape(12.dp))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "FETCHING PLAYLIST DATA",
                    color = Color.Cyan,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "$importProgressPercent%",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.ExtraBold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { importProgressPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Color.Cyan,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = importStatus.uppercase(),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "CHANNELS FOUND: $importChannelCount",
                    color = Color.Cyan,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif,
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
    isPlaying: Boolean
) {
    if (isBuffering || isError || !isPlaying) return

    val barCount = 12
    val randoms = remember { List(barCount) { Animatable(0.1f) } }

    LaunchedEffect(Unit) {
        while (true) {
            for (i in 0 until barCount) {
                val base = 1f - kotlin.math.abs(i - barCount / 2f) / (barCount / 2f)
                val noise = (0..40).random() / 100f
                val target = (base * 0.4f + noise).coerceIn(0.1f, 1f)
                launch {
                    randoms[i].animateTo(target, tween(80))
                }
            }
            delay(100)
        }
    }

    val accentColor = Color.Cyan

    Canvas(
        modifier = modifier
            .width((barCount * 6).dp)
            .height(24.dp)
            .padding(4.dp)
    ) {
        val spacing = 2.dp.toPx()
        val barWidth = (size.width - (barCount - 1) * spacing) / barCount
        
        for (i in 0 until barCount) {
            val h = randoms[i].value * size.height
            drawRect(
                color = accentColor.copy(alpha = 0.8f),
                topLeft = Offset(i * (barWidth + spacing), size.height - h),
                size = Size(barWidth, h)
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
    val osdVisible by viewModel.isOsdVisible.collectAsState()
    val sleepTimer by viewModel.sleepTimerMinutes.collectAsState()
    val isLocked by viewModel.isQuickLocked.collectAsState()
    val fontIndex by viewModel.selectedFontIndex.collectAsState()

    val currentFont = when(fontIndex) {
        1 -> FontFamily.Serif
        2 -> FontFamily.SansSerif
        else -> FontFamily.Default
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(4.vw())
    ) {
        // Top-Left: Sleep Timer
        if (sleepTimer != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 1.vw(), vertical = 0.5.vh())
            ) {
                Text(
                    text = "SLEEP: $sleepTimer MIN",
                    color = Color.Cyan,
                    fontFamily = currentFont,
                    fontSize = 1.8.vh().value.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Top-Right: Signal Strength Indicator
        if (activeChannel != null && !isTuning && osdVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(1.vw()),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "SIGNAL",
                    color = Color.White,
                    fontFamily = currentFont,
                    fontSize = 1.2.vh().value.sp,
                    fontWeight = FontWeight.SemiBold
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
                                .background(if (isActive) Color.Cyan else Color.DarkGray)
                        )
                    }
                }
                Text(
                    text = "${(signalStrength * 100).toInt()}%",
                    color = Color.White,
                    fontFamily = currentFont,
                    fontSize = 1.2.vh().value.sp
                )
            }
        }

        // Bottom-Right: Modern Channel Info
        if (activeChannel != null && !isTuning && osdVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .padding(1.5.vw()),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "CH ${String.format("%02d", activeChannel!!.channelNumber)}",
                    color = Color.Cyan,
                    fontFamily = currentFont,
                    fontSize = 3.vh().value.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = activeChannel!!.name.uppercase(),
                    color = Color.White,
                    fontFamily = currentFont,
                    fontSize = 2.2.vh().value.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!activeChannel!!.groupTitle.isNullOrEmpty()) {
                    Text(
                        text = activeChannel!!.groupTitle!!.uppercase(),
                        color = Color.LightGray,
                        fontFamily = currentFont,
                        fontSize = 1.5.vh().value.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 0.2.vh())
                    )
                }
                
                Spacer(modifier = Modifier.height(1.vh()))
                
                // EPG "Now Playing" block
                Column(
                    modifier = Modifier
                        .background(Color(0xFF121212).copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                        .padding(0.8.vw()),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "NOW PLAYING",
                        color = Color.Cyan,
                        fontFamily = currentFont,
                        fontSize = 1.2.vh().value.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "NO EPG DATA AVAILABLE", // Placeholder for XMLTV fetcher
                        color = Color.Cyan,
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
                    .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.Cyan, RoundedCornerShape(12.dp))
                    .padding(horizontal = 48.dp, vertical = 32.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "GO TO CHANNEL",
                        color = Color.White.copy(alpha = 0.5f),
                        fontFamily = currentFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = numpadBuffer,
                        color = Color.Cyan,
                        fontFamily = currentFont,
                        fontSize = 5.vh().value.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Bottom-Left: Volume bar block OSD (▰▰▰▰▰▰▰▱▱▱)
        if (isVolumeVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "VOLUME: $volumeLevel",
                    color = Color.White,
                    fontFamily = currentFont,
                    fontSize = 1.8.vh().value.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 1..10) {
                        Box(
                            modifier = Modifier
                                .size(width = 12.dp, height = 24.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (i <= volumeLevel) Color.Cyan else Color.White.copy(alpha = 0.1f))
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
                    .background(Color.Black.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.Cyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tuningStatus,
                    color = Color.Cyan,
                    fontFamily = currentFont,
                    fontSize = 2.vh().value.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Frequency pointer track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    val scalePercent = (tuningFreq - 45.25f) / (800.00f - 45.25f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(scalePercent.coerceIn(0f, 1f))
                            .background(Color.Cyan)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

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

@Composable
fun Modifier.modernFocusEffect(
    isFocused: Boolean,
    focusedColor: Color = Color.Cyan,
    unfocusedColor: Color = Color.Transparent,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)
): Modifier = this
    .border(
        width = if (isFocused) 2.dp else 0.dp,
        color = if (isFocused) focusedColor else Color.Transparent,
        shape = shape
    )
    .background(
        color = if (isFocused) focusedColor.copy(alpha = 0.15f) else Color.Transparent,
        shape = shape
    )

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
        kotlinx.coroutines.delay(100)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
                .modernFocusEffect(isFocused, focusedColor = Color.Red, shape = CircleShape)
                .size(48.dp)
                .testTag("settings_gear_button")
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Open Retro Settings Menu",
                tint = if (isFocused) Color.Red else Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Slide-out Retro STB Playlist Configuration Settings Panel.
 */
@Composable
fun SettingsMenu(
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

    // Custom modern theme colors
    val bgColor = Color(0xFF121212)
    val accentColor = Color.Cyan
    val secondaryColor = Color.White

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
            .border(2.dp, accentColor)
            .padding(24.dp)
    ) {
        val scrollState = rememberScrollState()
        var isNameFocused by remember { mutableStateOf(false) }
        var isUrlFocused by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            // Header with Close button
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYSTEM SETTINGS",
                    color = accentColor,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                )
                
                var isCloseFocused by remember { mutableStateOf(false) }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .onFocusChanged { isCloseFocused = it.isFocused }
                        .modernFocusEffect(isCloseFocused, focusedColor = accentColor, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Settings",
                        tint = if (isCloseFocused) Color.Cyan else Color.White
                    )
                }
            }

            // Input Fields Row
            Text(
                text = "IMPORT PLAYLIST URL",
                color = secondaryColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                enabled = !isImporting,
                readOnly = !isNameFocused,
                label = { Text("PLAYLIST LABEL", color = if (isNameFocused) accentColor else Color.White.copy(alpha = 0.6f), fontFamily = FontFamily.SansSerif) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedLabelColor = accentColor,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledBorderColor = Color.White.copy(alpha = 0.1f),
                    disabledLabelColor = Color.White.copy(alpha = 0.1f),
                    disabledTextColor = Color.Gray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { 
                        isNameFocused = it.isFocused 
                        if (it.isFocused) {
                            keyboardController?.show()
                        }
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = {
                        focusManager.moveFocus(FocusDirection.Down)
                    }
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = playlistUrl,
                onValueChange = { playlistUrl = it },
                enabled = !isImporting,
                readOnly = !isUrlFocused,
                label = { Text("M3U PLAYLIST URL", color = if (isUrlFocused) accentColor else Color.White.copy(alpha = 0.6f), fontFamily = FontFamily.SansSerif) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedLabelColor = accentColor,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledBorderColor = Color.White.copy(alpha = 0.1f),
                    disabledLabelColor = Color.White.copy(alpha = 0.1f),
                    disabledTextColor = Color.Gray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { 
                        isUrlFocused = it.isFocused 
                        if (it.isFocused) {
                            keyboardController?.show()
                        }
                    },
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
                text = "QUICK ADD SOURCES",
                color = secondaryColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var isBdFocused by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = {
                        playlistName = "BD CHANNELS"
                        playlistUrl = "https://iptv-org.github.io/iptv/countries/bd.m3u"
                    },
                    modifier = Modifier.weight(1f).onFocusChanged { isBdFocused = it.isFocused }.modernFocusEffect(isBdFocused),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isBdFocused) Color.White else accentColor),
                    border = BorderStroke(1.dp, if (isBdFocused) Color.Cyan else accentColor)
                ) {
                    Text("BD IPTV", fontSize = 10.sp, fontFamily = FontFamily.SansSerif)
                }
                var isWorldFocused by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = {
                        playlistName = "WORLD CHANNELS"
                        playlistUrl = "https://iptv-org.github.io/iptv/index.m3u"
                    },
                    modifier = Modifier.weight(1f).onFocusChanged { isWorldFocused = it.isFocused }.modernFocusEffect(isWorldFocused),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isWorldFocused) Color.White else accentColor),
                    border = BorderStroke(1.dp, if (isWorldFocused) Color.Cyan else accentColor)
                ) {
                    Text("WORLD IPTV", fontSize = 10.sp, fontFamily = FontFamily.SansSerif)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // INTERFACE SETTINGS
            Text(
                text = "INTERFACE CUSTOMIZATION",
                color = secondaryColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var isFontFocused by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { viewModel.cycleFont() },
                    modifier = Modifier.weight(1f).onFocusChanged { isFontFocused = it.isFocused }.modernFocusEffect(isFontFocused),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isFontFocused) Color.White else accentColor),
                    border = BorderStroke(1.dp, if (isFontFocused) Color.Cyan else accentColor)
                ) {
                    val fontIndex by viewModel.selectedFontIndex.collectAsState()
                    val fontName = when(fontIndex) {
                        1 -> "SERIF"
                        2 -> "SANS"
                        else -> "SYSTEM"
                    }
                    Text("FONT: $fontName", fontSize = 10.sp, fontFamily = FontFamily.SansSerif)
                }
                var isLockFocused by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { viewModel.toggleQuickLock() },
                    modifier = Modifier.weight(1f).onFocusChanged { isLockFocused = it.isFocused }.modernFocusEffect(isLockFocused),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isLockFocused) Color.White else if (viewModel.isQuickLocked.collectAsState().value) Color.Red else accentColor),
                    border = BorderStroke(1.dp, if (isLockFocused) Color.Cyan else if (viewModel.isQuickLocked.collectAsState().value) Color.Red else accentColor)
                ) {
                    Text(if (viewModel.isQuickLocked.collectAsState().value) "UNLOCK UI" else "LOCK UI", fontSize = 10.sp, fontFamily = FontFamily.SansSerif)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Load Playlist button / Progress Indicator
            if (isImporting) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, secondaryColor, RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "IMPORTING DATA...",
                            color = secondaryColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$importProgressPercent%",
                            color = accentColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Retro-character progress bar [████░░░░░░]
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { importProgressPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = accentColor,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "STATUS: ${importStatus.uppercase()}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif
                    )

                    Text(
                        text = "CHANNELS: $importChannelCount",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif,
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
                            containerColor = if (isLoadButtonFocused) accentColor else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { isLoadButtonFocused = it.isFocused }
                            .modernFocusEffect(isLoadButtonFocused, focusedColor = accentColor, unfocusedColor = Color.White.copy(alpha = 0.2f))
                            .clip(RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "IMPORT URL",
                            color = if (isLoadButtonFocused) Color.Black else accentColor,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFileButtonFocused) accentColor else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { isFileButtonFocused = it.isFocused }
                            .modernFocusEffect(isFileButtonFocused, focusedColor = accentColor, unfocusedColor = Color.White.copy(alpha = 0.2f))
                            .clip(RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "LOCAL FILE",
                            color = if (isFileButtonFocused) Color.Black else secondaryColor,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "ACTIVE SOURCES",
                color = secondaryColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Active sources list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                playlists.forEach { playlist ->
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
                                .onFocusChanged { isCardFocused = it.isFocused }
                                .modernFocusEffect(isCardFocused, focusedColor = Color.Red)
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
                                    color = if (isCardFocused) Color.White else accentColor,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = playlist.url,
                                    color = if (isCardFocused) Color.LightGray else Color.Gray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.SansSerif,
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
                                .onFocusChanged { isDeleteFocused = it.isFocused }
                                .modernFocusEffect(isDeleteFocused, focusedColor = Color.Red)
                                .focusable()
                                .testTag("delete_playlist_${playlist.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Wipe Playlist Source",
                                tint = if (isDeleteFocused) Color.White else Color.Red.copy(alpha = 0.6f),
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
                    containerColor = if (isTuneBtnFocused) accentColor else Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isTuneBtnFocused = it.isFocused }
                    .border(1.dp, accentColor, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "AUTO SCAN CHANNELS",
                    color = if (isTuneBtnFocused) Color.Black else accentColor,
                    fontFamily = FontFamily.SansSerif,
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
                    containerColor = if (isAspectBtnFocused) accentColor else Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isAspectBtnFocused = it.isFocused }
                    .modernFocusEffect(isAspectBtnFocused, focusedColor = accentColor, unfocusedColor = Color.White.copy(alpha = 0.2f))
                    .clip(RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "SCREEN ASPECT RATIO: $aspectRatioMode",
                    color = if (isAspectBtnFocused) Color.Black else secondaryColor,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Close button
            var isCloseBtnFocused by remember { mutableStateOf(false) }
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCloseBtnFocused) accentColor else Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isCloseBtnFocused = it.isFocused }
                    .modernFocusEffect(isCloseBtnFocused, focusedColor = accentColor, unfocusedColor = Color.White.copy(alpha = 0.2f))
                    .clip(RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "CLOSE SETTINGS",
                    color = if (isCloseBtnFocused) Color.Black else secondaryColor,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Slide-out Retro STB Channel Drawer (Slides from the left).
 */
@Composable
fun ChannelGuideDrawer(
    viewModel: IptvViewModel,
    showSearch: Boolean,
    onShowSearchChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val channels by viewModel.channels.collectAsState()
    val activeChannel by viewModel.activeChannel.collectAsState()
    val focusRequester = remember { FocusRequester() }

    var searchQuery by remember { mutableStateOf("") }
    var showOnlyFavorites by remember { mutableStateOf(false) }

    LaunchedEffect(showSearch) {
        if (showSearch) {
            // Wait for composition to attach the search box
            kotlinx.coroutines.delay(100)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            searchQuery = ""
        }
    }
    val keyboardInput by viewModel.keyboardInput.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    val activePlaylistId = activeChannel?.playlistId
    
    val activePlaylistChannels = remember(channels, activePlaylistId) {
        if (activePlaylistId != null) {
            channels.filter { it.playlistId == activePlaylistId }
        } else {
            val firstPlaylistId = channels.map { it.playlistId }.firstOrNull()
            if (firstPlaylistId != null) {
                channels.filter { it.playlistId == firstPlaylistId }
            } else {
                channels
            }
        }
    }

    val filteredChannels = remember(activePlaylistChannels, searchQuery, showOnlyFavorites, keyboardInput, selectedCategory) {
        activePlaylistChannels.filter { channel ->
            val effectiveSearch = if (keyboardInput.isNotEmpty()) keyboardInput else searchQuery
            val matchesSearch = if (effectiveSearch.isBlank()) true else channel.name.contains(effectiveSearch, ignoreCase = true)
            val matchesFav = if (showOnlyFavorites) channel.isFavorite else true
            val matchesCategory = if (selectedCategory == null) true else channel.groupTitle == selectedCategory
            matchesSearch && matchesFav && matchesCategory
        }
    }

    val categories = remember(activePlaylistChannels) {
        listOf(null) + activePlaylistChannels.mapNotNull { it.groupTitle }.distinct().sorted()
    }

    val accentColor = Color.Cyan
    val secondaryColor = Color.White
    
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(400.dp)
            .background(Color(0xFF121212))
            .border(1.dp, Color.White.copy(alpha = 0.1f))
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CHANNEL LIST",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = { showOnlyFavorites = !showOnlyFavorites }) {
                    Icon(
                        imageVector = if (showOnlyFavorites) Icons.Default.Star else Icons.Outlined.Star,
                        contentDescription = "Toggle Favorites Filter",
                        tint = if (showOnlyFavorites) accentColor else Color.Gray
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
                            .background(if (isSelected) accentColor else if (isCatFocused) Color.Cyan.copy(alpha = 0.2f) else Color.Transparent)
                            .onFocusChanged { isCatFocused = it.isFocused }
                            .modernFocusEffect(isCatFocused, focusedColor = Color.Cyan, unfocusedColor = accentColor)
                            .clickable { viewModel.setCategory(category) }
                            .focusable()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = category?.uppercase() ?: "ALL",
                            color = if (isSelected) Color.Black else if (isCatFocused) Color.White else accentColor,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("FILTER CHANNELS", color = accentColor, fontFamily = FontFamily.SansSerif, fontSize = 10.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        cursorColor = accentColor
                    ),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.SansSerif),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .padding(bottom = 12.dp)
                )
            }

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
                            .onFocusChanged { isFocused = it.isFocused }
                            .modernFocusEffect(isFocused, focusedColor = Color.Cyan, unfocusedColor = if (isPlaying) accentColor else Color.Transparent)
                            .background(
                                if (isPlaying) Color.Cyan.copy(alpha = 0.1f) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
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
                            color = if (isFocused || isPlaying) accentColor else Color.Gray,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.SansSerif,
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
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }

                        Text(
                            text = channel.name.uppercase(),
                            color = if (isFocused) Color.White else if (isPlaying) accentColor else Color.LightGray,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (channel.isFavorite) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Favorite",
                                tint = secondaryColor,
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
                                tint = accentColor,
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
                        containerColor = if (isSettingsFocused) accentColor else Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isSettingsFocused = it.isFocused }
                        .border(1.dp, accentColor, RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "SETTINGS",
                        color = if (isSettingsFocused) Color.Black else accentColor,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Close Button
                var isCloseFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCloseFocused) accentColor else Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isCloseFocused = it.isFocused }
                        .border(1.dp, accentColor, RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "DISMISS",
                        color = if (isCloseFocused) Color.Black else accentColor,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Vintage 90s Exit confirmation dialog overlay.
 */
@Composable
fun ExitConfirmationDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = Color.Cyan
    val bgColor = Color(0xFF121212)
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .background(bgColor, RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "EXIT APPLICATION",
                color = Color.White,
                fontFamily = FontFamily.SansSerif,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = "DO YOU WANT TO EXIT?",
                color = Color.White,
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
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
                        containerColor = if (isYesFocused) accentColor else Color.Transparent
                    ),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { isYesFocused = it.isFocused }
                        .border(1.dp, if (isYesFocused) accentColor else Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "YES",
                        color = if (isYesFocused) Color.Black else Color.White,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                }

                var isNoFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNoFocused) accentColor else Color.Transparent
                    ),
                    modifier = Modifier
                        .onFocusChanged { isNoFocused = it.isFocused }
                        .border(1.dp, if (isNoFocused) accentColor else Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "NO",
                        color = if (isNoFocused) Color.Black else Color.White,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            AsyncImage(
                model = R.drawable.img_tv_banner,
                contentDescription = "Hero Banner",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "NO CHANNELS LOADED",
                color = Color.White,
                fontSize = 24.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.ExtraBold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "PLEASE CONFIGURE YOUR PLAYLIST SOURCE",
                color = Color.Cyan,
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(0.7f)
            )
        }
    }
}

@Composable
fun ChannelGridView(
    viewModel: IptvViewModel,
    onClose: () -> Unit
) {
    val channels by viewModel.channels.collectAsState()
    val activeChannel by viewModel.activeChannel.collectAsState()
    val accentColor = Color.Cyan
    val bgColor = Color(0xFF121212)

    val activePlaylistId = activeChannel?.playlistId
    val activePlaylistChannels = remember(channels, activePlaylistId) {
        if (activePlaylistId != null) {
            channels.filter { it.playlistId == activePlaylistId }
        } else {
            val firstPlaylistId = channels.map { it.playlistId }.firstOrNull()
            if (firstPlaylistId != null) {
                channels.filter { it.playlistId == firstPlaylistId }
            } else {
                channels
            }
        }
    }

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
                .background(bgColor, RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(32.dp)
                .clickable(enabled = false) { }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CHANNEL GUIDE",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "STB MODE",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(activePlaylistChannels.take(18)) { channel -> // Limit to 18 for demo/performance
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
                text = "USE NAVIGATION KEYS TO SELECT",
                color = Color.Gray,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
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
    val accentColor = Color.Cyan

    Box(
        modifier = Modifier
            .aspectRatio(16f / 10f)
            .background(if (isPlaying) accentColor.copy(alpha = 0.1f) else Color.Black)
            .onFocusChanged { isFocused = it.isFocused }
            .modernFocusEffect(isFocused, focusedColor = Color.Cyan, unfocusedColor = if (isPlaying) accentColor else Color.DarkGray)
            .clickable { onClick() }
            .focusable()
            .clip(RoundedCornerShape(8.dp))
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
                    .background(if (isFocused) accentColor else Color.Black)
                    .padding(8.dp)
            ) {
                Text(
                    text = channel.name.uppercase(),
                    color = if (isFocused) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "NO EPG DATA",
                    color = if (isFocused) Color.Black.copy(alpha = 0.7f) else Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.SansSerif,
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
fun SearchKeyboard(
    viewModel: IptvViewModel,
    onClose: () -> Unit
) {
    val keyboardInput by viewModel.keyboardInput.collectAsState()
    val accentColor = Color.Cyan
    val bgColor = Color(0xFF121212)
    
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
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(450.dp)
                .background(bgColor, RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(24.dp)
                .clickable(enabled = false) { }
        ) {
            Text(
                text = "SEARCH CHANNELS",
                color = Color.White,
                fontSize = 18.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Input display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (keyboardInput.isEmpty()) "SEARCH..." else keyboardInput,
                    color = if (keyboardInput.isEmpty()) Color.Gray else accentColor,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Keys Grid
            val flatKeys = keys.flatten()
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.height(300.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(flatKeys) { key ->
                    var isFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isFocused) accentColor else Color.White.copy(alpha = 0.05f))
                            .border(1.dp, if (isFocused) accentColor else Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                            .onFocusChanged { isFocused = it.isFocused }
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
                            color = if (isFocused) Color.Black else Color.White,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CountrySelectionOverlay(
    viewModel: IptvViewModel,
    onCategorySelected: (String?) -> Unit
) {
    val isVisible by viewModel.isCountryMenuVisible.collectAsState()
    if (!isVisible) return

    val channels by viewModel.channels.collectAsState()
    val activeChannel by viewModel.activeChannel.collectAsState()
    val activePlaylistId = activeChannel?.playlistId

    // Extract unique categories from channels of the active source (playlist) only
    val categories = remember(channels, activePlaylistId) {
        val filtered = if (activePlaylistId != null) {
            channels.filter { it.playlistId == activePlaylistId }
        } else {
            val firstPlaylistId = channels.map { it.playlistId }.firstOrNull()
            if (firstPlaylistId != null) {
                channels.filter { it.playlistId == firstPlaylistId }
            } else {
                channels
            }
        }
        listOf(null) + filtered.mapNotNull { it.groupTitle }.distinct().sorted()
    }

    val accentColor = Color.Cyan
    val bgColor = Color(0xFF121212)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { viewModel.toggleCountryMenu(false) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.8f)
                .background(bgColor, RoundedCornerShape(16.dp))
                .border(2.dp, accentColor, RoundedCornerShape(16.dp))
                .padding(32.dp)
                .clickable(enabled = false) { },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SELECT CATEGORY",
                color = accentColor,
                fontFamily = FontFamily.SansSerif,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "ACTIVE SOURCE PLAYLIST CATEGORIES",
                color = Color.White.copy(alpha = 0.5f),
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (categories.isEmpty() || (categories.size == 1 && categories[0] == null)) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NO CATEGORIES DETECTED IN ACTIVE PLAYLIST",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(categories.size) { index ->
                        val category = categories[index]
                        val categoryLabel = category ?: "ALL CHANNELS"
                        var isFocused by remember { mutableStateOf(false) }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isFocused) accentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f))
                                .onFocusChanged { isFocused = it.isFocused }
                                .modernFocusEffect(isFocused, focusedColor = accentColor, unfocusedColor = Color.White.copy(alpha = 0.1f))
                                .clickable {
                                    onCategorySelected(category)
                                }
                                .focusable(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = categoryLabel.uppercase(),
                                color = if (isFocused) accentColor else Color.White,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "CLICK / SELECT TO FILTER AND VIEW CHANNELS",
                color = accentColor.copy(alpha = 0.7f),
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
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
    val accentColor = Color.Cyan

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(32.dp)
        ) {
            Text(
                text = "STREAM TECHNICAL DATA",
                color = Color.White,
                fontFamily = FontFamily.SansSerif,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            val stats = listOf(
                "RESOLUTION" to resolution,
                "CODEC" to codec,
                "BITRATE" to bitrate,
                "QUALITY" to "16K ULTRA HD",
                "STABILITY" to "OPTIMAL"
            )

            stats.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth(0.5f).padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = label, color = Color.Gray, fontFamily = FontFamily.SansSerif, fontSize = 12.sp)
                    Text(text = value, color = accentColor, fontFamily = FontFamily.SansSerif, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    val accentColor = Color.Cyan

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
                fontFamily = FontFamily.SansSerif,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "ENTER 4-DIGIT PIN",
                color = accentColor,
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (i in 0 until 4) {
                    val digit = if (i < pinBuffer.length) "*" else "_"
                    Text(
                        text = digit,
                        color = accentColor,
                        fontFamily = FontFamily.SansSerif,
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
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(100)
                    try {
                        focusRequester.requestFocus()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
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

/**
 * Retro-styled Player Controls Overlay (Pause/Resume & Quality indicator).
 */
@Composable
fun PlayerControlsOverlay(
    player: androidx.media3.exoplayer.ExoPlayer?,
    onClose: () -> Unit
) {
    if (player == null) return

    val accentColor = Color.Cyan
    val secondaryColor = Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF121212).copy(alpha = 0.95f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)))
                .padding(48.dp)
                .clickable(enabled = false) {}, // Prevent closing when clicking the panel itself
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PLAYBACK CONTROLS",
                color = accentColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            // Channel Name & Live Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(Color.Red)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = player.currentMediaItem?.mediaMetadata?.title?.toString()?.uppercase() ?: "STREAMING DATA...",
                    color = accentColor,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // PLAY / PAUSE (RESUME)
                var isPlayPauseFocused by remember { mutableStateOf(false) }
                val isPlaying = player.isPlaying
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            if (player.isPlaying) player.pause() else player.play()
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .onFocusChanged { isPlayPauseFocused = it.isFocused }
                            .modernFocusEffect(isPlayPauseFocused, focusedColor = Color.Cyan, shape = CircleShape)
                            .focusable()
                    ) {
                        Text(
                            text = if (isPlaying) "||" else "▶",
                            color = if (isPlayPauseFocused) Color.Black else Color.Cyan,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (isPlaying) "PAUSE" else "RESUME",
                        color = if (isPlayPauseFocused) Color.Cyan else Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // QUALITY (BITRATE / TRACKS)
                var isQualityFocused by remember { mutableStateOf(false) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            // Placeholder for quality selector
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .onFocusChanged { isQualityFocused = it.isFocused }
                            .modernFocusEffect(isQualityFocused, focusedColor = Color.Cyan, shape = CircleShape)
                            .focusable()
                    ) {
                        Text(
                            text = "UHD",
                            color = if (isQualityFocused) Color.Black else Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    Text(
                        text = "16K QUALITY",
                        color = if (isQualityFocused) Color.Cyan else Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "PRESS BACK TO DISMISS",
                color = Color.DarkGray,
                fontSize = 10.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Composable
fun IptvSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A0F)),
        contentAlignment = Alignment.Center
    ) {
        // Glowing futuristic digital backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Cyan.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        radius = 1400f
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(0.82f)
        ) {
            // Elegant brand heading
            Text(
                text = "IPTV MEDIA HUB",
                color = Color.White,
                fontSize = 26.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // The beautifully framed TV banner showing the user's customized portrait character
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        width = 2.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Cyan, Color(0xFF9013FE))
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                AsyncImage(
                    model = R.drawable.img_tv_banner,
                    contentDescription = "IPTV Customized Welcome Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                
                // Vignette gradient for dark immersive contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Subtitle status indicator
            Text(
                text = "INITIALIZING SECURE PORTAL...",
                color = Color.Cyan,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.alpha(0.8f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Progress bar
            LinearProgressIndicator(
                color = Color.Cyan,
                trackColor = Color.White.copy(alpha = 0.1f),
                modifier = Modifier
                    .width(180.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
    }
}

