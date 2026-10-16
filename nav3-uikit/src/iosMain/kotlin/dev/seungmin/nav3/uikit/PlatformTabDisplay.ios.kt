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
internal actual fun <T : Any> PlatformTabDisplay(
    tabs: List<NativeTab<T>>,
    entries: List<List<NavEntry<T>>>,
    selectedTab: Int,
    onTabSelected: (index: Int) -> Unit,
    onBack: (tab: Int) -> Unit,
    modifier: Modifier,
) {
    val parentLocals = currentCompositionLocalContext
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val currentOnBack by rememberUpdatedState(onBack)
    val coordinator = remember {
        UIKitTabCoordinator<T>(
            onTabSelected = { currentOnTabSelected(it) },
            onBack = { currentOnBack(it) },
        )
    }

    SideEffect { coordinator.update(tabs, entries, selectedTab, parentLocals) }
    DisposableEffect(coordinator) { onDispose { coordinator.dispose() } }

    // In the root of NativeNavigationViewController the container is not embedded in Compose.
    if (rememberHoistedToRoot(coordinator.tabBarController)) return

    UIKitViewController(
        factory = { coordinator.tabBarController },
        modifier = modifier,
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
        ),
    )
}
