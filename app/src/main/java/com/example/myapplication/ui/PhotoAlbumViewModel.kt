package com.example.myapplication.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AlbumCategory
import com.example.myapplication.data.DanmakuItem
import com.example.myapplication.data.MediaItem
import com.example.myapplication.data.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PhotoAlbumViewModel(private val repository: MediaRepository) : ViewModel() {

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (repository.getAllCategories().first().isEmpty()) {
                repository.insertCategory(AlbumCategory(name = "默认相册"))
            }
        }
    }

    val allItems: StateFlow<List<MediaItem>> = repository.getAllItems()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allCategories: StateFlow<List<AlbumCategory>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun getItemsForCategory(categoryId: Long): StateFlow<List<MediaItem>> = repository.getItemsForCategory(categoryId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun insert(item: MediaItem) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(item)
    }

    fun update(item: MediaItem) = viewModelScope.launch(Dispatchers.IO) {
        repository.update(item)
    }

    fun delete(item: MediaItem) = viewModelScope.launch(Dispatchers.IO) {
        repository.delete(item)
    }

    fun getDanmakusForMedia(mediaId: Long): StateFlow<List<DanmakuItem>> = repository.getDanmakusForMedia(mediaId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun insertDanmaku(danmaku: DanmakuItem) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertDanmaku(danmaku)
    }

    fun insertCategory(category: AlbumCategory) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertCategory(category)
    }

    fun updateCategory(category: AlbumCategory) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateCategory(category)
    }

    fun deleteCategory(category: AlbumCategory) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteCategory(category)
    }
}

class PhotoAlbumViewModelFactory(private val repository: MediaRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PhotoAlbumViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PhotoAlbumViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
