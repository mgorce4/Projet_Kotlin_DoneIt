package com.example.doneit.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doneit.data.PeriodicityType
import com.example.doneit.data.Task
import com.example.doneit.data.TaskDao
import com.example.doneit.data.TaskStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

    init {
        // Polling toutes les 60 secondes pour reset tâches périodiques et check overdue
        viewModelScope.launch {
            while (true) {
                checkAndUpdatePeriodicTasks()
                delay(60_000L)
            }
        }
    }

    /**
     * Vérifie toutes les tâches périodiques :
     * - DONE dont nextOccurrence est atteinte → repassent à TODO
     * - TODO dont l'heure de fin de la période courante est dépassée → passent à OVERDUE
     * Appelé aussi depuis l'UI au chargement initial.
     */
    suspend fun checkAndUpdatePeriodicTasks() {
        val tasks = allTasks.first()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val now = System.currentTimeMillis()

        // 1. Reset tâches périodiques DONE → TODO si minuit de la nouvelle période est passé
        tasks.filter { it.periodicity != PeriodicityType.NONE && it.status == TaskStatus.DONE }
            .forEach { task ->
                val resetMillis = try {
                    task.nextOccurrence?.let { fmt.parse(it)?.time } ?: return@forEach
                } catch (_: Exception) { return@forEach }
                if (now >= resetMillis) {
                    taskDao.updateTask(task.copy(status = TaskStatus.TODO))
                }
            }

        // 2. Tâches non-périodiques TODO → OVERDUE si date/heure dépassée
        tasks.filter { it.status == TaskStatus.TODO && it.periodicity == PeriodicityType.NONE && it.dateLimite != null && it.heureLimite != null }
            .forEach { task ->
                val endMillis = try { fmt.parse("${task.dateLimite} ${task.heureLimite}")?.time ?: 0L } catch (_: Exception) { 0L }
                if (endMillis > 0 && endMillis < now) {
                    val updated = task.copy(status = TaskStatus.OVERDUE)
                    taskDao.updateTask(updated)
                    _overdueEvent.value = updated
                }
            }

        // 3. Tâches périodiques TODO → OVERDUE si l'heure de fin de la période courante est dépassée
        tasks.filter { it.status == TaskStatus.TODO && it.periodicity != PeriodicityType.NONE && it.dateLimite != null && it.heureLimite != null }
            .forEach { task ->
                val limitMillis = computeCurrentPeriodDeadline(task)
                if (limitMillis > 0 && limitMillis < now) {
                    val updated = task.copy(status = TaskStatus.OVERDUE)
                    taskDao.updateTask(updated)
                    _overdueEvent.value = updated
                }
            }
    }

    /**
     * Calcule le timestamp de l'heure limite dans la PÉRIODE COURANTE pour une tâche périodique.
     * Ex : quotidienne à 10h → aujourd'hui à 10:00
     *      hebdomadaire lundi à 10h → ce lundi à 10:00
     *      mensuelle le 5 à 10h → ce mois-ci le 5 à 10:00
     */
    private fun computeCurrentPeriodDeadline(task: Task): Long {
        if (task.dateLimite == null || task.heureLimite == null) return 0L
        val refFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val refDate = try { refFmt.parse(task.dateLimite) } catch (_: Exception) { return 0L } ?: return 0L
        val refCal = Calendar.getInstance().apply { time = refDate }
        val h = task.heureLimite.substringBefore(":").toIntOrNull() ?: 0
        val m = task.heureLimite.substringAfter(":").toIntOrNull() ?: 0
        val cal = Calendar.getInstance()
        when (task.periodicity) {
            PeriodicityType.DAILY -> {
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, m)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            PeriodicityType.WEEKLY -> {
                val targetDow = refCal.get(Calendar.DAY_OF_WEEK)
                val diff = targetDow - cal.get(Calendar.DAY_OF_WEEK)
                cal.add(Calendar.DAY_OF_YEAR, diff)
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, m)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            PeriodicityType.MONTHLY -> {
                val targetDay = refCal.get(Calendar.DAY_OF_MONTH)
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, minOf(targetDay, maxDay))
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, m)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            PeriodicityType.NONE -> return 0L
        }
        return cal.timeInMillis
    }

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

    /**
     * Calcule le début de la prochaine PÉRIODE calendaire à partir du moment où la tâche est cochée,
     * en ignorant l'heure de fin de la tâche :
     *  - DAILY   → minuit (00:00) du lendemain
     *  - WEEKLY  → lundi à 00:00 de la semaine suivante
     *  - MONTHLY → 1er du mois suivant à 00:00
     * nextOccurrence stocke ce seuil de réactivation (pas l'heure de fin).
     */
    fun computeNextOccurrence(task: Task): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val now = Calendar.getInstance()
        val next = Calendar.getInstance()
        when (task.periodicity) {
            PeriodicityType.DAILY -> {
                // Minuit du lendemain
                next.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                next.set(Calendar.MILLISECOND, 0)
                next.add(Calendar.DAY_OF_YEAR, 1)
            }
            PeriodicityType.WEEKLY -> {
                // Lundi à 00:00 de la semaine suivante
                next.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                next.set(Calendar.MILLISECOND, 0)
                // Aller au lundi de cette semaine (Calendar.MONDAY = 2, Calendar.SUNDAY = 1)
                val dow = next.get(Calendar.DAY_OF_WEEK)
                val daysToLastMonday = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
                next.add(Calendar.DAY_OF_YEAR, -daysToLastMonday)
                // +7 jours = lundi de la semaine suivante
                next.add(Calendar.WEEK_OF_YEAR, 1)
            }
            PeriodicityType.MONTHLY -> {
                // 1er du mois suivant à 00:00
                next.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), 1, 0, 0, 0)
                next.set(Calendar.MILLISECOND, 0)
                next.add(Calendar.MONTH, 1)
            }
            PeriodicityType.NONE -> {}
        }
        return fmt.format(next.time)
    }

    /**
     * Remet une tâche périodique DONE à TODO dès que le seuil de réactivation
     * (= minuit du début de la nouvelle période) est atteint.
     */
    fun resetPeriodicTaskIfDue(task: Task) {
        if (task.periodicity == PeriodicityType.NONE || task.status != TaskStatus.DONE) return
        viewModelScope.launch {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val resetMillis = try {
                task.nextOccurrence?.let { fmt.parse(it)?.time } ?: return@launch
            } catch (_: Exception) { return@launch }
            if (System.currentTimeMillis() >= resetMillis) {
                // Remettre à TODO ; le check overdue dans l'UI s'occupera du statut si la date est dépassée
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