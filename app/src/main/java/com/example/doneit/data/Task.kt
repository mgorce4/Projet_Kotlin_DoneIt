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
    val priorite: Int?, // 1 (faible) à 5 (élevée) optionnel pour la première version la priorite n'étant pas mise en place
    val photoUrl: String? = null,
    val xpReward: Int? ,//Optionnel pour la première version la récompense en XP n'étant pas mise en place
    val status: TaskStatus = TaskStatus.TODO,
    val periodicity: PeriodicityType = PeriodicityType.NONE,
    // Prochaine occurrence pour les tâches périodiques (format yyyy-MM-dd HH:mm)
    val nextOccurrence: String? = null
)