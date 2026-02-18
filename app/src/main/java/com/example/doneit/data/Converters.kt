package com.example.doneit.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromTaskStatus(value: TaskStatus): String {
        return value.name
    }

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus {
        return TaskStatus.valueOf(value)
    }

    @TypeConverter
    fun fromPeriodicityType(value: PeriodicityType): String {
        return value.name
    }

    @TypeConverter
    fun toPeriodicityType(value: String): PeriodicityType {
        return PeriodicityType.valueOf(value)
    }
}

