package com.tamarilog.codexblanche

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tamarilog.codexblanche.ui.CodexBlancheTheme
import com.tamarilog.codexblanche.ui.navigation.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CodexBlancheApplication
        setContent {
            val vm: ChatViewModel = viewModel(factory = ChatViewModel.factory(app))
            val ui by vm.uiState.collectAsState()
            CodexBlancheTheme(themePreference = ui.snapshot.settings.theme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(chatViewModel = vm)
                }
            }
        }
    }
}
