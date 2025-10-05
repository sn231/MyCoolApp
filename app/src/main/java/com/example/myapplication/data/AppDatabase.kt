package com.example.myapplication.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MediaItem::class, DanmakuItem::class, AlbumCategory::class], version = 7, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mediaItemDao(): MediaItemDao
    abstract fun danmakuItemDao(): DanmakuItemDao
    abstract fun albumCategoryDao(): AlbumCategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "media_item_database"
                )
                .addMigrations(MIGRATION_6_7)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE media_items ADD COLUMN duration INTEGER")
            }
        }
    }
}
