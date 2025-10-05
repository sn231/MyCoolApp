package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "album_categories")
data class AlbumCategory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String
)
