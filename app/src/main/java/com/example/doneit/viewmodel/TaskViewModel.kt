package com.example.doneit.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doneit.data.Task
import com.example.doneit.data.TaskDao
import com.example.doneit.data.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class TaskViewModel(private val taskDao: TaskDao) : ViewModel() {

    // Lister toutes les tâches (Observé par l'UI)
    val allTasks: Flow<List<Task>> = taskDao.getAllTask()

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

            // C'est ici que tu déclencheras l'effet Waou (vibreur/son) plus tard
            effetWaou(updatedTask.status)
        }
    }

    // Logique de l'effet Waou (à compléter avec le vibreur/son)
    private fun effetWaou(status: TaskStatus) {
        if (status == TaskStatus.DONE) {
            println("DÉCLENCHER VIBREUR ET CONFETTIS !")
        }
    }

    fun deleteTaskManually(task: Task) {
        viewModelScope.launch {
            taskDao.deleteTask(task)
        }
    }
}