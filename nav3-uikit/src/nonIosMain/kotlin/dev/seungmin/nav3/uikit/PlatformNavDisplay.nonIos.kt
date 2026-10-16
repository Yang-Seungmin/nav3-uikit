package dev.seungmin.nav3.uikit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay

@Composable
internal actual fun <T : Any> PlatformNavDisplay(
    entries: List<NavEntry<T>>,
    modifier: Modifier,
    onBack: () -> Unit,
    options: NavDisplayOptions<T>,
) {
    NavDisplay(
        entries = entries,
        modifier = modifier,
        contentAlignment = options.contentAlignment,
        sceneStrategies = options.sceneStrategies,
        sceneDecoratorStrategies = options.sceneDecoratorStrategies,
        sharedTransitionScope = options.sharedTransitionScope,
        sizeTransform = options.sizeTransform,
        transitionSpec = options.transitionSpec,
        popTransitionSpec = options.popTransitionSpec,
        predictivePopTransitionSpec = options.predictivePopTransitionSpec,
        onBack = onBack,
    )
}
