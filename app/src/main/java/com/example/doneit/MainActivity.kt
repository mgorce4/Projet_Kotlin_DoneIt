package com.example.doneit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "doneit-db"
        ).build()
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
fun LoadingScreen(navController: NavHostController){
    Text(text = "Loading Screen")
}

@Composable
fun AddTaskFormScreen(navController: NavHostController){
    Text(text = "Add Task Form Screen")
}

@Composable
fun ProfileScreen(navController: NavHostController){
    Text(text = "Profile Screen")
}
