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
import com.example.doneit.ui.theme.DoneItTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DoneItTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "loading", modifier = modifier) {
        composable("loading"){
            LoadingScreen(navController = navController)
        }
        composable("home") {
            HomeScreen(navController = navController)
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
fun HomeScreen(navController: NavHostController){
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
