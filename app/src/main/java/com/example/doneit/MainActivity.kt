package com.example.doneit

import android.Manifest
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import coil.compose.AsyncImage
import com.example.doneit.data.AppDatabase
import com.example.doneit.viewmodel.TaskViewModel
import com.example.doneit.viewmodel.Rank
import com.example.doneit.viewmodel.RANKS
import com.example.doneit.viewmodel.getRankForCount
import com.example.doneit.viewmodel.getNextRank
import com.example.doneit.viewmodel.getRankProgress
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Copie une image depuis une URI externe vers le stockage interne de l'app et retourne le chemin absolu */
fun copyImageToInternal(context: Context, sourceUri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "task_photos").also { it.mkdirs() }
        val dest = File(dir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.absolutePath
    } catch (e: Exception) {
        null
    }
}

/** Crée un fichier temporaire pour la photo caméra et retourne son URI FileProvider */
fun createCameraImageUri(context: Context): Uri {
    val dir = File(context.externalCacheDir, "camera_photos").also { it.mkdirs() }
    val file = File(dir, "camera_${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

class TaskViewModelFactory(
    private val taskDao: com.example.doneit.data.TaskDao,
    private val prefs: android.content.SharedPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(taskDao, prefs) as T
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
            val doneitPrefs = getSharedPreferences("doneit_prefs", Context.MODE_PRIVATE)
            val taskViewModel: TaskViewModel = viewModel(
                factory = TaskViewModelFactory(database.taskDao(), doneitPrefs)
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

// ── RankUp Overlay ──────────────────────────────────────────────────────────

@Composable
fun RankUpOverlay(rank: Rank, onDismiss: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.4f) }

    LaunchedEffect(Unit) {
        // Apparition
        alpha.animateTo(1f, animationSpec = tween(400))
        scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        // Attendre
        delay(2800)
        // Disparition
        alpha.animateTo(0f, animationSpec = tween(400))
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable { /* bloquer le clic en dessous */ },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .alpha(alpha.value)
                .graphicsLayer(scaleX = scale.value, scaleY = scale.value)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(24.dp))
                .padding(horizontal = 40.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = rank.emoji,
                    fontSize = 64.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Nouveau rang !",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = rank.name,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dès ${rank.threshold} tâches accomplies",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
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
        // Détail plein écran d'une tâche
        composable("taskDetail/{taskId}") { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId")?.toLongOrNull()
            val task = tasks.value.find { it.id == taskId }
            TaskDetailScreen(navController = navController, task = task)
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
    var rankUpRank by remember { mutableStateOf<Rank?>(null) }

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

    // Observe le rang-up event
    val rankUpEvent by taskViewModel.rankUpEvent.collectAsStateWithLifecycle()
    LaunchedEffect(rankUpEvent) {
        if (rankUpEvent != null) {
            rankUpRank = rankUpEvent
            taskViewModel.consumeRankUpEvent()
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

    // Au chargement de l'écran, déclencher immédiatement un check complet
    // (le ViewModel fait ensuite un polling toutes les 60s en autonomie)
    LaunchedEffect(Unit) {
        taskViewModel.checkAndUpdatePeriodicTasks()
    }

    // Filtres pour tâches à effectuer
    val todoFilterOptions = listOf(
        "Trier ?", "Date croissante", "Date décroissante", "En retard", "A faire",
        "Priorité ↓", "Priorité ↑"
    )
    // Filtres pour tâches effectuées
    val doneFilterOptions = listOf(
        "Trier ?", "Date croissante", "Date décroissante",
        "Priorité ↓", "Priorité ↑"
    )

    // Application des filtres
    val filteredTasksToDo: List<Task> = when (todoFilter) {
        "Date croissante" -> tasks.value.filter { it.status == TaskStatus.TODO || it.status == TaskStatus.OVERDUE }
            .sortedBy { it.dateLimite + it.heureLimite }
        "Date décroissante" -> tasks.value.filter { it.status == TaskStatus.TODO || it.status == TaskStatus.OVERDUE }
            .sortedByDescending { it.dateLimite + it.heureLimite }
        "En retard" -> tasks.value.filter { it.status == TaskStatus.OVERDUE }
        "A faire" -> tasks.value.filter { it.status == TaskStatus.TODO }
        "Priorité ↓" -> tasks.value.filter { it.status == TaskStatus.TODO || it.status == TaskStatus.OVERDUE }
            .sortedByDescending { it.priorite ?: 0 }
        "Priorité ↑" -> tasks.value.filter { it.status == TaskStatus.TODO || it.status == TaskStatus.OVERDUE }
            .sortedBy { it.priorite ?: Int.MAX_VALUE }
        else -> tasks.value.filter { it.status == TaskStatus.TODO || it.status == TaskStatus.OVERDUE }
    }
    val filteredDoneTasks = when (doneFilter) {
        "Date croissante" -> tasks.value.filter { it.status == TaskStatus.DONE }.sortedBy { it.dateLimite + it.heureLimite }
        "Date décroissante" -> tasks.value.filter { it.status == TaskStatus.DONE }.sortedByDescending { it.dateLimite + it.heureLimite }
        "Priorité ↓" -> tasks.value.filter { it.status == TaskStatus.DONE }.sortedByDescending { it.priorite ?: 0 }
        "Priorité ↑" -> tasks.value.filter { it.status == TaskStatus.DONE }.sortedBy { it.priorite ?: Int.MAX_VALUE }
        else -> tasks.value.filter { it.status == TaskStatus.DONE }
    }

    var expandedDone by remember { mutableStateOf(true) }
    val totalDone by taskViewModel.totalTasksDone.collectAsStateWithLifecycle()
    val currentRank = getRankForCount(totalDone)
    val nextRank = getNextRank(totalDone)
    val rankProgress = getRankProgress(totalDone)
    val animatedRankProgress = remember { Animatable(0f) }
    LaunchedEffect(rankProgress) {
        animatedRankProgress.animateTo(rankProgress, animationSpec = tween(700, easing = FastOutSlowInEasing))
    }

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
        Spacer(modifier = Modifier.height(12.dp))

        // ── Jauge de rang ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Jauge
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(5.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedRankProgress.value)
                        .height(10.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp))
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            // Trophée cliquable → page profil
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .clickable { navController.navigate("profile") },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentRank.emoji,
                    fontSize = 20.sp
                )
            }
        }
        // Texte sous la jauge : rang actuel + prochain palier
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = currentRank.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (nextRank != null) {
                Text(
                    text = "${nextRank.threshold - totalDone} tâche${if (nextRank.threshold - totalDone > 1) "s" else ""} → ${nextRank.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                Text(
                    text = "Rang max 👑",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        // ─────────────────────────────────────────────────────────────
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
                        onDelete = { taskViewModel.deleteTaskManually(task) },
                        onClick = { navController.navigate("taskDetail/${task.id}") }
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
                        onHardDelete = { taskViewModel.deleteTaskManually(task) },
                        onClick = { navController.navigate("taskDetail/${task.id}") }
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
        // Overlay montée de rang
        rankUpRank?.let { rank ->
            RankUpOverlay(rank = rank, onDismiss = { rankUpRank = null })
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
    onDelete: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
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
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // ── Colonne gauche : titre, description, date, photo ──
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
                if (task.photoUrl != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = File(task.photoUrl),
                        contentDescription = "Photo de la tâche",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            // ── Colonne droite : [priorité + stylo + poubelle] puis checkbox ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Ligne 1 : priorité + stylo + poubelle alignés horizontalement
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.priorite != null) {
                        Text(
                            text = priorityEmoji(task.priorite),
                            fontSize = 18.sp,
                            modifier = Modifier.padding(end = 2.dp)
                        )
                    }
                    if (onEdit != null) {
                        IconButton(onClick = { onEdit() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = "Modifier", tint = MaterialTheme.colorScheme.onPrimary)
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
                            Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                // Ligne 2 : checkbox
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp).clickable { onCheck() }
                ) { /* Checkbox non cochée */ }
            }
        }
    }
}

// ── DoneTaskCard ──────────────────────────────────────────────────────────────
@Composable
fun DoneTaskCard(task: Task, color: Color, onDelete: () -> Unit, onHardDelete: () -> Unit, onClick: (() -> Unit)? = null) {
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
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // ── Colonne gauche : titre, description, date, photo ──
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
                // Photo en bas à gauche
                if (task.photoUrl != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = File(task.photoUrl),
                        contentDescription = "Photo de la tâche",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            // ── Colonne droite : [priorité + poubelle] puis checkbox ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Ligne 1 : priorité + poubelle alignés horizontalement
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.priorite != null) {
                        Text(
                            text = priorityEmoji(task.priorite),
                            fontSize = 18.sp,
                            modifier = Modifier.padding(end = 2.dp)
                        )
                    }
                    // Icône poubelle pour suppression définitive
                    IconButton(
                        onClick = {
                            val skip = prefs.getBoolean("skip_delete_confirm", false)
                            if (skip) onHardDelete() else showDialog = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Supprimer définitivement", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                // Ligne 2 : checkbox cochée
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(36.dp).clickable { onDelete() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("X", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

// ── TaskDetailScreen ──────────────────────────────────────────────────────────
@Composable
fun TaskDetailScreen(navController: NavHostController, task: Task?) {
    if (task == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    val statusLabel = when (task.status) {
        TaskStatus.TODO -> "À faire"
        TaskStatus.DONE -> "Effectuée ✅"
        TaskStatus.OVERDUE -> "En retard ⚠️"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Top bar : flèche retour
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                navController.navigate("home") {
                    popUpTo("home") { inclusive = false }
                }
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour accueil",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // ── Contenu détail ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Titre
            Text(
                text = task.titre,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Badge statut
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = statusLabel,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            // Description
            Text(
                text = "Description",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = task.description ?: "Aucune description",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Date / Périodicité
            Text(
                text = "Planification",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (task.periodicity != PeriodicityType.NONE) {
                val perioLabel = when (task.periodicity) {
                    PeriodicityType.DAILY -> "Quotidienne"
                    PeriodicityType.WEEKLY -> "Hebdomadaire"
                    PeriodicityType.MONTHLY -> "Mensuelle"
                    else -> ""
                }
                DetailInfoRow(label = "Type", value = "Tâche périodique — $perioLabel")
                val nextDate = task.nextOccurrence
                    ?: if (task.dateLimite != null && task.heureLimite != null) "${task.dateLimite} ${task.heureLimite}" else "Non défini"
                DetailInfoRow(label = "Prochaine occurrence", value = nextDate)
            } else {
                DetailInfoRow(label = "Date limite", value = task.dateLimite ?: "Non définie")
                DetailInfoRow(label = "Heure limite", value = task.heureLimite ?: "Non définie")
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Priorité
            Text(
                text = "Priorité",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (task.priorite != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = priorityEmoji(task.priorite),
                        fontSize = 28.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${task.priorite} — ${priorityLabel(task.priorite)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Text(
                    text = "Aucune priorité définie",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Photo
            if (task.photoUrl != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Photo",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = File(task.photoUrl),
                    contentDescription = "Photo de la tâche",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Ligne info réutilisable dans TaskDetailScreen ─────────────────────────────
@Composable
fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.55f)
        )
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

// ── Icône de priorité ─────────────────────────────────────────────────────

fun priorityEmoji(priorite: Int?): String = when (priorite) {
    1 -> "🪶"
    2 -> "🌿"
    3 -> "⚡"
    4 -> "🔥"
    5 -> "⚠️"
    else -> ""
}

fun priorityLabel(priorite: Int?): String = when (priorite) {
    1 -> "Très faible"
    2 -> "Faible"
    3 -> "Modérée"
    4 -> "Élevée"
    5 -> "Critique"
    else -> "Aucune"
}

@Composable
fun PrioritySelector(
    selectedPriority: Int?,
    onPriorityChange: (Int?) -> Unit
) {
    Text(
        "Priorité",
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Bouton "Aucune"
        val noneSelected = selectedPriority == null
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (noneSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .clickable { onPriorityChange(null) }
                .padding(2.dp)
        ) {
            Text(
                text = "—",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                color = if (noneSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        // Boutons 1 à 5
        for (p in 1..5) {
            val isSelected = selectedPriority == p
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPriorityChange(p) }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = priorityEmoji(p),
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "$p",
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    if (selectedPriority != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Priorité ${priorityLabel(selectedPriority)}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}

// ── Sélecteur de photo réutilisable ──────────────────────────────────────

@Composable
fun PhotoPickerSection(
    photoPath: String?,
    onPhotoPicked: (String?) -> Unit
) {
    val context = LocalContext.current

    // Caméra : URI temporaire pour TakePicture
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val path = copyImageToInternal(context, uri)
            onPhotoPicked(path)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraUri?.let { uri ->
                // Copie depuis le cache externe vers le stockage interne
                val path = copyImageToInternal(context, uri)
                onPhotoPicked(path)
            }
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createCameraImageUri(context)
            cameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    Text("Photo", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
    Spacer(modifier = Modifier.height(8.dp))

    // Aperçu de la photo ou placeholder
    if (photoPath != null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = File(photoPath),
                contentDescription = "Photo de la tâche",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { galleryLauncher.launch("image/*") },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.weight(1f)
        ) {
            Text("🖼 Galerie", color = MaterialTheme.colorScheme.onSurface)
        }
        Button(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    val uri = createCameraImageUri(context)
                    cameraUri = uri
                    cameraLauncher.launch(uri)
                } else {
                    cameraPermLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.weight(1f)
        ) {
            Text("📷 Caméra", color = MaterialTheme.colorScheme.onSurface)
        }
        if (photoPath != null) {
            Button(
                onClick = { onPhotoPicked(null) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Supprimer photo", tint = MaterialTheme.colorScheme.onError)
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

// ── AddTaskFormScreen ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskFormScreen(navController: NavHostController, taskViewModel: TaskViewModel) {
    var titre by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priorite by remember { mutableStateOf<Int?>(null) }
    var photoPath by remember { mutableStateOf<String?>(null) }
    // Date limite classique
    var hasDeadline by remember { mutableStateOf(false) }
    var dateLimite by remember { mutableStateOf("") }
    var heureLimite by remember { mutableStateOf("") }
    // Périodicité
    var hasPeriodicity by remember { mutableStateOf(false) }
    var periodicity by remember { mutableStateOf(PeriodicityType.WEEKLY) }
    var periodicityStartDate by remember { mutableStateOf("") }
    var periodicityStartHeure by remember { mutableStateOf("") }

    // Erreurs de validation
    var titreError by remember { mutableStateOf(false) }
    var temporaliteError by remember { mutableStateOf(false) }
    var deadlineDateError by remember { mutableStateOf(false) }
    var deadlineHeureError by remember { mutableStateOf(false) }
    var perioDateError by remember { mutableStateOf(false) }
    var perioHeureError by remember { mutableStateOf(false) }

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
        Text("Titre *", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = titre,
            onValueChange = { titre = it; titreError = false },
            placeholder = { Text("Titre ...") },
            isError = titreError,
            supportingText = if (titreError) {{ Text("Le titre est obligatoire", color = MaterialTheme.colorScheme.error) }} else null,
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

        // Priorité
        PrioritySelector(
            selectedPriority = priorite,
            onPriorityChange = { priorite = it }
        )

        // Photo
        PhotoPickerSection(
            photoPath = photoPath,
            onPhotoPicked = { photoPath = it }
        )

        // Section périodicité
        if (temporaliteError) {
            Text(
                text = "⚠ Choisissez une date limite ou une périodicité",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        PeriodicitySection(
            hasDeadline = hasDeadline,
            onHasDeadlineChange = { hasDeadline = it; temporaliteError = false; deadlineDateError = false; deadlineHeureError = false },
            dateLimite = dateLimite,
            onDateClick = {
                DatePickerDialog(context, { _, y, m, d ->
                    dateLimite = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                    deadlineDateError = false
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            },
            heureLimite = heureLimite,
            onHeureClick = {
                TimePickerDialog(context, { _, h, min ->
                    heureLimite = String.format(Locale.getDefault(), "%02d:%02d", h, min)
                    deadlineHeureError = false
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
            },
            hasPeriodicity = hasPeriodicity,
            onHasPeriodicityChange = { hasPeriodicity = it; temporaliteError = false; perioDateError = false; perioHeureError = false },
            periodicity = periodicity,
            onPeriodicityChange = { periodicity = it },
            periodicityStartDate = periodicityStartDate,
            onPeriodicityDateClick = {
                DatePickerDialog(context, { _, y, m, d ->
                    periodicityStartDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                    perioDateError = false
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            },
            periodicityStartHeure = periodicityStartHeure,
            onPeriodicityHeureClick = {
                TimePickerDialog(context, { _, h, min ->
                    periodicityStartHeure = String.format(Locale.getDefault(), "%02d:%02d", h, min)
                    perioHeureError = false
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
            }
        )
        // Messages d'erreur détaillés sur les champs date/heure
        if (deadlineDateError) {
            Text("⚠ La date limite est requise", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (deadlineHeureError) {
            Text("⚠ L'heure limite est requise", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (perioDateError) {
            Text("⚠ La date de la première occurrence est requise", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (perioHeureError) {
            Text("⚠ L'heure de la première occurrence est requise", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                // Validation
                var valid = true
                if (titre.isBlank()) { titreError = true; valid = false }
                if (!hasDeadline && !hasPeriodicity) { temporaliteError = true; valid = false }
                if (hasDeadline) {
                    if (dateLimite.isEmpty()) { deadlineDateError = true; valid = false }
                    if (heureLimite.isEmpty()) { deadlineHeureError = true; valid = false }
                }
                if (hasPeriodicity) {
                    if (periodicityStartDate.isEmpty()) { perioDateError = true; valid = false }
                    if (periodicityStartHeure.isEmpty()) { perioHeureError = true; valid = false }
                }
                if (!valid) return@Button

                val task = Task(
                    titre = titre,
                    description = description.ifBlank { null },
                    dateLimite = if (hasDeadline && dateLimite.isNotEmpty()) dateLimite else null,
                    heureLimite = if (hasDeadline && heureLimite.isNotEmpty()) heureLimite else null,
                    priorite = priorite, photoUrl = photoPath, xpReward = null,
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
    val totalDone by taskViewModel.totalTasksDone.collectAsStateWithLifecycle()
    val currentRank = getRankForCount(totalDone)
    val nextRank = getNextRank(totalDone)
    val progress = getRankProgress(totalDone)

    // Animation de la jauge
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        animatedProgress.animateTo(progress, animationSpec = tween(900, easing = FastOutSlowInEasing))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
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
        Spacer(modifier = Modifier.height(32.dp))

        // Carte de rang
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = currentRank.emoji, fontSize = 56.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = currentRank.name,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$totalDone tâche${if (totalDone > 1) "s" else ""} accomplie${if (totalDone > 1) "s" else ""} au total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Jauge de progression
                if (nextRank != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = currentRank.emoji + " ${currentRank.threshold}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "${nextRank.threshold} " + nextRank.emoji,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(7.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress.value)
                                .height(14.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(7.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Prochain rang : ${nextRank.name} ${nextRank.emoji} (encore ${nextRank.threshold - totalDone} tâche${if (nextRank.threshold - totalDone > 1) "s" else ""})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Rang maximum atteint
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(7.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "🎉 Rang maximum atteint !",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tous les rangs
        Text(
            text = "Tous les rangs",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        RANKS.forEach { rank ->
            val unlocked = totalDone >= rank.threshold
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(
                        if (rank == currentRank) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (unlocked) rank.emoji else "🔒",
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rank.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (rank == currentRank) FontWeight.Bold else FontWeight.Normal,
                        color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Text(
                        text = if (rank.threshold == 0) "Rang de départ" else "Dès ${rank.threshold} tâches",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                if (rank == currentRank) {
                    Text(
                        text = "← Actuel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Statistiques rapides
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Statistiques",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$doneCount", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(text = "En cours (DONE)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$totalDone", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        Text(text = "Total accompli", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                    }
                }
            }
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
    var priorite by remember { mutableStateOf<Int?>(taskToEdit?.priorite) }
    var photoPath by remember { mutableStateOf<String?>(taskToEdit?.photoUrl) }
    // Date limite
    var hasDeadline by remember { mutableStateOf(taskToEdit?.periodicity == PeriodicityType.NONE && taskToEdit.dateLimite != null) }
    var dateLimite by remember { mutableStateOf(taskToEdit?.dateLimite ?: "") }
    var heureLimite by remember { mutableStateOf(taskToEdit?.heureLimite ?: "") }
    // Périodicité
    var hasPeriodicity by remember { mutableStateOf(taskToEdit?.periodicity != PeriodicityType.NONE) }
    var periodicity by remember { mutableStateOf(taskToEdit?.periodicity?.takeIf { it != PeriodicityType.NONE } ?: PeriodicityType.WEEKLY) }
    var periodicityStartDate by remember { mutableStateOf(taskToEdit?.nextOccurrence?.substringBefore(" ") ?: taskToEdit?.dateLimite ?: "") }
    var periodicityStartHeure by remember { mutableStateOf(taskToEdit?.nextOccurrence?.substringAfter(" ") ?: taskToEdit?.heureLimite ?: "") }

    // Erreurs de validation
    var titreError by remember { mutableStateOf(false) }
    var temporaliteError by remember { mutableStateOf(false) }
    var deadlineDateError by remember { mutableStateOf(false) }
    var deadlineHeureError by remember { mutableStateOf(false) }
    var perioDateError by remember { mutableStateOf(false) }
    var perioHeureError by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }

    LaunchedEffect(taskToEdit) {
        if (taskToEdit != null) {
            titre = taskToEdit.titre
            description = taskToEdit.description ?: ""
            priorite = taskToEdit.priorite
            photoPath = taskToEdit.photoUrl
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
        Text("Titre *", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = titre,
            onValueChange = { titre = it; titreError = false },
            placeholder = { Text("Titre ...") },
            isError = titreError,
            supportingText = if (titreError) {{ Text("Le titre est obligatoire", color = MaterialTheme.colorScheme.error) }} else null,
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
        Spacer(modifier = Modifier.height(16.dp))

        // Priorité
        PrioritySelector(
            selectedPriority = priorite,
            onPriorityChange = { priorite = it }
        )

        // Photo
        PhotoPickerSection(
            photoPath = photoPath,
            onPhotoPicked = { photoPath = it }
        )

        // Section périodicité
        if (temporaliteError) {
            Text(
                text = "⚠ Choisissez une date limite ou une périodicité",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        PeriodicitySection(
            hasDeadline = hasDeadline,
            onHasDeadlineChange = { hasDeadline = it; temporaliteError = false; deadlineDateError = false; deadlineHeureError = false },
            dateLimite = dateLimite,
            onDateClick = {
                DatePickerDialog(context, { _, y, m, d ->
                    dateLimite = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                    deadlineDateError = false
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            },
            heureLimite = heureLimite,
            onHeureClick = {
                TimePickerDialog(context, { _, h, min ->
                    heureLimite = String.format(Locale.getDefault(), "%02d:%02d", h, min)
                    deadlineHeureError = false
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
            },
            hasPeriodicity = hasPeriodicity,
            onHasPeriodicityChange = { hasPeriodicity = it; temporaliteError = false; perioDateError = false; perioHeureError = false },
            periodicity = periodicity,
            onPeriodicityChange = { periodicity = it },
            periodicityStartDate = periodicityStartDate,
            onPeriodicityDateClick = {
                DatePickerDialog(context, { _, y, m, d ->
                    periodicityStartDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                    perioDateError = false
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            },
            periodicityStartHeure = periodicityStartHeure,
            onPeriodicityHeureClick = {
                TimePickerDialog(context, { _, h, min ->
                    periodicityStartHeure = String.format(Locale.getDefault(), "%02d:%02d", h, min)
                    perioHeureError = false
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
            }
        )
        // Messages d'erreur détaillés sur les champs date/heure
        if (deadlineDateError) {
            Text("⚠ La date limite est requise", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (deadlineHeureError) {
            Text("⚠ L'heure limite est requise", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (perioDateError) {
            Text("⚠ La date de la première occurrence est requise", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (perioHeureError) {
            Text("⚠ L'heure de la première occurrence est requise", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                // Validation
                var valid = true
                if (titre.isBlank()) { titreError = true; valid = false }
                if (!hasDeadline && !hasPeriodicity) { temporaliteError = true; valid = false }
                if (hasDeadline) {
                    if (dateLimite.isEmpty()) { deadlineDateError = true; valid = false }
                    if (heureLimite.isEmpty()) { deadlineHeureError = true; valid = false }
                }
                if (hasPeriodicity) {
                    if (periodicityStartDate.isEmpty()) { perioDateError = true; valid = false }
                    if (periodicityStartHeure.isEmpty()) { perioHeureError = true; valid = false }
                }
                if (!valid) return@Button

                if (taskToEdit != null) {
                    val updatedTask = taskToEdit.copy(
                        titre = titre,
                        description = description.ifBlank { null },
                        priorite = priorite,
                        photoUrl = photoPath,
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

