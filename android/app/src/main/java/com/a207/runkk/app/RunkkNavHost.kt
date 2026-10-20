package com.a207.runkk.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes

/**
 * 화면 그래프를 띄운다(6.1.14). 각 ui.* 모듈이 Hilt @IntoSet으로 등록한 FeatureEntry만 받으므로
 * app은 ui.*를 import하지 않는다(design-review.md M35).
 */
@Composable
fun RunkkNavHost(featureEntries: Set<FeatureEntry>, modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Routes.Key(Routes.HOME))
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryProvider =
            entryProvider<NavKey> {
                featureEntries.forEach { it.register(this) }
            }
    )
}
