package dev.seungmin.nav3.uikit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.rememberSceneState

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
    val nativeChanges = remember { mutableIntStateOf(0) }
    val coordinator = remember {
        UIKitNavigationCoordinator<T>(
            parentLocals = parentLocals,
            configure = configure,
            onBack = { currentOnBack() },
            onNativeChange = { nativeChanges.intValue++ },
        )
    }
    val overlays = rememberOverlays(entries, options, onBack)

    // Reading the counter recomposes this display after every change UIKit made on its own, so the
    // effect below runs even when the app kept its back stack, and UIKit follows the entries again.
    nativeChanges.intValue
    SideEffect {
        coordinator.parentLocals.value = parentLocals
        coordinator.setEntries(entries, overlays)
    }
    DisposableEffect(coordinator) { onDispose { coordinator.dispose() } }

    NativeContainer(coordinator.navigationController, modifier)
}

/**
 * Places a display's native container: in the root of [NativeNavigationViewController] when it can
 * have it, otherwise embedded in Compose through UIKit interop.
 */
@Composable
internal fun NativeContainer(controller: platform.UIKit.UIViewController, modifier: Modifier) {
    if (rememberRootPlacement(controller) != RootPlacement.Embedded) return
    UIKitViewController(
        factory = { controller },
        modifier = modifier,
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
        ),
    )
}

/**
 * The content of each entry that the scene strategies show in an overlay scene, by content key.
 * The strategies run as in `NavDisplay`, so dialogs and other overlays come from the same metadata
 * and strategies on every platform. Overlay scenes of more than one entry are not supported; their
 * entries are pushed.
 */
@Composable
internal fun <T : Any> rememberOverlays(
    entries: List<NavEntry<T>>,
    options: NavDisplayOptions<T>,
    onBack: () -> Unit,
): Map<Any, @Composable () -> Unit> {
    if (entries.isEmpty()) return emptyMap()
    val sceneState = rememberSceneState(
        entries = entries,
        sceneStrategies = options.sceneStrategies,
        sceneDecoratorStrategies = options.sceneDecoratorStrategies,
        onBack = onBack,
    )
    return remember(sceneState) {
        buildMap {
            sceneState.overlayScenes.forEach { scene ->
                scene.entries.singleOrNull()?.let { put(it.contentKey, scene.content) }
            }
        }
    }
}
