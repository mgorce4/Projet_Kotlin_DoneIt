package com.example.doneit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titre: String,
    val description: String?, // Optionnel
    val dateLimite: String?, // Optionnel
    val heureLimite: String?, // Optionnel
    val priorite: Int = 0,
    val photoUrl: String? = null,
    val xpReward: Int = 10,
    val status: TaskStatus = TaskStatus.TODO,
    val periodicity: PeriodicityType = PeriodicityType.NONE
)