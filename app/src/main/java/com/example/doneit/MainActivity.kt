package com.example.doneit

import android.Manifest
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.doneit.data.AppDatabase
import com.example.doneit.viewmodel.TaskViewModel
import com.example.doneit.ui.theme.DoneItTheme
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.doneit.data.TaskStatus
import com.example.doneit.data.Task
import com.example.doneit.data.PeriodicityType
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar
import java.util.Locale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class TaskViewModelFactory(private val taskDao: com.example.doneit.data.TaskDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(taskDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

const val NOTIF_CHANNEL_ID = "doneit_overdue_channel"
const val NOTIF_CHANNEL_NAME = "Tâches en retard"

fun createNotificationChannel(context: Context) {
    val channel = NotificationChannel(
        NOTIF_CHANNEL_ID,
        NOTIF_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Alertes pour les tâches en retard"
    }
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(channel)
}

fun sendOverdueNotification(context: Context, task: Task) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notif = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("Tâche en retard !")
        .setContentText("Votre tâche \"${task.titre}\" est dépassée.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    manager.notify(task.id.toInt(), notif)
}

fun vibrateDevice(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 400), -1)
        )
    } else {
        @Suppress("DEPRECATION")
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        @Suppress("DEPRECATION")
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 400), -1))
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel(this)

        // Initialize Room database with proper configuration
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "doneit-db"
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

        enableEdgeToEdge()
        setContent {
            val taskViewModel: TaskViewModel = viewModel(
                factory = TaskViewModelFactory(database.taskDao())
            )
            // Demande permission notification (Android 13+)
            val notifPermLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {}
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            DoneItTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(
                        modifier = Modifier.padding(innerPadding),
                        taskViewModel = taskViewModel
                    )
                }
            }
        }
    }
}

// ── Confetti Waou Overlay ──────────────────────────────────────────────────

private val confettiColors = listOf(
    Color(0xFFFFD700), Color(0xFFFF6B6B), Color(0xFF4ECDC4),
    Color(0xFF45B7D1), Color(0xFF96CEB4), Color(0xFFFECA57),
    Color(0xFFFF9FF3), Color(0xFF54A0FF)
)

data class ConfettiPiece(
    val x: Float, val y: Float,
    val color: Color, val angle: Float,
    val speed: Float, val size: Float
)

