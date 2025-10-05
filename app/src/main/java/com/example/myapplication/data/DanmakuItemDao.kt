package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DanmakuItemDao {
    @Insert
    suspend fun insert(danmaku: DanmakuItem)

    @Query("SELECT * FROM danmaku_items WHERE mediaId = :mediaId ORDER BY timestamp ASC")
    fun getDanmakusForMedia(mediaId: Long): Flow<List<DanmakuItem>>
}
