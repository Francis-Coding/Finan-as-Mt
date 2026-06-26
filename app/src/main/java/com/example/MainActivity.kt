package com.example

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.ui.AppContent
import com.example.ui.FinanceViewModel
import com.example.ui.theme.FinancasTheme

class MainActivity : FragmentActivity() {
    private val viewModel: FinanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinancasTheme(
                themeMode = viewModel.themeMode,
                primaryColorHex = viewModel.accentColor
            ) {
                AppContent(viewModel = viewModel)
            }
        }
    }
}
