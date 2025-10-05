package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    @Insert
    suspend fun insert(item: MediaItem)

    @Update
    suspend fun update(item: MediaItem)

    @Delete
    suspend fun delete(item: MediaItem)

    @Query("SELECT * FROM media_items ORDER BY id DESC")
    fun getAllItems(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE categoryId = :categoryId ORDER BY id DESC")
    fun getItemsForCategory(categoryId: Long): Flow<List<MediaItem>>
}
