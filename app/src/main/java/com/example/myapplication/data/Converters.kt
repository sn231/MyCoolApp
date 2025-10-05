package com.example.myapplication.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromString(value: String?): List<String> {
        return value?.split(',')?.filter { it.isNotEmpty() } ?: emptyList()
    }

    @TypeConverter
    fun fromList(list: List<String>?): String {
        return list?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toMediaType(value: String) = enumValueOf<MediaType>(value)

    @TypeConverter
    fun fromMediaType(value: MediaType) = value.name
}
