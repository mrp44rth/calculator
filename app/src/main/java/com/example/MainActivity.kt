package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.data.database.AppDatabase
import com.example.data.repository.HistoryRepository
import com.example.ui.screens.CalculatorScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.CalculatorViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize Database & Repository
    val database = AppDatabase.getDatabase(applicationContext)
    val repository = HistoryRepository(database.historyDao())
    
    // Instantiate ViewModel with custom factory
    val viewModel: CalculatorViewModel by viewModels {
      CalculatorViewModel.provideFactory(repository)
    }

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        CalculatorScreen(
          viewModel = viewModel,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }
}

