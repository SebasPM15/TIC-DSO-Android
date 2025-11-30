package com.mateopilco.ticdso

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
// Importante: Importar HomeScreen, no MainScreen
import com.mateopilco.ticdso.presentation.ui.screen.HomeScreen
import com.mateopilco.ticdso.presentation.viewmodel.MainViewModel
import com.mateopilco.ticdso.presentation.ui.theme.TICDSOTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TICDSOTheme {
                // Inyectamos el ViewModel automáticamente
                val viewModel: MainViewModel = viewModel()

                // Llamamos a la pantalla correcta
                HomeScreen(viewModel = viewModel)
            }
        }
    }
}