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

    suspend fun getChannelByNumber(num: Int): IPTVChannel? = withContext(Dispatchers.IO) {
        iptvDao.getChannelByNumber(num)
    }

    suspend fun toggleFavorite(channel: IPTVChannel) = withContext(Dispatchers.IO) {
        iptvDao.updateChannel(channel.copy(isFavorite = !channel.isFavorite))
    }

    /**
     * Seeds the database with default channels if it is empty.
     * This creates a system playlist with ID 1 and populates it with beautiful live feeds.
     */
    suspend fun seedDefaultChannelsIfEmpty() = withContext(Dispatchers.IO) {
        val count = iptvDao.getChannelCount()
        if (count == 0) {
            Log.d("IptvRepository", "Database is empty. Seeding default analog streams...")
            val playlistId = iptvDao.insertPlaylist(
                M3UPlaylist(
                    id = 1,
                    name = "Retro Default Feed",
                    url = "system://default"
                )
            ).toInt()

            val defaultStreams = listOf(
                IPTVChannel(
                    playlistId = playlistId,
                    name = "NASA TV Media",
                    url = "https://nasa-ottdestination.amagi.tv/playlist.m3u8",
                    logoUrl = "https://www.nasa.gov/wp-content/themes/nasa/assets/images/nasa-logo.svg",
                    groupTitle = "Science",
                    channelNumber = 1
                ),
                IPTVChannel(
                    playlistId = playlistId,
                    name = "Akamai Test Feed",
                    url = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
                    logoUrl = null,
                    groupTitle = "Test Cards",
                    channelNumber = 2
                ),
                IPTVChannel(
                    playlistId = playlistId,
                    name = "Mux Bunny Channel",
                    url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    logoUrl = null,
                    groupTitle = "Retro Cartoons",
                    channelNumber = 3
                ),
                IPTVChannel(
                    playlistId = playlistId,
                    name = "Tears of Steel HLS",
                    url = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                    logoUrl = null,
                    groupTitle = "Cinematics",
                    channelNumber = 4
                ),
                IPTVChannel(
                    playlistId = playlistId,
                    name = "Sintel Movie Feed",
                    url = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
                    logoUrl = null,
                    groupTitle = "Cinematics",
                    channelNumber = 5
                )
            )
            iptvDao.insertChannels(defaultStreams)
            Log.d("IptvRepository", "Default channels seeded successfully.")
        }
    }

    /**
     * Downloads and parses an M3U playlist from a URL, assigning sequential channel numbers.
     * Reports live progress via onProgress callback.
     */
    suspend fun importPlaylistFromUrl(
        name: String,
        urlString: String,
        onProgress: (status: String, percent: Int, channelCount: Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
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
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
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
    ): Result<Unit> = withContext(Dispatchers.IO) {
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
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(inputStream))
            onProgress("READING PLAYLIST...", 5, 0)

            // Create the playlist entity
            val playlist = M3UPlaylist(name = name, url = urlString)
            val playlistId = iptvDao.insertPlaylist(playlist).toInt()

            // Find starting channel number to prevent overlap
            val maxChannelNum = iptvDao.getMaxChannelNumber() ?: 0
            val startChannelNum = if (maxChannelNum == 0) 1 else maxChannelNum + 1

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
                                channelNumber = channelIndex++
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
                onProgress("SAVING ${channels.size} CHANNELS TO DATABASE...", 92, channels.size)
                
                // Save in batches of 500 to prevent SQLite bulk insert failures
                val chunkSize = 500
                val totalChunks = (channels.size + chunkSize - 1) / chunkSize
                channels.chunked(chunkSize).forEachIndexed { index, batch ->
                    iptvDao.insertChannels(batch)
                    val percent = 92 + ((index + 1) * 8 / totalChunks).coerceIn(0, 8)
                    onProgress("SAVING CHANNELS (${percent}%)...", percent, channels.size)
                }
                
                onProgress("IMPORT COMPLETED!", 100, channels.size)
                Log.d("IptvRepository", "Imported ${channels.size} channels.")
                Result.success(Unit)
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
