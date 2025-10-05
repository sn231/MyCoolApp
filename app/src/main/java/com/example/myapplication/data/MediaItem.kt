package com.example.myapplication.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

enum class MediaType {
    PHOTO,
    VIDEO
}

@Parcelize
@Entity(
    tableName = "media_items",
    foreignKeys = [ForeignKey(
        entity = AlbumCategory::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["categoryId"])]
)
data class MediaItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uri: String,
    val categoryId: Long,
    val type: MediaType,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isFavorite: Boolean = false,
    val duration: Long? = null
) : Parcelable
