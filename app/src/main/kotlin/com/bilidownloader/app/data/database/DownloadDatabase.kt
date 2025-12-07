package com.bilidownloader.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import com.bilidownloader.app.data.model.DownloadRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadRecordDao {
    @Query("SELECT * FROM download_records ORDER BY downloadTime DESC")
    fun getAllRecords(): Flow<List<DownloadRecord>>
    
    @Query("SELECT * FROM download_records WHERE videoId = :videoId AND cid = :cid AND quality = :quality LIMIT 1")
    suspend fun getRecord(videoId: String, cid: Long, quality: Int): DownloadRecord?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DownloadRecord)
    
    @Delete
    suspend fun delete(record: DownloadRecord)
    
    @Query("DELETE FROM download_records")
    suspend fun deleteAll()
    
    @Query("SELECT EXISTS(SELECT 1 FROM download_records WHERE videoId = :videoId AND cid = :cid AND quality = :quality)")
    suspend fun isDownloaded(videoId: String, cid: Long, quality: Int): Boolean
    
    @Query("SELECT * FROM download_records WHERE videoId = :videoId")
    suspend fun getRecordsByVideoId(videoId: String): List<DownloadRecord>
}

@Database(entities = [DownloadRecord::class], version = 1, exportSchema = false)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadRecordDao(): DownloadRecordDao
    
    companion object {
        @Volatile
        private var INSTANCE: DownloadDatabase? = null
        
        fun getDatabase(context: Context): DownloadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "bili_download_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}