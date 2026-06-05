package com.example

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainScreen
import com.example.ui.TourViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Global exception protection to prevent any crash from stopping the app of Rajasthan Forensic Lab
    setupSafeEnvironment()
    
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
          val viewModel: TourViewModel = viewModel()
          MainScreen(viewModel = viewModel)
        }
      }
    }
  }

  private fun setupSafeEnvironment() {
    // Thread Exception handler for capturing background and main thread tasks crashes
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      Log.e("ForensicDiarySafe", "Caught uncaught exception on thread ${thread.name}", throwable)
      Handler(Looper.getMainLooper()).post {
        try {
          Toast.makeText(
            applicationContext,
            "An issue occurred: ${throwable.localizedMessage ?: "Please retry"}",
            Toast.LENGTH_LONG
          ).show()
        } catch (e: Exception) {
          // fallback
        }
      }
    }
  }
}