@Composable
fun WaouOverlay(onDismiss: () -> Unit) {
    val pieces = remember {
        List(80) {
            ConfettiPiece(
                x = Random.nextFloat(),
                y = Random.nextFloat() * -0.3f,
                color = confettiColors.random(),
                angle = Random.nextFloat() * 360f,
                speed = 0.3f + Random.nextFloat() * 0.7f,
                size = 8f + Random.nextFloat() * 14f
            )
        }
    }
    val progress = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(2200, easing = LinearEasing))
        alpha.animateTo(0f, animationSpec = tween(400))
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = false, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha.value)
        ) {
            pieces.forEach { p ->
                val cx = p.x * size.width
                val baseY = p.y * size.height
                val cy = baseY + progress.value * size.height * 1.4f * p.speed
                val rot = p.angle + progress.value * 360f * p.speed
                val rad = Math.toRadians(rot.toDouble())
                val dx = (cos(rad) * p.size).toFloat()
                val dy = (sin(rad) * p.size).toFloat()
                drawLine(
                    color = p.color,
                    start = androidx.compose.ui.geometry.Offset(cx - dx, cy - dy),
                    end = androidx.compose.ui.geometry.Offset(cx + dx, cy + dy),
                    strokeWidth = p.size * 0.5f
                )
            }
        }
        // Message central
        Box(
            modifier = Modifier
                .alpha(if (progress.value < 0.7f) 1f else (1f - (progress.value - 0.7f) / 0.3f))
                .background(Color(0xCC000000), RoundedCornerShape(16.dp))
                .padding(horizontal = 32.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🎉 Bravo ! Tâche accomplie !",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── AppNavigation ──────────────────────────────────────────────────────────

@Composable
fun AppNavigation(modifier: Modifier = Modifier, taskViewModel: TaskViewModel) {
    val navController = rememberNavController()
    val tasks = taskViewModel.allTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    NavHost(navController = navController, startDestination = "loading", modifier = modifier) {
        composable("loading"){
            LoadingScreen(navController = navController)
        }
        composable("home") {
            HomeScreen(navController = navController, taskViewModel = taskViewModel)
        }
        composable("addTaskForm") {
            AddTaskFormScreen(navController = navController, taskViewModel = taskViewModel)
        }
        composable("profile") {
            ProfileScreen(navController = navController, taskViewModel = taskViewModel)
        }
        //  passage de l'id de la tâche
        composable("editTaskForm/{taskId}") { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId")?.toLongOrNull()
            val editTask = tasks.value.find { it.id == taskId }
            EditTaskFormScreen(navController = navController, taskViewModel = taskViewModel, taskToEdit = editTask)
        }
    }
}

@Composable
fun HomeScreen(navController: NavHostController, taskViewModel: TaskViewModel) {
    val tasks = taskViewModel.allTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    var todoFilter by remember { mutableStateOf("Aucun") }
    var doneFilter by remember { mutableStateOf("Aucun") }
    var expandedTodoFilter by remember { mutableStateOf(false) }
    var expandedDoneFilter by remember { mutableStateOf(false) }
    var showWaou by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Observe l'événement Waou → vibration + confettis
    val waouEvent by taskViewModel.waouEvent.collectAsStateWithLifecycle()
    LaunchedEffect(waouEvent) {
        if (waouEvent != null) {
            vibrateDevice(context)
            showWaou = true
            taskViewModel.consumeWaouEvent()
        }
    }

    // Observe les événements overdue → notification système
    val overdueEvent by taskViewModel.overdueEvent.collectAsStateWithLifecycle()
    LaunchedEffect(overdueEvent) {
        if (overdueEvent != null) {
            sendOverdueNotification(context, overdueEvent!!)
            taskViewModel.consumeOverdueEvent()
        }
    }

    // Alerte notif pour les tâches qui passent overdue
    LaunchedEffect(tasks.value) {
        tasks.value.filter { it.status == TaskStatus.TODO && it.dateLimite != null && it.heureLimite != null && it.periodicity == PeriodicityType.NONE }
            .forEach { task ->
                val dateTime = task.dateLimite + " " + task.heureLimite
                val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val endMillis = try { formatter.parse(dateTime)?.time ?: 0L } catch (_: Exception) { 0L }
                if (endMillis > 0 && endMillis < System.currentTimeMillis()) {
                    val updatedTask = task.copy(status = TaskStatus.OVERDUE)
                    taskViewModel.updateTask(updatedTask)
                    taskViewModel.triggerOverdueNotification(updatedTask)
                }
            }
        // Reset les tâches périodiques DONE dont la prochaine occurrence est atteinte
        tasks.value.filter { it.periodicity != PeriodicityType.NONE && it.status == TaskStatus.DONE }
            .forEach { task -> taskViewModel.resetPeriodicTaskIfDue(task) }
    }

    // Filtres pour tâches à effectuer
    val todoFilterOptions = listOf(
        "Trier ?", "Date croissante", "Date décroissante", "En retard", "A faire"
    )
    // Filtres pour tâches effectuées
    val doneFilterOptions = listOf(
        "Trier ?", "Date croissante", "Date décroissante"
    )

    // Application des filtres
    val filteredTasksToDo: List<Task> = when (todoFilter) {
        "Date croissante" -> tasks.value.filter { it.status == TaskStatus.TODO || it.status == TaskStatus.OVERDUE }
            .sortedBy { it.dateLimite + it.heureLimite }
        "Date décroissante" -> tasks.value.filter { it.status == TaskStatus.TODO || it.status == TaskStatus.OVERDUE }
            .sortedByDescending { it.dateLimite + it.heureLimite }
        "En retard" -> tasks.value.filter { it.status == TaskStatus.OVERDUE }
        "A faire" -> tasks.value.filter { it.status == TaskStatus.TODO }
        else -> tasks.value.filter { it.status == TaskStatus.TODO || it.status == TaskStatus.OVERDUE }
    }
    val filteredDoneTasks = when (doneFilter) {
        "Date croissante" -> tasks.value.filter { it.status == TaskStatus.DONE }.sortedBy { it.dateLimite + it.heureLimite }
        "Date décroissante" -> tasks.value.filter { it.status == TaskStatus.DONE }.sortedByDescending { it.dateLimite + it.heureLimite }
        else -> tasks.value.filter { it.status == TaskStatus.DONE }
    }

    var expandedDone by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(10.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "DoneIt!",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.displayMedium
                )
            }
            IconButton(
                onClick = { navController.navigate("profile") },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Section Tâches à effectuer
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tâches à effectuer",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Box {
                Text(
                    text = todoFilter.takeIf { it != "Aucun" && it != "Trier ?" } ?: "Trier ?",
                    modifier = Modifier
                        .clickable { expandedTodoFilter = true }
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                DropdownMenu(
                    expanded = expandedTodoFilter,
                    onDismissRequest = { expandedTodoFilter = false }
                ) {
                    todoFilterOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                todoFilter = option
                                expandedTodoFilter = false
                            }
                        )
                    }
                }
            }
            IconButton(onClick = { navController.navigate("addTaskForm") }) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (filteredTasksToDo.isEmpty()) {
            Text(
                text = "Aucune tâche à effectuer",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Column {
                filteredTasksToDo.forEach { task ->
                    TaskCard(
                        task = task,
                        color = if (task.status == TaskStatus.OVERDUE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        onCheck = { taskViewModel.completeTaskWithReward(task) },
                        onEdit = { navController.navigate("editTaskForm/${task.id}") },
                        onDelete = { taskViewModel.deleteTaskManually(task) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Section Tâches effectuées
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Tâches effectuées",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Box {
                Text(
                    text = doneFilter.takeIf { it != "Aucun" && it != "Trier ?" } ?: "Trier ?",
                    modifier = Modifier
                        .clickable { expandedDoneFilter = true }
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                DropdownMenu(
                    expanded = expandedDoneFilter,
                    onDismissRequest = { expandedDoneFilter = false }
                ) {
                    doneFilterOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                doneFilter = option
                                expandedDoneFilter = false
                            }
                        )
                    }
                }
            }
            IconButton(onClick = { expandedDone = !expandedDone }) {
                Icon(
                    if (expandedDone) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expandedDone) "Réduire" else "Déplier",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (filteredDoneTasks.isEmpty()) {
            Text(
                text = "Aucune tâche effectuée",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (expandedDone) {
            Column {
                filteredDoneTasks.forEach { task ->
                    DoneTaskCard(
                        task = task,
                        color = MaterialTheme.colorScheme.surface,
                        onDelete = {
                            val dateTime = task.dateLimite + " " + task.heureLimite
                            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            val endMillis = try { formatter.parse(dateTime)?.time ?: 0L } catch (_: Exception) { 0L }
                            val newStatus = if (endMillis > 0 && endMillis < System.currentTimeMillis()) TaskStatus.OVERDUE else TaskStatus.TODO
                            taskViewModel.updateTask(task.copy(status = newStatus))
                        },
                        onHardDelete = { taskViewModel.deleteTaskManually(task) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        }  // fin Column
        // Overlay confettis "Effet Waou"
        if (showWaou) {
            WaouOverlay(onDismiss = { showWaou = false })
        }
    } // fin Box
}

// ── Dialogue de confirmation suppression ────────────────────────────────────
@Composable
fun DeleteConfirmDialog(
    taskTitle: String,
    onConfirm: (neverAskAgain: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var neverAskAgain by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supprimer la tâche ?") },
        text = {
            Column {
                Text("Voulez-vous vraiment supprimer \"$taskTitle\" ?")
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = neverAskAgain,
                        onCheckedChange = { neverAskAgain = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ne plus demander", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(neverAskAgain) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Supprimer") }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) { Text("Annuler") }
        }
    )
}

// ── TaskCard ─────────────────────────────────────────────────────────────────
@Composable
fun TaskCard(
    task: Task,
    color: Color,
    onCheck: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("doneit_prefs", Context.MODE_PRIVATE) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        DeleteConfirmDialog(
            taskTitle = task.titre,
            onConfirm = { neverAskAgain ->
                if (neverAskAgain) {
                    prefs.edit().putBoolean("skip_delete_confirm", true).apply()
                }
                showDialog = false
                onDelete?.invoke()
            },
            onDismiss = { showDialog = false }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.titre,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = task.description ?: "Description...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
                if (task.periodicity != PeriodicityType.NONE) {
                    val perioLabel = when (task.periodicity) {
                        PeriodicityType.DAILY -> "Quotidienne"
                        PeriodicityType.WEEKLY -> "Hebdomadaire"
                        PeriodicityType.MONTHLY -> "Mensuelle"
                        else -> ""
                    }
                    val nextDate = task.nextOccurrence
                        ?: if (task.dateLimite != null && task.heureLimite != null) "${task.dateLimite} ${task.heureLimite}" else null
                    Text(
                        text = "$perioLabel — À faire pour le : ${nextDate ?: "Non défini"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                } else {
                    Text(
                        text = "Date limite : ${task.dateLimite ?: "dd/mm/yyyy"} ${task.heureLimite ?: "00h00"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    if (onEdit != null) {
                        IconButton(
                            onClick = { onEdit() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Modifier",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    if (onDelete != null) {
                        IconButton(
                            onClick = {
                                val skip = prefs.getBoolean("skip_delete_confirm", false)
                                if (skip) onDelete() else showDialog = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Supprimer",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { onCheck() }
                ) {
                    // Checkbox non cochée
                }
            }
        }
    }
}

// ── DoneTaskCard ──────────────────────────────────────────────────────────────
@Composable
fun DoneTaskCard(task: Task, color: Color, onDelete: () -> Unit, onHardDelete: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("doneit_prefs", Context.MODE_PRIVATE) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        DeleteConfirmDialog(
            taskTitle = task.titre,
            onConfirm = { neverAskAgain ->
                if (neverAskAgain) {
                    prefs.edit().putBoolean("skip_delete_confirm", true).apply()
                }
                showDialog = false
                onHardDelete()
            },
            onDismiss = { showDialog = false }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.titre,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = task.description ?: "Description...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                if (task.periodicity != PeriodicityType.NONE) {
                    val perioLabel = when (task.periodicity) {
                        PeriodicityType.DAILY -> "Quotidienne"
                        PeriodicityType.WEEKLY -> "Hebdomadaire"
                        PeriodicityType.MONTHLY -> "Mensuelle"
                        else -> ""
                    }
                    val nextDate = task.nextOccurrence
                        ?: if (task.dateLimite != null && task.heureLimite != null) "${task.dateLimite} ${task.heureLimite}" else null
                    Text(
                        text = "$perioLabel — Prochaine : ${nextDate ?: "Non défini"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                } else {
                    Text(
                        text = "Date limite : ${task.dateLimite ?: "dd/mm/yyyy"} ${task.heureLimite ?: "00h00"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    // Icône poubelle pour suppression définitive
                    IconButton(
                        onClick = {
                            val skip = prefs.getBoolean("skip_delete_confirm", false)
                            if (skip) onHardDelete() else showDialog = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Supprimer définitivement",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                // Checkbox cochée pour décocher
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { onDelete() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("X", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingScreen(navController: NavHostController) {
    // Pour la navigation après 10 secondes
    val alreadyNavigated = remember { mutableStateOf(false) }

    // Lance le timer une seule fois
    LaunchedEffect(Unit) {
        delay(10_000)
        if (!alreadyNavigated.value) {
            alreadyNavigated.value = true
            navController.navigate("home") {
                popUpTo("loading") { inclusive = true }
            }
        }
    }

    // Gestion du clic pour passer à l'écran suivant
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = !alreadyNavigated.value) {
                if (!alreadyNavigated.value) {
                    alreadyNavigated.value = true
                    navController.navigate("home") {
                        popUpTo("loading") { inclusive = true }
                    }
                }
            }
    ) {

        // logo
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 28.dp, vertical = 26.dp)
        ) {
            Text(
                text = "DoneIt!",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }


    }
}

// ── Section périodicité réutilisable ──────────────────────────────────────

@Composable
fun PeriodicitySection(
    hasDeadline: Boolean,
    onHasDeadlineChange: (Boolean) -> Unit,
    dateLimite: String,
    onDateClick: () -> Unit,
    heureLimite: String,
    onHeureClick: () -> Unit,
    hasPeriodicity: Boolean,
    onHasPeriodicityChange: (Boolean) -> Unit,
    periodicity: PeriodicityType,
    onPeriodicityChange: (PeriodicityType) -> Unit,
    periodicityStartDate: String,
    onPeriodicityDateClick: () -> Unit,
    periodicityStartHeure: String,
    onPeriodicityHeureClick: () -> Unit
) {
    var expandedPerioMenu by remember { mutableStateOf(false) }
    val perioOptions = listOf(
        PeriodicityType.DAILY to "Quotidienne",
        PeriodicityType.WEEKLY to "Hebdomadaire",
        PeriodicityType.MONTHLY to "Mensuelle"
    )

    // ── Checkbox Date limite ──
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = hasDeadline,
            onCheckedChange = {
                onHasDeadlineChange(it)
                if (it) onHasPeriodicityChange(false)
            }
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Date limite", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
    }
    if (hasDeadline) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onDateClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (dateLimite.isNotEmpty()) dateLimite else "Choisir la date",
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Button(
                onClick = onHeureClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (heureLimite.isNotEmpty()) heureLimite else "Choisir l'heure",
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    // ── Checkbox Périodicité ──
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = hasPeriodicity,
            onCheckedChange = {
                onHasPeriodicityChange(it)
                if (it) onHasDeadlineChange(false)
            }
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Tâche périodique", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
    }
    if (hasPeriodicity) {
        // Sélecteur de type de périodicité
        Box {
            val perioLabel = perioOptions.find { it.first == periodicity }?.second ?: "Choisir..."
            Button(
                onClick = { expandedPerioMenu = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("$perioLabel", color = MaterialTheme.colorScheme.onPrimary)
            }
            DropdownMenu(expanded = expandedPerioMenu, onDismissRequest = { expandedPerioMenu = false }) {
                perioOptions.forEach { (type, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { onPeriodicityChange(type); expandedPerioMenu = false }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Première occurrence :",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPeriodicityDateClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (periodicityStartDate.isNotEmpty()) periodicityStartDate else "Choisir la date",
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Button(
                onClick = onPeriodicityHeureClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (periodicityStartHeure.isNotEmpty()) periodicityStartHeure else "Choisir l'heure",
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ── AddTaskFormScreen ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskFormScreen(navController: NavHostController, taskViewModel: TaskViewModel) {
    var titre by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    // Date limite classique
    var hasDeadline by remember { mutableStateOf(false) }
    var dateLimite by remember { mutableStateOf("") }
    var heureLimite by remember { mutableStateOf("") }
    // Périodicité
    var hasPeriodicity by remember { mutableStateOf(false) }
    var periodicity by remember { mutableStateOf(PeriodicityType.WEEKLY) }
    var periodicityStartDate by remember { mutableStateOf("") }
    var periodicityStartHeure by remember { mutableStateOf("") }

    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    val scrollStateAdd = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(scrollStateAdd)
    ) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(10.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .clickable { navController.navigate("home") }
            ) {
                Text("DoneIt!", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.displayMedium)
            }
            IconButton(onClick = { navController.navigate("profile") }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Titre
        Text("Titre", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = titre, onValueChange = { titre = it },
            placeholder = { Text("Titre ...") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Description
        Text("Description", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            placeholder = { Text("Description ...") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Section périodicité
        PeriodicitySection(
            hasDeadline = hasDeadline,
            onHasDeadlineChange = { hasDeadline = it },
            dateLimite = dateLimite,
            onDateClick = {
                DatePickerDialog(context, { _, y, m, d ->
                    dateLimite = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            },
            heureLimite = heureLimite,
            onHeureClick = {
                TimePickerDialog(context, { _, h, min ->
                    heureLimite = String.format(Locale.getDefault(), "%02d:%02d", h, min)
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
            },
            hasPeriodicity = hasPeriodicity,
            onHasPeriodicityChange = { hasPeriodicity = it },
            periodicity = periodicity,
            onPeriodicityChange = { periodicity = it },
            periodicityStartDate = periodicityStartDate,
            onPeriodicityDateClick = {
                DatePickerDialog(context, { _, y, m, d ->
                    periodicityStartDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            },
            periodicityStartHeure = periodicityStartHeure,
            onPeriodicityHeureClick = {
                TimePickerDialog(context, { _, h, min ->
                    periodicityStartHeure = String.format(Locale.getDefault(), "%02d:%02d", h, min)
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
            }
        )

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                val task = Task(
                    titre = titre,
                    description = description.ifBlank { null },
                    dateLimite = if (hasDeadline && dateLimite.isNotEmpty()) dateLimite else null,
                    heureLimite = if (hasDeadline && heureLimite.isNotEmpty()) heureLimite else null,
                    priorite = null, photoUrl = null, xpReward = null,
                    status = TaskStatus.TODO,
                    periodicity = if (hasPeriodicity) periodicity else PeriodicityType.NONE,
                    nextOccurrence = if (hasPeriodicity && periodicityStartDate.isNotEmpty() && periodicityStartHeure.isNotEmpty())
                        "$periodicityStartDate $periodicityStartHeure" else null
                )
                taskViewModel.addTask(task)
                navController.navigate("home") { popUpTo("addTaskForm") { inclusive = true } }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Valider", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun ProfileScreen(navController: NavHostController, taskViewModel: TaskViewModel) {
    val tasks = taskViewModel.allTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val doneCount = tasks.value.count { it.status == TaskStatus.DONE }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header avec logo DoneIt! qui retourne à HomeScreen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(10.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .clickable { navController.navigate("home") }
            ) {
                Text(
                    text = "DoneIt!",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.displayMedium
                )
            }
        }
        Spacer(modifier = Modifier.height(120.dp))
        // Affichage du nombre de tâches effectuées
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Vous avez réalisé $doneCount tâche.s !",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        // Bouton pour vider la base de données
        Button(
            onClick = { taskViewModel.clearAllTasks() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = "Vider toutes les tâches",
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskFormScreen(navController: NavHostController, taskViewModel: TaskViewModel, taskToEdit: Task?) {
    var titre by remember { mutableStateOf(taskToEdit?.titre ?: "") }
    var description by remember { mutableStateOf(taskToEdit?.description ?: "") }
    // Date limite
    var hasDeadline by remember { mutableStateOf(taskToEdit?.periodicity == PeriodicityType.NONE && taskToEdit.dateLimite != null) }
    var dateLimite by remember { mutableStateOf(taskToEdit?.dateLimite ?: "") }
    var heureLimite by remember { mutableStateOf(taskToEdit?.heureLimite ?: "") }
    // Périodicité
    var hasPeriodicity by remember { mutableStateOf(taskToEdit?.periodicity != PeriodicityType.NONE) }
    var periodicity by remember { mutableStateOf(taskToEdit?.periodicity?.takeIf { it != PeriodicityType.NONE } ?: PeriodicityType.WEEKLY) }
    var periodicityStartDate by remember { mutableStateOf(taskToEdit?.nextOccurrence?.substringBefore(" ") ?: taskToEdit?.dateLimite ?: "") }
    var periodicityStartHeure by remember { mutableStateOf(taskToEdit?.nextOccurrence?.substringAfter(" ") ?: taskToEdit?.heureLimite ?: "") }

    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }

    LaunchedEffect(taskToEdit) {
        if (taskToEdit != null) {
            titre = taskToEdit.titre
            description = taskToEdit.description ?: ""
            hasPeriodicity = taskToEdit.periodicity != PeriodicityType.NONE
            hasDeadline = taskToEdit.periodicity == PeriodicityType.NONE && taskToEdit.dateLimite != null
            if (hasPeriodicity) {
                periodicity = taskToEdit.periodicity
                periodicityStartDate = taskToEdit.nextOccurrence?.substringBefore(" ") ?: taskToEdit.dateLimite ?: ""
                periodicityStartHeure = taskToEdit.nextOccurrence?.substringAfter(" ") ?: taskToEdit.heureLimite ?: ""
            } else {
                dateLimite = taskToEdit.dateLimite ?: ""
                heureLimite = taskToEdit.heureLimite ?: ""
            }
        }
    }

    val scrollStateEdit = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(scrollStateEdit)
    ) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(10.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .clickable { navController.navigate("home") }
            ) {
                Text("DoneIt!", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.displayMedium)
            }
            IconButton(onClick = { navController.navigate("profile") }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Titre
        Text("Titre", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = titre, onValueChange = { titre = it },
            placeholder = { Text("Titre ...") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Description
        Text("Description", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            placeholder = { Text("Description ...") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Section périodicité
        PeriodicitySection(
            hasDeadline = hasDeadline,
            onHasDeadlineChange = { hasDeadline = it },
            dateLimite = dateLimite,
            onDateClick = {
                DatePickerDialog(context, { _, y, m, d ->
                    dateLimite = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            },
            heureLimite = heureLimite,
            onHeureClick = {
                TimePickerDialog(context, { _, h, min ->
                    heureLimite = String.format(Locale.getDefault(), "%02d:%02d", h, min)
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
            },
            hasPeriodicity = hasPeriodicity,
            onHasPeriodicityChange = { hasPeriodicity = it },
            periodicity = periodicity,
            onPeriodicityChange = { periodicity = it },
            periodicityStartDate = periodicityStartDate,
            onPeriodicityDateClick = {
                DatePickerDialog(context, { _, y, m, d ->
                    periodicityStartDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            },
            periodicityStartHeure = periodicityStartHeure,
            onPeriodicityHeureClick = {
                TimePickerDialog(context, { _, h, min ->
                    periodicityStartHeure = String.format(Locale.getDefault(), "%02d:%02d", h, min)
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
            }
        )

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                if (taskToEdit != null) {
                    val updatedTask = taskToEdit.copy(
                        titre = titre,
                        description = description.ifBlank { null },
                        dateLimite = if (hasDeadline && dateLimite.isNotEmpty()) dateLimite else null,
                        heureLimite = if (hasDeadline && heureLimite.isNotEmpty()) heureLimite else null,
                        periodicity = if (hasPeriodicity) periodicity else PeriodicityType.NONE,
                        nextOccurrence = if (hasPeriodicity && periodicityStartDate.isNotEmpty() && periodicityStartHeure.isNotEmpty())
                            "$periodicityStartDate $periodicityStartHeure" else null
                    )
                    taskViewModel.updateTask(updatedTask)
                }
                navController.navigate("home") { popUpTo("editTaskForm") { inclusive = true } }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Mettre à jour", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyLarge)
        }
    }
}