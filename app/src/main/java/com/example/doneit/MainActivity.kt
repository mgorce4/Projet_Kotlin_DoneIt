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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
        .setContentTitle("⚠️ Tâche en retard !")
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
        tasks.value.filter { it.status == TaskStatus.TODO && it.dateLimite != null && it.heureLimite != null }
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
                        onEdit = {
                            navController.navigate("editTaskForm/${task.id}")
                        }
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
                            // Décoche la tâche : repasse à TODO ou OVERDUE selon date
                            val dateTime = task.dateLimite + " " + task.heureLimite
                            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            val endMillis = try { formatter.parse(dateTime)?.time ?: 0L } catch (_: Exception) { 0L }
                            val newStatus = if (endMillis > 0 && endMillis < System.currentTimeMillis()) TaskStatus.OVERDUE else TaskStatus.TODO
                            val updatedTask = task.copy(status = newStatus)
                            taskViewModel.updateTask(updatedTask)
                        }
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

@Composable
fun TaskCard(task: Task, color: Color, onCheck: () -> Unit, onEdit: (() -> Unit)? = null) {
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
                Text(
                    text = "Date limite : ${task.dateLimite ?: "dd/mm/yyyy"} ${task.heureLimite ?: "00h00"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

@Composable
fun DoneTaskCard(task: Task, color: Color, onDelete: () -> Unit) {
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
                Text(
                    text = "Date limite : ${task.dateLimite ?: "dd/mm/yyyy"} ${task.heureLimite ?: "00h00"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onDelete() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Affiche une checkbox cochée (X) et permet de décocher
                    Text("X", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyLarge)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskFormScreen(navController: NavHostController, taskViewModel: TaskViewModel) {
    var titre by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dateLimite by remember { mutableStateOf("") }
    var heureLimite by remember { mutableStateOf("") }
    val context = LocalContext.current

    val calendar = remember { Calendar.getInstance() }

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
                    .clickable { navController.navigate("home") }
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
        Spacer(modifier = Modifier.height(24.dp))

        // Formulaire
        Text(text = "Titre", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = titre,
            onValueChange = { titre = it },
            placeholder = { Text("Titre ...") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Description", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            placeholder = { Text("Description ...") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Fin de la tâche", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Button(
                onClick = {
                    val dateListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                        val m = month + 1
                        dateLimite = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, m, dayOfMonth)
                    }
                    DatePickerDialog(
                        context,
                        dateListener,
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(
                    text = if (dateLimite.isNotEmpty()) dateLimite else "Choisir la date",
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val timeListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
                        heureLimite = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                    }
                    TimePickerDialog(
                        context,
                        timeListener,
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        true
                    ).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(
                    text = if (heureLimite.isNotEmpty()) heureLimite else "Choisir l'heure",
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                val task = Task(
                    titre = titre,
                    description = description,
                    dateLimite = if (dateLimite.isNotEmpty()) dateLimite else null,
                    heureLimite = if (heureLimite.isNotEmpty()) heureLimite else null,
                    priorite = null,
                    photoUrl = null,
                    xpReward = null,
                    status = TaskStatus.TODO,
                    periodicity = PeriodicityType.NONE
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
    var dateLimite by remember { mutableStateOf(taskToEdit?.dateLimite ?: "") }
    var heureLimite by remember { mutableStateOf(taskToEdit?.heureLimite ?: "") }
    val context = LocalContext.current

    val calendar = remember { Calendar.getInstance() }

    LaunchedEffect(taskToEdit) {
        if (taskToEdit != null) {
            titre = taskToEdit.titre
            description = taskToEdit.description ?: ""
            dateLimite = taskToEdit.dateLimite ?: ""
            heureLimite = taskToEdit.heureLimite ?: ""
        }
    }

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
                    .clickable { navController.navigate("home") }
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
        Spacer(modifier = Modifier.height(24.dp))

        // Formulaire
        Text(text = "Titre", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = titre,
            onValueChange = { titre = it },
            placeholder = { Text("Titre ...") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Description", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            placeholder = { Text("Description ...") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Fin de la tâche", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Button(
                onClick = {
                    val dateListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                        val m = month + 1
                        dateLimite = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, m, dayOfMonth)
                    }
                    DatePickerDialog(
                        context,
                        dateListener,
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(
                    text = if (dateLimite.isNotEmpty()) dateLimite else "Choisir la date",
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val timeListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
                        heureLimite = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                    }
                    TimePickerDialog(
                        context,
                        timeListener,
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        true
                    ).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(
                    text = if (heureLimite.isNotEmpty()) heureLimite else "Choisir l'heure",
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                if (taskToEdit != null) {
                    val updatedTask = taskToEdit.copy(
                        titre = titre,
                        description = description,
                        dateLimite = if (dateLimite.isNotEmpty()) dateLimite else null,
                        heureLimite = if (heureLimite.isNotEmpty()) heureLimite else null
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