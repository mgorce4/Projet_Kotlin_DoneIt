package com.example.doneit.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doneit.data.PeriodicityType
import com.example.doneit.data.Task
import com.example.doneit.data.TaskDao
import com.example.doneit.data.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TaskViewModel(private val taskDao: TaskDao) : ViewModel() {

    // Lister toutes les tâches (Observé par l'UI)
    val allTasks: Flow<List<Task>> = taskDao.getAllTask()

    // Signal pour déclencher l'effet waou dans l'UI
    private val _waouEvent = MutableStateFlow<Task?>(null)
    val waouEvent: StateFlow<Task?> = _waouEvent

    // Signal pour déclencher une notif overdue dans l'UI
    private val _overdueEvent = MutableStateFlow<Task?>(null)
    val overdueEvent: StateFlow<Task?> = _overdueEvent

    // Ajouter une tâche
    fun addTask(task: Task) {
        viewModelScope.launch { taskDao.insertTask(task) }
    }

    //Marquer comme réalisée avec "Effet Waou"
    fun completeTaskWithReward(task: Task) {
        viewModelScope.launch {
            if (task.periodicity != PeriodicityType.NONE) {
                // Tâche périodique : on calcule la prochaine occurrence et on remet à TODO
                val next = computeNextOccurrence(task)
                val updatedTask = task.copy(
                    status = TaskStatus.DONE,
                    nextOccurrence = next
                )
                taskDao.updateTask(updatedTask)
                _waouEvent.value = updatedTask
            } else {
                val updatedTask = task.copy(status = TaskStatus.DONE)
                taskDao.updateTask(updatedTask)
                _waouEvent.value = updatedTask
            }
        }
    }

    /** Calcule la prochaine occurrence en fonction de la périodicité */
    fun computeNextOccurrence(task: Task): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val base = when {
            task.nextOccurrence != null -> try { fmt.parse(task.nextOccurrence)!! } catch (_: Exception) { Date() }
            task.dateLimite != null && task.heureLimite != null ->
                try { fmt.parse("${task.dateLimite} ${task.heureLimite}")!! } catch (_: Exception) { Date() }
            else -> Date()
        }
        val cal = Calendar.getInstance().apply { time = base }
        when (task.periodicity) {
            PeriodicityType.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            PeriodicityType.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            PeriodicityType.MONTHLY -> cal.add(Calendar.MONTH, 1)
            PeriodicityType.NONE -> {}
        }
        return fmt.format(cal.time)
    }

    /** Remet une tâche périodique DONE à TODO quand sa nextOccurrence est atteinte */
    fun resetPeriodicTaskIfDue(task: Task) {
        if (task.periodicity == PeriodicityType.NONE || task.status != TaskStatus.DONE) return
        viewModelScope.launch {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val nextMillis = try {
                task.nextOccurrence?.let { fmt.parse(it)?.time } ?: return@launch
            } catch (_: Exception) { return@launch }
            if (System.currentTimeMillis() >= nextMillis) {
                taskDao.updateTask(task.copy(status = TaskStatus.TODO))
            }
        }
    }

    // Consommer l'événement Waou (pour ne pas le déclencher plusieurs fois)
    fun consumeWaouEvent() { _waouEvent.value = null }

    // Déclencher une notification de retard (overdue) pour une tâche
    fun triggerOverdueNotification(task: Task) { _overdueEvent.value = task }

    // Consommer l'événement de notification de retard
    fun consumeOverdueEvent() { _overdueEvent.value = null }

    fun deleteTaskManually(task: Task) {
        viewModelScope.launch { taskDao.deleteTask(task) }
    }

    // Mettre à jour une tâche (statut, etc.)
    fun updateTask(task: Task) {
        viewModelScope.launch { taskDao.updateTask(task) }
    }

    // Vider toutes les tâches de la base de données
    fun clearAllTasks() {
        viewModelScope.launch { taskDao.deleteAllTasks() }
    }
}