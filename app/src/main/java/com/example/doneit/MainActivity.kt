package com.example.doneit

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
            AddTaskFormScreen(navController = navController)
        }
        composable("profile") {
            ProfileScreen(navController = navController)
        }
    }
}

@Composable
fun HomeScreen(navController: NavHostController, taskViewModel: TaskViewModel){
    Text(text = "Home Screen")
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

@Composable
fun AddTaskFormScreen(navController: NavHostController){
    Text(text = "Add Task Form Screen")
}

@Composable
fun ProfileScreen(navController: NavHostController){
    Text(text = "Profile Screen")
}
