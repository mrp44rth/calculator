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
  private lateinit var viewModel: CalculatorViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize Database & Repository
    val database = AppDatabase.getDatabase(applicationContext)
    val repository = HistoryRepository(database.historyDao())
    val vaultRepository = com.example.data.repository.VaultRepository(database.vaultFileDao())
    
    // Instantiate ViewModel with custom factory using ViewModelProvider
    viewModel = androidx.lifecycle.ViewModelProvider(
      this,
      CalculatorViewModel.provideFactory(application, repository, vaultRepository)
    )[CalculatorViewModel::class.java]

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

  override fun onStop() {
    super.onStop()
    // Securely terminate the activity when backgrounded (Home button, App Switcher, Screen Lock)
    // to prevent anyone from seeing the decrypted vault. We do not exit if a media picker is active.
    if (!viewModel.isPickerActive) {
      finishAndRemoveTask()
    }
  }
}

