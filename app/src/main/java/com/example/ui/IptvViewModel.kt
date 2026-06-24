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

    private val _isStaticActive = MutableStateFlow(false)
    val isStaticActive: StateFlow<Boolean> = _isStaticActive.asStateFlow()

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

            val totalSteps = 40
            val startFreq = 45.25f
            val endFreq = 800.00f
            val freqStep = (endFreq - startFreq) / totalSteps

            for (step in 0..totalSteps) {
                val currentFreq = startFreq + (freqStep * step)
                _tuningFrequency.value = currentFreq
                _tuningStatusText.value = "TUNING ANALOG BAND: ${String.format("%.2f", currentFreq)} MHz"
                
                // Play short burst of white noise sound occasionally
                if (step % 12 == 0) {
                    playWhiteNoiseSound()
                }

                // Simulate locking a channel signal
                if (step == 8 || step == 16 || step == 24 || step == 32 || step == 38) {
                    _tuningStatusText.value = "SIGNAL FOUND AT ${String.format("%.2f", currentFreq)} MHz - SAVING..."
                    delay(350)
                } else {
                    delay(80)
                }
            }

            _tuningStatusText.value = "AUTO-TUNING COMPLETE! CHANNELS LOCKED."
            delay(1000)

            _isTuning.value = false
            sharedPrefs.edit().putBoolean("has_tuned_channels", true).apply()
            
            // Auto play the first channel
            channels.value.firstOrNull()?.let {
                setActiveChannel(it)
            } ?: run {
                _isStaticActive.value = false
            }
        }
    }

    /**
     * Sets the active channel with a retro 1.0s static transit delay.
     */
    fun setActiveChannel(channel: IPTVChannel) {
        viewModelScope.launch {
            _activeChannel.value = channel
            // Save state
            sharedPrefs.edit().putInt("last_watched_channel_num", channel.channelNumber).apply()

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

    /**
     * Imports a playlist URL and triggers tuning.
     */
    fun importPlaylist(name: String, url: String) {
        viewModelScope.launch {
            val result = repository.importPlaylistFromUrl(name, url)
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

    private fun restoreLastWatchedChannel() {
        viewModelScope.launch {
            val savedNum = sharedPrefs.getInt("last_watched_channel_num", -1)
            val list = channels.value
            if (list.isNotEmpty()) {
                val lastChannel = list.find { it.channelNumber == savedNum }
                if (lastChannel != null) {
                    setActiveChannel(lastChannel)
                } else {
                    setActiveChannel(list.first())
                }
            }
        }
    }
}
