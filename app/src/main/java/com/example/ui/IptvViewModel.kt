package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.IPTVChannel
import com.example.data.IptvDatabase
import com.example.data.IptvRepository
import com.example.data.M3UPlaylist
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class IptvViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: IptvRepository
    private val sharedPrefs = application.getSharedPreferences("sr_iptv_prefs", Context.MODE_PRIVATE)

    // Data streams
    val playlists: StateFlow<List<M3UPlaylist>>
    val channels: StateFlow<List<IPTVChannel>>

    // Core playback states
    private val _activeChannel = MutableStateFlow<IPTVChannel?>(null)
    val activeChannel: StateFlow<IPTVChannel?> = _activeChannel.asStateFlow()

    private val _playbackPlaylist = MutableStateFlow<List<IPTVChannel>>(emptyList())
    val playbackPlaylist: StateFlow<List<IPTVChannel>> = _playbackPlaylist.asStateFlow()

    private val _playbackIndex = MutableStateFlow(-1)
    val playbackIndex: StateFlow<Int> = _playbackIndex.asStateFlow()

    private val _selectedSourceUrl = MutableStateFlow<String?>(null)
    val selectedSourceUrl: StateFlow<String?> = _selectedSourceUrl.asStateFlow()

    private val _isStaticActive = MutableStateFlow(false)
    val isStaticActive: StateFlow<Boolean> = _isStaticActive.asStateFlow()

    // Dynamic Video Aspect Ratio (FILL, FIT, ZOOM)
    private val _aspectRatioMode = MutableStateFlow(sharedPrefs.getString("aspect_ratio_mode", "FILL") ?: "FILL")
    val aspectRatioMode: StateFlow<String> = _aspectRatioMode.asStateFlow()

    // Stream playback quality and health indicators
    private val _isStreamBuffering = MutableStateFlow(false)
    val isStreamBuffering: StateFlow<Boolean> = _isStreamBuffering.asStateFlow()

    private val _isStreamError = MutableStateFlow(false)
    val isStreamError: StateFlow<Boolean> = _isStreamError.asStateFlow()

    private val _streamErrorMessage = MutableStateFlow("")
    val streamErrorMessage: StateFlow<String> = _streamErrorMessage.asStateFlow()

    // Simulated Analog Auto-Tuning states
    private val _isTuning = MutableStateFlow(false)
    val isTuning: StateFlow<Boolean> = _isTuning.asStateFlow()

    private val _tuningFrequency = MutableStateFlow(45.25f)
    val tuningFrequency: StateFlow<Float> = _tuningFrequency.asStateFlow()

    private val _tuningStatusText = MutableStateFlow("")
    val tuningStatusText: StateFlow<String> = _tuningStatusText.asStateFlow()

    // Volume level state (0 to 10)
    private val _currentVolume = MutableStateFlow(10)
    val currentVolume: StateFlow<Int> = _currentVolume.asStateFlow()

    private val _volumeDisplayVisible = MutableStateFlow(false)
    val volumeDisplayVisible: StateFlow<Boolean> = _volumeDisplayVisible.asStateFlow()

    // New Retro States
    private val _signalStrength = MutableStateFlow(0.85f)
    val signalStrength: StateFlow<Float> = _signalStrength.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()

    private val _isKeyboardVisible = MutableStateFlow(false)
    val isKeyboardVisible: StateFlow<Boolean> = _isKeyboardVisible.asStateFlow()

    private val _keyboardInput = MutableStateFlow("")
    val keyboardInput: StateFlow<String> = _keyboardInput.asStateFlow()

    private val _technicalStatsVisible = MutableStateFlow(false)
    val technicalStatsVisible: StateFlow<Boolean> = _technicalStatsVisible.asStateFlow()

    private val _streamResolution = MutableStateFlow("N/A")
    val streamResolution: StateFlow<String> = _streamResolution.asStateFlow()

    private val _streamCodec = MutableStateFlow("N/A")
    val streamCodec: StateFlow<String> = _streamCodec.asStateFlow()

    private val _streamBitrate = MutableStateFlow("0 KBPS")
    val streamBitrate: StateFlow<String> = _streamBitrate.asStateFlow()

    private val _isPinEntryVisible = MutableStateFlow(false)
    val isPinEntryVisible: StateFlow<Boolean> = _isPinEntryVisible.asStateFlow()

    private val _isCountryMenuVisible = MutableStateFlow(false)
    val isCountryMenuVisible: StateFlow<Boolean> = _isCountryMenuVisible.asStateFlow()

    private val _pinBuffer = MutableStateFlow("")
    val pinBuffer: StateFlow<String> = _pinBuffer.asStateFlow()

    private val _isChannelLocked = MutableStateFlow(false)
    val isChannelLocked: StateFlow<Boolean> = _isChannelLocked.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(sharedPrefs.getString("last_selected_category", null))
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _isPowerOnEffectActive = MutableStateFlow(true)
    val isPowerOnEffectActive: StateFlow<Boolean> = _isPowerOnEffectActive.asStateFlow()

    private val _isGridViewVisible = MutableStateFlow(false)
    val isGridViewVisible: StateFlow<Boolean> = _isGridViewVisible.asStateFlow()

    private val _previousChannel = MutableStateFlow<IPTVChannel?>(null)
    val previousChannel: StateFlow<IPTVChannel?> = _previousChannel.asStateFlow()

    private val _currentTime = MutableStateFlow("")
    val currentTime: StateFlow<String> = _currentTime.asStateFlow()

    private val _isQuickLocked = MutableStateFlow(false)
    val isQuickLocked: StateFlow<Boolean> = _isQuickLocked.asStateFlow()

    private val _isOsdVisible = MutableStateFlow(false)
    val isOsdVisible: StateFlow<Boolean> = _isOsdVisible.asStateFlow()

    private var osdHideJob: Job? = null

    private val _selectedFontIndex = MutableStateFlow(sharedPrefs.getInt("selected_font_index", 0))
    val selectedFontIndex: StateFlow<Int> = _selectedFontIndex.asStateFlow()

    // Multi-digit channel input buffer
    private val _inputBufferText = MutableStateFlow("")
    val inputBufferText: StateFlow<String> = _inputBufferText.asStateFlow()

    private val _importedPlaylistResult = MutableSharedFlow<Result<Unit>>()
    val importedPlaylistResult: SharedFlow<Result<Unit>> = _importedPlaylistResult.asSharedFlow()

    // Playlist Import Progress states
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importStatus = MutableStateFlow("")
    val importStatus: StateFlow<String> = _importStatus.asStateFlow()

    private val _importProgressPercent = MutableStateFlow(0)
    val importProgressPercent: StateFlow<Int> = _importProgressPercent.asStateFlow()

    private val _importChannelCount = MutableStateFlow(0)
    val importChannelCount: StateFlow<Int> = _importChannelCount.asStateFlow()

    private var numpadDebounceJob: Job? = null
    private var staticOverlayJob: Job? = null
    private var volumeHideJob: Job? = null
    private var sleepTimerJob: Job? = null

    init {
        val database = Room.databaseBuilder(
            application,
            IptvDatabase::class.java,
            "sr_iptv_database"
        ).fallbackToDestructiveMigration().build()
        
        repository = IptvRepository(database.iptvDao())
        
        playlists = repository.playlists.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        channels = repository.channels.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Initialize channels and restore state
        viewModelScope.launch {
            repository.seedDefaultChannelsIfEmpty()
            
            // Check if we need a first-launch auto-tune
            val hasTuned = sharedPrefs.getBoolean("has_tuned_channels", false)
            if (!hasTuned) {
                runAnalogAutoTuning(isFirstLaunch = true)
            } else {
                restoreLastWatchedChannel()
            }

            // Start signal fluctuation loop
            startSignalFluctuation()
            
            // Start Clock loop
            startClockLoop()

            // Start auto verification for inactive channels
            startAutoVerificationTimer()
        }
    }

    private fun startAutoVerificationTimer() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(5 * 60 * 1000L) // 5 minutes
                try {
                    val allChannels = repository.getAllChannelsNow()
                    val inactiveChannels = allChannels.filter { !it.isActive }
                    
                    for (channel in inactiveChannels) {
                        try {
                            val url = java.net.URL(channel.url)
                            val connection = url.openConnection() as java.net.HttpURLConnection
                            connection.requestMethod = "HEAD"
                            connection.connectTimeout = 5000
                            connection.readTimeout = 5000
                            
                            val responseCode = connection.responseCode
                            if (responseCode in 200..299) {
                                repository.updateChannelActiveStatus(channel.id, true)
                            }
                            connection.disconnect()
                        } catch (e: Exception) {
                            // Still dead, ignore
                        }
                    }
                } catch (e: Exception) {
                    // Ignore general verification errors
                }
            }
        }
    }

    private fun startClockLoop() {
        viewModelScope.launch {
            val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            while (true) {
                _currentTime.value = sdf.format(java.util.Date())
                delay(1000)
            }
        }
    }

    fun toggleCountryMenu(visible: Boolean? = null) {
        _isCountryMenuVisible.value = visible ?: !_isCountryMenuVisible.value
    }

    fun selectCategoryByNumber(num: Int) {
        val cats = channels.value.mapNotNull { it.groupTitle }.distinct()
        // Special case for the requirement: 1: Bangladesh, 2: India, 3: International
        val targetCategory = when (num) {
            1 -> cats.find { it.contains("Bangladesh", ignoreCase = true) || it.contains("BD", ignoreCase = true) }
            2 -> cats.find { it.contains("India", ignoreCase = true) || it.contains("IN", ignoreCase = true) }
            3 -> cats.find { !it.contains("Bangladesh", ignoreCase = true) && !it.contains("BD", ignoreCase = true) && 
                              !it.contains("India", ignoreCase = true) && !it.contains("IN", ignoreCase = true) }
            else -> null
        }
        
        if (targetCategory != null) {
            _selectedCategory.value = targetCategory
            // Jump to first channel in this category
            val firstInCat = channels.value.find { it.groupTitle == targetCategory && it.isActive }
            if (firstInCat != null) {
                setActiveChannel(firstInCat)
            }
        }
        _isCountryMenuVisible.value = false
    }

    fun toggleGridView() {
        _isGridViewVisible.value = !_isGridViewVisible.value
    }

    fun recallChannel() {
        _previousChannel.value?.let { prev ->
            setActiveChannel(prev)
        }
    }

    /**
     * Periodically fluctuates signal strength for retro realism.
     */
    private fun startSignalFluctuation() {
        viewModelScope.launch {
            while (true) {
                val current = _signalStrength.value
                val noise = ((0..10).random() - 5) / 100f // -0.05 to +0.05
                _signalStrength.value = (current + noise).coerceIn(0.4f, 1.0f)
                delay((1000..3000).random().toLong())
            }
        }
    }

    /**
     * Sleep timer logic
     */
    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = minutes
        if (minutes != null && minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                var remaining = minutes
                while (remaining > 0) {
                    delay(60000) // 1 minute
                    remaining--
                    _sleepTimerMinutes.value = remaining
                }
                // Time's up: stop playback
                _activeChannel.value = null
                _isStaticActive.value = true
                _sleepTimerMinutes.value = null
            }
        }
    }

    /**
     * Keyboard controls
     */
    fun toggleKeyboard(visible: Boolean) {
        _isKeyboardVisible.value = visible
        if (!visible) _keyboardInput.value = ""
    }

    fun updateKeyboardInput(input: String) {
        _keyboardInput.value = input
    }

    /**
     * Technical Stats controls
     */
    fun toggleTechnicalStats() {
        _technicalStatsVisible.value = !_technicalStatsVisible.value
    }

    fun updateStreamStats(width: Int, height: Int, codec: String, bitrate: String) {
        _streamResolution.value = if (width > 0) "${width}X${height}" else "N/A"
        _streamCodec.value = codec.uppercase()
        _streamBitrate.value = bitrate.uppercase()
    }

    /**
     * Parental Control logic
     */
    private fun checkChannelLock(channel: IPTVChannel) {
        val group = channel.groupTitle?.lowercase() ?: ""
        val isRestricted = group.contains("adult") || group.contains("xxx") || group.contains("restricted")
        _isChannelLocked.value = isRestricted
    }

    fun pressPinDigit(digit: Int) {
        if (_pinBuffer.value.length >= 4) return
        _pinBuffer.value += digit
        if (_pinBuffer.value.length == 4) {
            viewModelScope.launch {
                delay(300)
                if (_pinBuffer.value == "0000") { // Hardcoded default PIN
                    _isChannelLocked.value = false
                    _isPinEntryVisible.value = false
                }
                _pinBuffer.value = ""
            }
        }
    }

    /**
     * Category filtering
     */
    fun setCategory(category: String?) {
        _selectedCategory.value = category
        sharedPrefs.edit().putString("last_selected_category", category).apply()
    }

    /**
     * Power-on effect
     */
    fun dismissPowerOnEffect() {
        _isPowerOnEffectActive.value = false
    }

    fun toggleQuickLock() {
        _isQuickLocked.value = !_isQuickLocked.value
    }

    fun cycleFont() {
        val nextFont = (_selectedFontIndex.value + 1) % 3
        _selectedFontIndex.value = nextFont
        sharedPrefs.edit().putInt("selected_font_index", nextFont).apply()
    }

    private fun playWhiteNoiseSound() {
        // Sound logic omitted for now or can be added with SoundPool
    }

    /**
     * Triggers the analog auto-tuning animation.
     * Sweeps frequency sequentially from 45.25 MHz to 800 MHz.
     */
    fun runAnalogAutoTuning(isFirstLaunch: Boolean = false, playlistIdToSelect: Int? = null) {
        viewModelScope.launch {
            _isTuning.value = false
            _isStaticActive.value = false

            val currentChannels = repository.getAllChannelsNow()
            if (currentChannels.isEmpty()) {
                _tuningStatusText.value = "ERROR: NO CHANNELS DETECTED. PLEASE IMPORT M3U PLAYLIST SOURCE."
                return@launch
            }

            // Immediately tune to first channel or restore
            sharedPrefs.edit().putBoolean("has_tuned_channels", true).apply()
            
            if (playlistIdToSelect != null) {
                // Wait for both the playlist and channels flow to catch up
                var plist = playlists.value.find { it.id == playlistIdToSelect }
                var pChannels = repository.getChannelsByPlaylist(playlistIdToSelect)
                var attempts = 0
                while ((plist == null || pChannels.isEmpty()) && attempts < 15) {
                    delay(300)
                    plist = playlists.value.find { it.id == playlistIdToSelect }
                    pChannels = repository.getChannelsByPlaylist(playlistIdToSelect)
                    attempts++
                }
                
                if (pChannels.isNotEmpty()) {
                    _selectedSourceUrl.value = plist?.url
                    sharedPrefs.edit().putString("last_selected_source_url", plist?.url ?: "").apply()
                    setActiveChannel(pChannels.first())
                } else {
                    restoreLastWatchedChannel()
                }
            } else {
                restoreLastWatchedChannel()
            }
        }
    }

    /**
     * Sets the active channel with a retro 1.0s static transit delay.
     */
    fun showOsd() {
        osdHideJob?.cancel()
        _isOsdVisible.value = true
        osdHideJob = viewModelScope.launch {
            delay(4000)
            _isOsdVisible.value = false
        }
    }

    fun setActiveChannel(channel: IPTVChannel) {
        if (_activeChannel.value?.id == channel.id) return
        
        viewModelScope.launch {
            val current = _activeChannel.value
            if (current != null && current.id != channel.id) {
                _previousChannel.value = current
            }
            
            var finalChannel = channel
            if (!channel.isActive) {
                repository.updateChannelActiveStatus(channel.id, true)
                finalChannel = channel.copy(isActive = true)
            }
            
            checkChannelLock(finalChannel)
            _activeChannel.value = finalChannel

            // Update playback playlist for pre-loading (5-6 channels ahead) inside this playlist ONLY
            val allActive = channels.value
                .filter { it.playlistId == finalChannel.playlistId }
                .map { if (it.id == finalChannel.id) finalChannel else it }
                .filter { it.isActive }
            val index = allActive.indexOfFirst { it.id == finalChannel.id }
            if (index != -1) {
                _playbackPlaylist.value = allActive
                _playbackIndex.value = index
                preheatNextChannels(allActive, index)
            }
            
            // If locked, show PIN entry immediately
            if (_isChannelLocked.value) {
                _isPinEntryVisible.value = true
            }
            
            // Look up the playlist to get its source URL
            val playlist = playlists.value.find { it.id == finalChannel.playlistId }
            val sourceUrl = playlist?.url
            _selectedSourceUrl.value = sourceUrl

            // Save state
            sharedPrefs.edit()
                .putInt("last_watched_channel_num", finalChannel.channelNumber)
                .putString("last_selected_source_url", sourceUrl ?: "")
                .apply()

            // Reset signal/stream health indicators for the new channel
            _isStreamBuffering.value = false
            _isStreamError.value = false
            _streamErrorMessage.value = ""

            // Removed physical STB relay transition per user request for clean video
            _isStaticActive.value = false
            showOsd()
        }
    }

    /**
     * Toggles video scaling/aspect ratio mode.
     */
    fun toggleAspectRatio() {
        val nextMode = when (_aspectRatioMode.value) {
            "FILL" -> "FIT"
            "FIT" -> "ZOOM"
            else -> "FILL"
        }
        _aspectRatioMode.value = nextMode
        sharedPrefs.edit().putString("aspect_ratio_mode", nextMode).apply()
    }

    fun toggleFavorite(channel: IPTVChannel) {
        viewModelScope.launch {
            repository.toggleFavorite(channel)
        }
    }

    private var bufferingJob: Job? = null

    /**
     * Updates playback buffering state.
     */
    fun setStreamBuffering(buffering: Boolean) {
        _isStreamBuffering.value = buffering
        if (buffering) {
            bufferingJob?.cancel()
            bufferingJob = viewModelScope.launch {
                delay(10000L) // 10 seconds timeout
                if (_isStreamBuffering.value) {
                    setStreamError(true, "BUFFERING TIMEOUT")
                    markCurrentChannelInactive()
                }
            }
        } else {
            bufferingJob?.cancel()
            bufferingJob = null
        }
    }
    
    fun markCurrentChannelInactive() {
        val channel = _activeChannel.value ?: return
        viewModelScope.launch {
            repository.updateChannelActiveStatus(channel.id, false)
            zapNextChannel()
        }
    }

    /**
     * Updates stream playback errors and plays static noise burst if signal is lost.
     */
    fun setStreamError(error: Boolean, message: String = "") {
        _isStreamError.value = error
        _streamErrorMessage.value = message
        if (error) {
            playWhiteNoiseSound()
        }
    }

    /**
     * D-pad UP key zaps to next channel.
     */
    fun zapNextChannel() {
        val current = _activeChannel.value
        val playlistId = current?.playlistId
        val channelList = channels.value
            .filter { it.isActive && (playlistId == null || it.playlistId == playlistId) }
        if (channelList.isEmpty()) return
        val index = if (current != null) {
            val i = channelList.indexOfFirst { it.id == current.id }
            if (i == -1) 0 else (i + 1) % channelList.size
        } else {
            0
        }
        setActiveChannel(channelList[index])
    }

    /**
     * D-pad DOWN key zaps to previous channel.
     */
    fun zapPreviousChannel() {
        val current = _activeChannel.value
        val playlistId = current?.playlistId
        val channelList = channels.value
            .filter { it.isActive && (playlistId == null || it.playlistId == playlistId) }
        if (channelList.isEmpty()) return
        val index = if (current != null) {
            val i = channelList.indexOfFirst { it.id == current.id }
            if (i == -1) 0 else (i - 1 + channelList.size) % channelList.size
        } else {
            0
        }
        setActiveChannel(channelList[index])
    }

    /**
     * Receives set-top box remote numeric key events.
     * Accumulates digits, debounces for 1.5 seconds, then switches channel.
     */
    fun pressNumericKey(digit: Int) {
        numpadDebounceJob?.cancel()
        val currentBuffer = _inputBufferText.value + digit
        // Limit to 4 digits
        if (currentBuffer.length > 4) return
        _inputBufferText.value = currentBuffer

        numpadDebounceJob = viewModelScope.launch {
            delay(1000) // Shorter 1.0-second remote debounce for faster response
            commitNumpadInput()
        }
    }

    /**
     * Forces immediate switch of currently buffered numeric input.
     */
    fun commitNumpadInput() {
        val input = _inputBufferText.value
        _inputBufferText.value = ""
        if (input.isEmpty()) return

        val targetNum = input.toIntOrNull() ?: return
        val currentPlaylistId = _activeChannel.value?.playlistId
        viewModelScope.launch {
            val matchedChannel = repository.getChannelByNumber(targetNum, currentPlaylistId)
            if (matchedChannel != null) {
                setActiveChannel(matchedChannel)
            } else {
                // If channel is missing, flash static and show "NO SIGNAL"
                _isStaticActive.value = true
                playWhiteNoiseSound()
                delay(800)
                _isStaticActive.value = false
                Log.d("IptvViewModel", "Channel $targetNum not found")
            }
        }
    }

    /**
     * Retro volume adjustments.
     */
    fun setVolume(volume: Int) {
        val target = volume.coerceIn(0, 10)
        _currentVolume.value = target
        
        // Removed direct system volume manipulation to avoid AppOps attribution errors in preview
        
        _volumeDisplayVisible.value = true
        volumeHideJob?.cancel()
        volumeHideJob = viewModelScope.launch {
            delay(2000)
            _volumeDisplayVisible.value = false
        }
    }

    fun adjustVolume(increment: Boolean) {
        val current = _currentVolume.value
        val target = if (increment) (current + 1).coerceIn(0, 10) else (current - 1).coerceIn(0, 10)
        setVolume(target)
    }

    fun importPlaylistFromFile(name: String, uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            _isImporting.value = true
            _importProgressPercent.value = 0
            _importChannelCount.value = 0
            _importStatus.value = "OPENING LOCAL FILE..."
            
            val result = repository.importPlaylistFromLocalFile(name, uri, context) { status, percent, count ->
                _importStatus.value = status
                _importProgressPercent.value = percent
                _importChannelCount.value = count
            }
            
            _isImporting.value = false
            _importedPlaylistResult.emit(result.map { })
            if (result.isSuccess) {
                // Run analog tuning to scan these channels sequentially and select this playlist
                runAnalogAutoTuning(isFirstLaunch = false, playlistIdToSelect = result.getOrNull())
            }
        }
    }

    /**
     * Imports a playlist URL and triggers tuning.
     */
    fun importPlaylist(name: String, url: String) {
        viewModelScope.launch {
            _isImporting.value = true
            _importProgressPercent.value = 0
            _importChannelCount.value = 0
            _importStatus.value = "CONNECTING..."
            
            val result = repository.importPlaylistFromUrl(name, url) { status, percent, count ->
                _importStatus.value = status
                _importProgressPercent.value = percent
                _importChannelCount.value = count
            }
            
            _isImporting.value = false
            _importedPlaylistResult.emit(result.map { })
            if (result.isSuccess) {
                // Run analog tuning to scan these channels sequentially and select this playlist
                runAnalogAutoTuning(isFirstLaunch = false, playlistIdToSelect = result.getOrNull())
            }
        }
    }

    /**
     * Deletes a playlist link, wiping channels cascade.
     */
    fun deletePlaylist(playlistId: Int) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            
            // If the deleted playlist contained our active channel, auto reset channel
            val remaining = channels.value
            if (remaining.isEmpty()) {
                _activeChannel.value = null
            } else {
                val current = _activeChannel.value
                if (current == null || !remaining.any { it.id == current.id }) {
                    setActiveChannel(remaining.first())
                }
            }
        }
    }

    /**
     * Selects and plays the first channel of a playlist.
     */
    fun selectPlaylist(playlist: M3UPlaylist) {
        viewModelScope.launch {
            val list = channels.value.filter { it.playlistId == playlist.id }
            if (list.isNotEmpty()) {
                setActiveChannel(list.first())
            }
        }
    }

    private fun restoreLastWatchedChannel() {
        viewModelScope.launch {
            if (repository.getChannelCount() > 0) {
                // Wait for the flows to emit non-empty lists
                val playlistList = playlists.filter { it.isNotEmpty() }.first()
                val channelList = channels.filter { it.isNotEmpty() }.first()
                
                val savedNum = sharedPrefs.getInt("last_watched_channel_num", -1)
                val savedSourceUrl = sharedPrefs.getString("last_selected_source_url", null)
                
                _selectedSourceUrl.value = savedSourceUrl

                var targetChannel: IPTVChannel? = null

                if (!savedSourceUrl.isNullOrEmpty()) {
                    val matchingPlaylist = playlistList.find { it.url == savedSourceUrl }
                    if (matchingPlaylist != null) {
                        val playlistChannels = channelList.filter { it.playlistId == matchingPlaylist.id }
                        if (playlistChannels.isNotEmpty()) {
                            // Find the saved channel number within this playlist first
                            targetChannel = playlistChannels.find { it.channelNumber == savedNum }
                            // If not found, default to the first channel of this playlist
                            if (targetChannel == null) {
                                targetChannel = playlistChannels.first()
                            }
                        }
                    }
                }

                // Fallback to searching all channels by saved channel number
                if (targetChannel == null && savedNum != -1) {
                    targetChannel = channelList.find { it.channelNumber == savedNum }
                }

                // Ultimate fallback: first channel in the system
                if (targetChannel == null) {
                    targetChannel = channelList.first()
                }

                setActiveChannel(targetChannel)
            }
        }
    }

    private var preheatJob: Job? = null

    private fun preheatNextChannels(allActive: List<IPTVChannel>, currentIndex: Int) {
        preheatJob?.cancel()
        preheatJob = viewModelScope.launch(Dispatchers.IO) {
            if (allActive.isEmpty() || currentIndex == -1) return@launch
            
            // Wait 1.5 seconds so we do not interfere with the active channel's immediate buffering/playback startup
            delay(1500)
            
            val channelsToPreheat = mutableListOf<IPTVChannel>()
            
            // Previous 1 channel
            if (allActive.size > 1) {
                val prevIndex = (currentIndex - 1 + allActive.size) % allActive.size
                channelsToPreheat.add(allActive[prevIndex])
            }
            
            // Next 2 channels (Reduced from 5 to minimize network/CPU load on TVs)
            val nextCount = minOf(2, allActive.size - 1)
            for (i in 1..nextCount) {
                val nextIndex = (currentIndex + i) % allActive.size
                if (nextIndex != currentIndex && !channelsToPreheat.any { it.id == allActive[nextIndex].id }) {
                    channelsToPreheat.add(allActive[nextIndex])
                }
            }
            
            for (channel in channelsToPreheat) {
                val streamUrl = channel.url
                if (streamUrl.isBlank()) continue
                
                // Extra short delay between channels to avoid network congestion and CPU overhead on low-RAM TVs
                delay(300)
                
                try {
                    val url = java.net.URL(streamUrl)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 1500
                    conn.readTimeout = 1500
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    conn.requestMethod = "GET"
                    
                    val responseCode = conn.responseCode
                    if (responseCode in 200..299) {
                        // Read a single byte to ensure connection handshake is complete and warm in the pool
                        conn.inputStream.use { input ->
                            input.read()
                        }
                    }
                    conn.disconnect()
                    Log.d("IptvViewModel", "Warm-up connection successful for ${channel.name}")
                } catch (e: Exception) {
                    Log.w("IptvViewModel", "Preheat warm-up failed for ${channel.name}: ${e.message}")
                }
            }
        }
    }
}
