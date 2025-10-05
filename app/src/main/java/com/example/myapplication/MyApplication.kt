package com.example.myapplication

import android.app.Application
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.MediaRepository

class MyApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { MediaRepository(database.mediaItemDao(), database.danmakuItemDao(), database.albumCategoryDao()) }
}
