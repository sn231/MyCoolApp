package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumCategoryDao {
    @Query("SELECT * FROM album_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<AlbumCategory>>

    @Insert
    suspend fun insert(category: AlbumCategory)

    @Update
    suspend fun update(category: AlbumCategory)

    @Delete
    suspend fun delete(category: AlbumCategory)
}
