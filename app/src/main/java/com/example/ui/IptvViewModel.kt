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

class IptvViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: IptvRepository
    private val sharedPrefs = application.getSharedPreferences("sr_iptv_prefs", Context.MODE_PRIVATE)

    // Data streams
    val playlists: StateFlow<List<M3UPlaylist>>
    val channels: StateFlow<List<IPTVChannel>>

    // Core playback states
    private val _activeChannel = MutableStateFlow<IPTVChannel?>(null)
    val activeChannel: StateFlow<IPTVChannel?> = _activeChannel.asStateFlow()

    private val _selectedSourceUrl = MutableStateFlow<String?>(null)
    val selectedSourceUrl: StateFlow<String?> = _selectedSourceUrl.asStateFlow()

    private val _isStaticActive = MutableStateFlow(false)
    val isStaticActive: StateFlow<Boolean> = _isStaticActive.asStateFlow()

    // Dynamic Video Aspect Ratio (FILL, FIT, ZOOM)
    private val _aspectRatioMode = MutableStateFlow("FILL")
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
    private val _currentVolume = MutableStateFlow(7)
    val currentVolume: StateFlow<Int> = _currentVolume.asStateFlow()

    private val _volumeDisplayVisible = MutableStateFlow(false)
    val volumeDisplayVisible: StateFlow<Boolean> = _volumeDisplayVisible.asStateFlow()

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
        }
    }

    /**
     * Triggers the analog auto-tuning animation.
     * Sweeps frequency sequentially from 45.25 MHz to 800 MHz.
     */
    fun runAnalogAutoTuning(isFirstLaunch: Boolean = false) {
        viewModelScope.launch {
            _isTuning.value = true
            _isStaticActive.value = true
            playWhiteNoiseSound()

            val currentChannels = channels.value
            val totalSteps = 40
            val startFreq = 45.25f
            val endFreq = 800.00f
            val freqStep = (endFreq - startFreq) / totalSteps

            if (currentChannels.isEmpty()) {
                // No channels - sweep with error
                for (step in 0..totalSteps) {
                    val currentFreq = startFreq + (freqStep * step)
                    _tuningFrequency.value = currentFreq
                    _tuningStatusText.value = "SEARCHING VHF/UHF BANDS: ${String.format("%.2f", currentFreq)} MHz..."
                    
                    if (step % 12 == 0) {
                        playWhiteNoiseSound()
                    }
                    delay(80)
                }
                _tuningStatusText.value = "ERROR: NO CHANNELS DETECTED. PLEASE IMPORT M3U PLAYLIST SOURCE."
                delay(2000)
                _isTuning.value = false
                _isStaticActive.value = false
            } else {
                // Sweep and map real channels to frequency segments
                val stepInterval = (totalSteps / currentChannels.size).coerceAtLeast(1)
                for (step in 0..totalSteps) {
                    val currentFreq = startFreq + (freqStep * step)
                    _tuningFrequency.value = currentFreq
                    
                    // Check if we hit a step to lock a real channel
                    val channelIndex = step / stepInterval
                    if (step % stepInterval == 0 && channelIndex < currentChannels.size) {
                        val lockingChannel = currentChannels[channelIndex]
                        _tuningStatusText.value = "LOCKING CH ${lockingChannel.channelNumber}: ${lockingChannel.name.uppercase()} AT ${String.format("%.2f", currentFreq)} MHz..."
                        playWhiteNoiseSound()
                        delay(400)
                    } else {
                        _tuningStatusText.value = "SCANNING ANALOG FREQUENCY: ${String.format("%.2f", currentFreq)} MHz"
                        delay(80)
                    }
                }
                
                _tuningStatusText.value = "SUCCESS: ${currentChannels.size} CHANNELS INSTALLED!"
                delay(1500)
                _isTuning.value = false
                _isStaticActive.value = false
                
                // Tune to first channel or restore
                sharedPrefs.edit().putBoolean("has_tuned_channels", true).apply()
                restoreLastWatchedChannel()
            }
        }
    }

    /**
     * Sets the active channel with a retro 1.0s static transit delay.
     */
    fun setActiveChannel(channel: IPTVChannel) {
        viewModelScope.launch {
            _activeChannel.value = channel
            
            // Look up the playlist to get its source URL
            val playlist = playlists.value.find { it.id == channel.playlistId }
            val sourceUrl = playlist?.url
            _selectedSourceUrl.value = sourceUrl

            // Save state
            sharedPrefs.edit()
                .putInt("last_watched_channel_num", channel.channelNumber)
                .putString("last_selected_source_url", sourceUrl)
                .apply()

            // Reset signal/stream health indicators for the new channel
            _isStreamBuffering.value = false
            _isStreamError.value = false
            _streamErrorMessage.value = ""

            // Replicate 1.0s CRT channel changing static and sound
            staticOverlayJob?.cancel()
            _isStaticActive.value = true
            playWhiteNoiseSound()
            
            staticOverlayJob = viewModelScope.launch {
                delay(1000) // Exactly 1-second physical STB relay transition
                _isStaticActive.value = false
            }
        }
    }

    /**
     * Toggles video scaling/aspect ratio mode.
     */
    fun toggleAspectRatio() {
        _aspectRatioMode.value = when (_aspectRatioMode.value) {
            "FILL" -> "FIT"
            "FIT" -> "ZOOM"
            else -> "FILL"
        }
    }

    /**
     * Updates playback buffering state.
     */
    fun setStreamBuffering(buffering: Boolean) {
        _isStreamBuffering.value = buffering
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
        val channelList = channels.value
        if (channelList.isEmpty()) return
        val current = _activeChannel.value
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
        val channelList = channels.value
        if (channelList.isEmpty()) return
        val current = _activeChannel.value
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
            delay(1500) // 1.5-second remote debounce
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
        viewModelScope.launch {
            val matchedChannel = repository.getChannelByNumber(targetNum)
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
    fun adjustVolume(increment: Boolean) {
        val current = _currentVolume.value
        if (increment && current < 10) {
            _currentVolume.value = current + 1
        } else if (!increment && current > 0) {
            _currentVolume.value = current - 1
        }
        _volumeDisplayVisible.value = true

        volumeHideJob?.cancel()
        volumeHideJob = viewModelScope.launch {
            delay(2000)
            _volumeDisplayVisible.value = false
        }
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
            _importedPlaylistResult.emit(result)
            if (result.isSuccess) {
                // Run analog tuning to scan these channels sequentially
                runAnalogAutoTuning(isFirstLaunch = false)
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
            _importedPlaylistResult.emit(result)
            if (result.isSuccess) {
                // Run analog tuning to scan these channels sequentially
                runAnalogAutoTuning(isFirstLaunch = false)
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

                _activeChannel.value = targetChannel
            }
        }
    }
}
