package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlists")
data class M3UPlaylist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = M3UPlaylist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"]), Index(value = ["channelNumber"], unique = true)]
)
data class IPTVChannel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Int,
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val channelNumber: Int
)

@Dao
interface IptvDao {
    @Query("SELECT * FROM playlists ORDER BY addedAt DESC")
    fun getAllPlaylists(): Flow<List<M3UPlaylist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: M3UPlaylist): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Int)

    @Query("SELECT * FROM channels ORDER BY channelNumber ASC")
    fun getAllChannels(): Flow<List<IPTVChannel>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY channelNumber ASC")
    suspend fun getChannelsByPlaylist(playlistId: Int): List<IPTVChannel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<IPTVChannel>)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsByPlaylist(playlistId: Int)

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun getChannelCount(): Int

    @Query("SELECT MAX(channelNumber) FROM channels")
    suspend fun getMaxChannelNumber(): Int?

    @Query("SELECT * FROM channels WHERE channelNumber = :num LIMIT 1")
    suspend fun getChannelByNumber(num: Int): IPTVChannel?
}

@Database(
    entities = [M3UPlaylist::class, IPTVChannel::class],
    version = 1,
    exportSchema = false
)
abstract class IptvDatabase : RoomDatabase() {
    abstract fun iptvDao(): IptvDao
}
