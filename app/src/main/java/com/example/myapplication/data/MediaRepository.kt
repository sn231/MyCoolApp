package com.example.myapplication.data

import kotlinx.coroutines.flow.Flow

class MediaRepository(
    private val mediaItemDao: MediaItemDao, 
    private val danmakuItemDao: DanmakuItemDao,
    private val albumCategoryDao: AlbumCategoryDao
) {

    fun getAllItems(): Flow<List<MediaItem>> = mediaItemDao.getAllItems()

    fun getItemsForCategory(categoryId: Long): Flow<List<MediaItem>> =
        mediaItemDao.getItemsForCategory(categoryId)

    suspend fun insert(item: MediaItem) {
        mediaItemDao.insert(item)
    }

    suspend fun update(item: MediaItem) {
        mediaItemDao.update(item)
    }

    suspend fun delete(item: MediaItem) {
        mediaItemDao.delete(item)
    }

    fun getDanmakusForMedia(mediaId: Long): Flow<List<DanmakuItem>> = danmakuItemDao.getDanmakusForMedia(mediaId)

    suspend fun insertDanmaku(danmaku: DanmakuItem) {
        danmakuItemDao.insert(danmaku)
    }

    fun getAllCategories(): Flow<List<AlbumCategory>> = albumCategoryDao.getAllCategories()

    suspend fun insertCategory(category: AlbumCategory) {
        albumCategoryDao.insert(category)
    }

    suspend fun updateCategory(category: AlbumCategory) {
        albumCategoryDao.update(category)
    }

    suspend fun deleteCategory(category: AlbumCategory) {
        albumCategoryDao.delete(category)
    }
}
