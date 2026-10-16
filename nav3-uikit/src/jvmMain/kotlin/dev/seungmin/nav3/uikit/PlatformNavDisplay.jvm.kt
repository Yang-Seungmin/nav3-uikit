package dev.seungmin.nav3.uikit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.ui.NavDisplay

@Composable
internal actual fun <T : Any> PlatformNavDisplay(
    backStack: List<T>,
    modifier: Modifier,
    onBack: () -> Unit,
    entryDecorators: List<NavEntryDecorator<T>>,
    entryProvider: (key: T) -> NavEntry<T>,
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = onBack,
        entryDecorators = entryDecorators,
        entryProvider = entryProvider,
    )
}
