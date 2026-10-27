package com.charon.dexon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.charon.dexon.ui.MainViewModel
import com.charon.dexon.ui.navigation.DexonNavHost
import com.charon.dexon.ui.theme.DexonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DexonTheme {
                val vm: MainViewModel = viewModel()
                DexonNavHost(vm)
            }
        }
    }
}
