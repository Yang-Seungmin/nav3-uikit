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

@Composable
internal actual fun <T : Any> PlatformNavDisplay(
    entries: List<NavEntry<T>>,
    modifier: Modifier,
    onBack: () -> Unit,
    options: NavDisplayOptions<T>,
) {
    // The entries were decorated in the host composition, so Navigation 3's own pop tracking
    // decides when per-entry state (saveable state, ViewModels) is cleared. Each entry's content
    // is then composed inside its own UIViewController.
    val parentLocals = currentCompositionLocalContext
    val currentOnBack by rememberUpdatedState(onBack)
    val configure = LocalNativeRootHost.current?.configure ?: {}
    val coordinator = remember {
        UIKitNavigationCoordinator<T>(parentLocals, configure) { currentOnBack() }
    }
    val showsDialogs = options.showsDialogs

    SideEffect {
        coordinator.parentLocals.value = parentLocals
        coordinator.setEntries(entries, showsDialogs)
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
