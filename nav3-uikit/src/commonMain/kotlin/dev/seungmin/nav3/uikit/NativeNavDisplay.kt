package dev.seungmin.nav3.uikit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * Displays a Navigation 3 back stack with the platform's native navigation container.
 *
 * The parameters mirror [androidx.navigation3.ui.NavDisplay], so a call site can switch between
 * the two by changing the function name. On iOS each entry is hosted in its own
 * `UIViewController` inside a `UINavigationController`, which gives the system push/pop
 * transitions, the edge swipe-back gesture, the native navigation bar, and sheets. Other platforms
 * delegate to [androidx.navigation3.ui.NavDisplay].
 *
 * The back stack stays the source of truth. When the user pops a screen natively (swipe-back, the
 * back button, or dragging a sheet down), [onBack] is called once per removed entry.
 *
 * Per-entry presentation on iOS is controlled through [NavEntry.metadata]; see [NativeNavigation].
 */
@Composable
public fun <T : Any> NativeNavDisplay(
    backStack: List<T>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {
        if (backStack is MutableList<T>) {
            backStack.removeLastOrNull()
        }
    },
    entryDecorators: List<NavEntryDecorator<T>> =
        listOf(rememberSaveableStateHolderNavEntryDecorator()),
    entryProvider: (key: T) -> NavEntry<T>,
) {
    PlatformNavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = onBack,
        entryDecorators = entryDecorators,
        entryProvider = entryProvider,
    )
}

@Composable
internal expect fun <T : Any> PlatformNavDisplay(
    backStack: List<T>,
    modifier: Modifier,
    onBack: () -> Unit,
    entryDecorators: List<NavEntryDecorator<T>>,
    entryProvider: (key: T) -> NavEntry<T>,
)
