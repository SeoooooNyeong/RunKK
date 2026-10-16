package com.a207.runkk.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.a207.runkk.ui.home.HomeScreen
import kotlinx.serialization.Serializable

/** Navigation 3 keys: one data object or data class per screen. */
@Serializable
data object HomeKey : NavKey

@Composable
fun RunkkNavDisplay(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(HomeKey)
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<HomeKey> { HomeScreen() }
        }
    )
}
