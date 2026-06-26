package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class IptvRepository(private val iptvDao: IptvDao) {

    val playlists: Flow<List<M3UPlaylist>> = iptvDao.getAllPlaylists()
    val channels: Flow<List<IPTVChannel>> = iptvDao.getAllChannels()

    suspend fun getAllChannelsNow(): List<IPTVChannel> = withContext(Dispatchers.IO) {
        iptvDao.getAllChannelsNow()
    }

    suspend fun getChannelCount(): Int = withContext(Dispatchers.IO) {
        iptvDao.getChannelCount()
    }

    suspend fun updateChannelActiveStatus(channelId: Int, isActive: Boolean) = withContext(Dispatchers.IO) {
        iptvDao.updateChannelActiveStatus(channelId, isActive)
    }

    suspend fun getChannelByNumber(num: Int, playlistId: Int? = null): IPTVChannel? = withContext(Dispatchers.IO) {
        if (playlistId != null) {
            iptvDao.getChannelByNumberAndPlaylist(playlistId, num)
        } else {
            iptvDao.getChannelByNumber(num)
        }
    }

    suspend fun toggleFavorite(channel: IPTVChannel) = withContext(Dispatchers.IO) {
        iptvDao.updateChannel(channel.copy(isFavorite = !channel.isFavorite))
    }

    /**
     * Seeds the database with default channels if it is empty.
     * This creates a system playlist with ID 1 and populates it with beautiful live feeds.
     */
    suspend fun seedDefaultChannelsIfEmpty() = withContext(Dispatchers.IO) {
        // No-op. Starting clean without pre-loaded demo active channels.
    }

    /**
     * Downloads and parses an M3U playlist from a URL, assigning sequential channel numbers.
     * Reports live progress via onProgress callback.
     */
    suspend fun importPlaylistFromUrl(
        name: String,
        urlString: String,
        onProgress: (status: String, percent: Int, channelCount: Int) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            onProgress("CONNECTING TO SERVER...", 0, 0)
            Log.d("IptvRepository", "Importing playlist: $name from $urlString")
            
            var currentUrl = urlString
            var connection: HttpURLConnection? = null
            var redirectCount = 0
            val maxRedirects = 5
            var responseCode = -1

            while (redirectCount < maxRedirects) {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty(
                    "User-Agent", 
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = false
                connection.doInput = true

                responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 ||
                    responseCode == 308
                ) {
                    val newUrl = connection.getHeaderField("Location")
                    if (newUrl.isNullOrEmpty()) {
                        break
                    }
                    currentUrl = newUrl
                    redirectCount++
                    Log.d("IptvRepository", "Following redirect $redirectCount to $currentUrl")
                    connection.disconnect()
                } else {
                    break
                }
            }

            if (connection == null || responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP Error: $responseCode"))
            }

            val contentLength = connection.contentLength
            val inputStream = connection.inputStream
            
            importPlaylistFromInputStream(name, urlString, inputStream, contentLength, onProgress)
        } catch (e: Exception) {
            Log.e("IptvRepository", "Failed to import playlist", e)
            Result.failure(e)
        }
    }

    suspend fun importPlaylistFromLocalFile(
        name: String,
        uri: android.net.Uri,
        context: android.content.Context,
        onProgress: (status: String, percent: Int, channelCount: Int) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            onProgress("OPENING LOCAL FILE...", 0, 0)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open file: $uri"))
            
            // Get approximate length from file
            var contentLength = -1
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    contentLength = cursor.getLong(sizeIndex).toInt()
                }
            }

            importPlaylistFromInputStream(name, uri.toString(), inputStream, contentLength, onProgress)
        } catch (e: Exception) {
            Log.e("IptvRepository", "Failed to import local playlist", e)
            Result.failure(e)
        }
    }

    private suspend fun importPlaylistFromInputStream(
        name: String,
        urlString: String,
        inputStream: java.io.InputStream,
        contentLength: Int,
        onProgress: (status: String, percent: Int, channelCount: Int) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(inputStream))
            onProgress("READING PLAYLIST...", 5, 0)

            // Create the playlist entity
            val playlist = M3UPlaylist(name = name, url = urlString)
            val playlistId = iptvDao.insertPlaylist(playlist).toInt()

            // Every playlist's channel numbers start from 1
            val startChannelNum = 1

            val channels = mutableListOf<IPTVChannel>()
            var currentName: String? = null
            var currentLogoUrl: String? = null
            var currentGroup: String? = null
            var channelIndex = startChannelNum

            var line: String?
            var bytesRead = 0L
            
            // We read line-by-line
            var isFirstLine = true
            var isM3u = true // Assume true, don't fail immediately

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                val trimmed = currentLine.trim()
                bytesRead += currentLine.length + 1 // Approximate bytes read

                if (trimmed.isEmpty()) continue

                if (trimmed.startsWith("#EXTINF:")) {
                    // Extract metadata
                    currentName = parseTagValue(trimmed, "tvg-name")
                    currentLogoUrl = parseTagValue(trimmed, "tvg-logo")
                    currentGroup = parseTagValue(trimmed, "group-title")
                    val currentCountry = parseTagValue(trimmed, "tvg-country")
                    
                    if (currentGroup.isNullOrEmpty() && !currentCountry.isNullOrEmpty()) {
                        currentGroup = currentCountry
                    }

                    // Fallback to name after last comma if tvg-name is missing or empty
                    val commaIndex = trimmed.lastIndexOf(',')
                    if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                        val candidateName = trimmed.substring(commaIndex + 1).trim()
                        if (candidateName.isNotEmpty()) {
                            currentName = candidateName
                        }
                    }
                } else if (trimmed.startsWith("#")) {
                    continue
                } else {
                    // Stream URL line
                    val streamUrl = trimmed
                    if (streamUrl.isNotEmpty()) {
                        channels.add(
                            IPTVChannel(
                                playlistId = playlistId,
                                name = currentName ?: "Channel $channelIndex",
                                url = streamUrl,
                                logoUrl = currentLogoUrl,
                                groupTitle = currentGroup ?: "General",
                                channelNumber = 0 // Will be assigned later
                            )
                        )
                        // Reset transient fields
                        currentName = null
                        currentLogoUrl = null
                        currentGroup = null

                        // Periodically report progress to not overflow the UI state updates
                        if (channels.size % 20 == 0) {
                            val percent = if (contentLength > 0) {
                                (bytesRead * 100 / contentLength).toInt().coerceIn(10, 90)
                            } else {
                                (10 + (channels.size % 80)) // simple progress that wraps
                            }
                            onProgress("PARSING: ${channels.size} CHANNELS FOUND...", percent, channels.size)
                        }
                    }
                }
            }
            reader.close()

            if (!isM3u) {
                iptvDao.deletePlaylist(playlistId)
                return@withContext Result.failure(Exception("Invalid M3U: Missing #EXTM3U header"))
            }

            if (channels.isNotEmpty()) {
                onProgress("SORTING CHANNELS...", 90, channels.size)
                
                // Country-wise sorting (BD -> IN -> Others)
                val sortedChannels = channels.sortedWith { ch1, ch2 ->
                    val getPriority = { group: String? ->
                        val lower = group?.lowercase() ?: ""
                        if (lower.contains("bangladesh") || lower.contains("bd")) 1
                        else if (lower.contains("india") || lower.contains("in")) 2
                        else 3
                    }
                    val p1 = getPriority(ch1.groupTitle)
                    val p2 = getPriority(ch2.groupTitle)
                    p1.compareTo(p2)
                }.mapIndexed { index, channel ->
                    channel.copy(channelNumber = startChannelNum + index)
                }

                onProgress("SAVING ${sortedChannels.size} CHANNELS TO DATABASE...", 92, sortedChannels.size)
                
                // Save in batches of 500 to prevent SQLite bulk insert failures
                val chunkSize = 500
                val totalChunks = (sortedChannels.size + chunkSize - 1) / chunkSize
                sortedChannels.chunked(chunkSize).forEachIndexed { index, batch ->
                    iptvDao.insertChannels(batch)
                    val percent = 92 + ((index + 1) * 8 / totalChunks).coerceIn(0, 8)
                    onProgress("SAVING CHANNELS (${percent}%)...", percent, sortedChannels.size)
                }
                
                onProgress("IMPORT COMPLETED!", 100, sortedChannels.size)
                Log.d("IptvRepository", "Imported ${channels.size} channels.")
                Result.success(playlistId)
            } else {
                iptvDao.deletePlaylist(playlistId)
                Result.failure(Exception("No valid channels found in the M3U file"))
            }
        } catch (e: Exception) {
            Log.e("IptvRepository", "Failed to import playlist", e)
            Result.failure(e)
        }
    }

    /**
     * Wipes a playlist and cascades the channel deletions instantly.
     */
    suspend fun deletePlaylist(playlistId: Int) = withContext(Dispatchers.IO) {
        iptvDao.deletePlaylist(playlistId)
    }

    suspend fun getChannelsByPlaylist(playlistId: Int): List<IPTVChannel> = withContext(Dispatchers.IO) {
        iptvDao.getChannelsByPlaylist(playlistId)
    }

    private fun parseTagValue(line: String, tagName: String): String? {
        val pattern = "$tagName=\""
        val startIndex = line.indexOf(pattern)
        if (startIndex == -1) return null
        val valueStart = startIndex + pattern.length
        val endIndex = line.indexOf('"', valueStart)
        if (endIndex == -1) return null
        return line.substring(valueStart, endIndex)
    }
}
