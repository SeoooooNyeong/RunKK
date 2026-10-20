package com.a207.runkk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.navigation.FeatureEntry
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var featureEntries: Set<@JvmSuppressWildcards FeatureEntry>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RunkkTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RunkkNavHost(
                        featureEntries = featureEntries,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
