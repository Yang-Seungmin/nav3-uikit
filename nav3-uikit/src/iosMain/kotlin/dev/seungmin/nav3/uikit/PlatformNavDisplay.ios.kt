package dev.seungmin.nav3.uikit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries

@Composable
internal actual fun <T : Any> PlatformNavDisplay(
    backStack: List<T>,
    modifier: Modifier,
    onBack: () -> Unit,
    entryDecorators: List<NavEntryDecorator<T>>,
    entryProvider: (key: T) -> NavEntry<T>,
) {
    // Decorators run here, in the host composition, so Navigation 3's own pop tracking decides
    // when per-entry state (saveable state, ViewModels) is cleared. Each entry's content is then
    // composed inside its own UIViewController.
    val entries = rememberDecoratedNavEntries(backStack, entryDecorators, entryProvider)
    val parentLocals = currentCompositionLocalContext
    val currentOnBack by rememberUpdatedState(onBack)
    val coordinator = remember { UIKitNavigationCoordinator<T>(parentLocals) { currentOnBack() } }

    SideEffect {
        coordinator.parentLocals.value = parentLocals
        coordinator.setEntries(entries)
    }
    DisposableEffect(coordinator) { onDispose { coordinator.dispose() } }

    // In the root of NativeNavigationViewController the container is not embedded in Compose.
    if (rememberHoistedToRoot(coordinator.navigationController)) return

    UIKitViewController(
        factory = { coordinator.navigationController },
        modifier = modifier,
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
        ),
    )
}
