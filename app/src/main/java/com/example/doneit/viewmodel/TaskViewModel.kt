package com.example.doneit.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doneit.data.Task
import com.example.doneit.data.TaskDao
import com.example.doneit.data.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
        viewModelScope.launch {
            taskDao.insertTask(task)
        }
    }

    //Marquer comme réalisée avec "Effet Waou"
    fun completeTaskWithReward(task: Task) {
        viewModelScope.launch {
            // On met à jour le statut dans la base
            val updatedTask = task.copy(status = TaskStatus.DONE)
            taskDao.updateTask(updatedTask)
            // Déclenche l'effet waou
            _waouEvent.value = updatedTask
        }
    }

    // Consommer l'événement Waou (pour ne pas le déclencher plusieurs fois)
    fun consumeWaouEvent() {
        _waouEvent.value = null
    }

    // Déclencher une notification de retard (overdue) pour une tâche
    fun triggerOverdueNotification(task: Task) {
        _overdueEvent.value = task
    }

    // Consommer l'événement de notification de retard
    fun consumeOverdueEvent() {
        _overdueEvent.value = null
    }

    fun deleteTaskManually(task: Task) {
        viewModelScope.launch {
            taskDao.deleteTask(task)
        }
    }

    // Mettre à jour une tâche (statut, etc.)
    fun updateTask(task: Task) {
        viewModelScope.launch {
            taskDao.updateTask(task)
        }
    }

    // Vider toutes les tâches de la base de données
    fun clearAllTasks() {
        viewModelScope.launch {
            taskDao.deleteAllTasks()
        }
    }
}