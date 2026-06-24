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

    suspend fun getChannelCount(): Int = withContext(Dispatchers.IO) {
        iptvDao.getChannelCount()
    }

    suspend fun getChannelByNumber(num: Int): IPTVChannel? = withContext(Dispatchers.IO) {
        iptvDao.getChannelByNumber(num)
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
     */
    suspend fun importPlaylistFromUrl(name: String, urlString: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("IptvRepository", "Importing playlist: $name from $urlString")
            val content = fetchUrlContent(urlString)
            if (content.isEmpty() || !content.contains("#EXTM3U")) {
                return@withContext Result.failure(Exception("Invalid M3U playlist content (missing #EXTM3U)"))
            }

            // Create the playlist entity
            val playlist = M3UPlaylist(name = name, url = urlString)
            val playlistId = iptvDao.insertPlaylist(playlist).toInt()

            // Find the starting channel number to prevent overlap
            val maxChannelNum = iptvDao.getMaxChannelNumber() ?: 0
            val startChannelNum = if (maxChannelNum == 0) 1 else maxChannelNum + 1

            // Parse and insert channels
            val channels = parseM3u(playlistId, content, startChannelNum)
            if (channels.isNotEmpty()) {
                iptvDao.insertChannels(channels)
                Log.d("IptvRepository", "Imported ${channels.size} channels.")
                Result.success(Unit)
            } else {
                // Delete empty playlist to clean up
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

    private fun fetchUrlContent(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.doInput = true

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            reader.close()
            return sb.toString()
        } else {
            throw Exception("HTTP Error: $responseCode")
        }
    }

    private fun parseM3u(playlistId: Int, content: String, startChannelNum: Int): List<IPTVChannel> {
        val channels = mutableListOf<IPTVChannel>()
        val lines = content.lines()
        var currentName: String? = null
        var currentLogoUrl: String? = null
        var currentGroup: String? = null
        var channelIndex = startChannelNum

        for (line in lines) {
            val trimmed = line.trim()
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
                // Skip other tags
                continue
            } else {
                // This is a stream URL line
                val streamUrl = trimmed
                if (streamUrl.startsWith("http://") || streamUrl.startsWith("https://")) {
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
                }
            }
        }
        return channels
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
