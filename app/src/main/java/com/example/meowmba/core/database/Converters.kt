package com.example.meowmba.core.database

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromMessageRole(role: MessageRole): String = role.name

    @TypeConverter
    fun toMessageRole(role: String): MessageRole = 
        try { MessageRole.valueOf(role.uppercase()) } catch (e: Exception) { MessageRole.USER }

    @TypeConverter
    fun fromImportance(importance: ImportanceLevel): Int = importance.value

    @TypeConverter
    fun toImportance(value: Int): ImportanceLevel =
        ImportanceLevel.entries.find { it.value == value } ?: ImportanceLevel.LOW

    @TypeConverter
    fun fromMemoryType(type: MemoryType): String = type.name

    @TypeConverter
    fun toMemoryType(type: String): MemoryType =
        try { MemoryType.valueOf(type.uppercase()) } catch (e: Exception) { MemoryType.SEMANTIC }

    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun toStringList(value: String): List<String> = 
        if (value.isBlank()) emptyList() else value.split(",")
}
