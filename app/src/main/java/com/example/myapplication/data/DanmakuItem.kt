package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "danmaku_items",
    foreignKeys = [ForeignKey(
        entity = MediaItem::class,
        parentColumns = ["id"],
        childColumns = ["mediaId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["mediaId"])]
)
data class DanmakuItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaId: Long,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
