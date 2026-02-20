package com.example.doneit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.text.font.FontWeight

class TaskViewModelFactory(private val taskDao: com.example.doneit.data.TaskDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(taskDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Room database with proper configuration
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "doneit-db"
        )
            .fallbackToDestructiveMigration(dropAllTables = true) // Permet de recréer la DB en cas de changement de schéma
            .build()

        enableEdgeToEdge()
        setContent {
            val taskViewModel: TaskViewModel = viewModel(
                factory = TaskViewModelFactory(database.taskDao())
            )
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

@Composable
fun AppNavigation(modifier: Modifier = Modifier, taskViewModel: TaskViewModel) {
    val navController = rememberNavController()
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
    }
}

@Composable
fun HomeScreen(navController: NavHostController, taskViewModel: TaskViewModel) {
    val tasks = taskViewModel.allTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val todoTasks = tasks.value.filter { it.status == TaskStatus.TODO }
    val overdueTasks = tasks.value.filter { it.status == TaskStatus.OVERDUE }
    val doneTasks = tasks.value.filter { it.status == TaskStatus.DONE }

    // Met à jour les tâches non faites dont la date de fin est dépassée
    LaunchedEffect(tasks.value) {
        tasks.value.filter { it.status == TaskStatus.TODO && it.dateLimite != null && it.heureLimite != null }
            .forEach { task ->
                val dateTime = task.dateLimite + " " + task.heureLimite
                val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val endMillis = try { formatter.parse(dateTime)?.time ?: 0L } catch (_: Exception) { 0L }
                if (endMillis > 0 && endMillis < System.currentTimeMillis()) {
                    val updatedTask = task.copy(status = TaskStatus.OVERDUE)
                    taskViewModel.updateTask(updatedTask)
                }
            }
    }

    var expandedDone by remember { mutableStateOf(true) }

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
            IconButton(onClick = { navController.navigate("addTaskForm") }) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (todoTasks.isEmpty() && overdueTasks.isEmpty()) {
            Text(
                text = "Aucune tâche à effectuer",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Column {
                overdueTasks.forEach { task ->
                    TaskCard(
                        task = task,
                        color = MaterialTheme.colorScheme.primary,
                        onCheck = { taskViewModel.completeTaskWithReward(task) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                todoTasks.forEach { task ->
                    TaskCard(
                        task = task,
                        color = MaterialTheme.colorScheme.secondary,
                        onCheck = { taskViewModel.completeTaskWithReward(task) }
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
            IconButton(onClick = { expandedDone = !expandedDone }) {
                Icon(
                    if (expandedDone) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expandedDone) "Réduire" else "Déplier",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (doneTasks.isEmpty()) {
            Text(
                text = "Aucune tâche effectuée",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (expandedDone) {
            Column {
                doneTasks.forEach { task ->
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
    }
}

@Composable
fun TaskCard(task: Task, color: Color, onCheck: () -> Unit) {
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
    }
}