package com.bookwormbliss.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.bookwormbliss.app.navigation.BookwormNavGraph
import com.bookwormbliss.app.ui.theme.BookwormBlissTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as BookwormBlissApp

        setContent {
            val keepScreenOn by app.preferencesRepository.preferencesFlow
                .map { it.keepScreenOn }
                .collectAsState(initial = true)

            window.setFlags(
                if (keepScreenOn) android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0,
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )

            BookwormBlissTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BookwormNavGraph()
                }
            }
        }
    }
}
