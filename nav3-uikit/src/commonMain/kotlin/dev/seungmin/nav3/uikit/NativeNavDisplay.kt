package dev.seungmin.nav3.uikit

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import androidx.navigationevent.NavigationEvent

/**
 * Displays a Navigation 3 back stack with the platform's native navigation container.
 *
 * The parameters are those of [androidx.navigation3.ui.NavDisplay], so a call site can switch
 * between the two by changing the function name. On iOS each entry is hosted in its own
 * `UIViewController` inside a `UINavigationController`, which gives the system push/pop
 * transitions, the edge swipe-back gesture, the native navigation bar, and sheets. Other platforms
 * pass every parameter on to [androidx.navigation3.ui.NavDisplay].
 *
 * On iOS the native container fills the space [modifier] leaves it, because Compose cannot measure
 * a UIKit view's content. A call site without `Modifier.fillMaxSize()` therefore shows its screens
 * as it does with `NavDisplay`.
 *
 * On iOS the transition parameters, [sizeTransform], [contentAlignment],
 * [sceneDecoratorStrategies], and [sharedTransitionScope] have no effect, because UIKit animates
 * the transitions. [sceneStrategies] run as they do in `NavDisplay`: an entry that a strategy shows
 * in an [androidx.navigation3.scene.OverlayScene], such as an entry with [DialogSceneStrategy.dialog]
 * metadata, is presented over the screens below it with that scene's own content. Every other entry
 * is pushed on its own.
 *
 * The native navigation bar appears only for entries that ask for it, with [NativeNavigation.title]
 * metadata or [NativeNavigationBarItems]. Screens that draw their own top app bar look the same as
 * under `NavDisplay`.
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
    contentAlignment: Alignment = Alignment.TopStart,
    onBack: () -> Unit = {
        if (backStack is MutableList<T>) {
            backStack.removeLastOrNull()
        }
    },
    entryDecorators: List<NavEntryDecorator<T>> =
        listOf(rememberSaveableStateHolderNavEntryDecorator()),
    sceneStrategies: List<SceneStrategy<T>> = listOf(SinglePaneSceneStrategy()),
    sceneDecoratorStrategies: List<SceneDecoratorStrategy<T>> = emptyList(),
    sharedTransitionScope: SharedTransitionScope? = null,
    sizeTransform: SizeTransform? = null,
    transitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultTransitionSpec(),
    popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultPopTransitionSpec(),
    predictivePopTransitionSpec:
        AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform =
        defaultPredictivePopTransitionSpec(),
    entryProvider: (key: T) -> NavEntry<T>,
) {
    require(backStack.isNotEmpty()) { "NativeNavDisplay backstack cannot be empty" }
    val entries = rememberDecoratedNavEntries(backStack, entryDecorators, entryProvider)
    NativeNavDisplay(
        entries = entries,
        modifier = modifier,
        contentAlignment = contentAlignment,
        sceneStrategies = sceneStrategies,
        sceneDecoratorStrategies = sceneDecoratorStrategies,
        sharedTransitionScope = sharedTransitionScope,
        sizeTransform = sizeTransform,
        transitionSpec = transitionSpec,
        popTransitionSpec = popTransitionSpec,
        predictivePopTransitionSpec = predictivePopTransitionSpec,
        onBack = onBack,
    )
}

/**
 * Displays entries that were already decorated with
 * [androidx.navigation3.runtime.rememberDecoratedNavEntries], like the `entries` overload of
 * [androidx.navigation3.ui.NavDisplay]. Use it when the back stack is assembled from several
 * decorated back stacks.
 */
@Composable
public fun <T : Any> NativeNavDisplay(
    entries: List<NavEntry<T>>,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    sceneStrategies: List<SceneStrategy<T>> = listOf(SinglePaneSceneStrategy()),
    sceneDecoratorStrategies: List<SceneDecoratorStrategy<T>> = emptyList(),
    sharedTransitionScope: SharedTransitionScope? = null,
    sizeTransform: SizeTransform? = null,
    transitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultTransitionSpec(),
    popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultPopTransitionSpec(),
    predictivePopTransitionSpec:
        AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform =
        defaultPredictivePopTransitionSpec(),
    onBack: () -> Unit,
) {
    PlatformNavDisplay(
        entries = entries,
        modifier = modifier,
        onBack = onBack,
        options = NavDisplayOptions(
            contentAlignment = contentAlignment,
            sceneStrategies = sceneStrategies,
            sceneDecoratorStrategies = sceneDecoratorStrategies,
            sharedTransitionScope = sharedTransitionScope,
            sizeTransform = sizeTransform,
            transitionSpec = transitionSpec,
            popTransitionSpec = popTransitionSpec,
            predictivePopTransitionSpec = predictivePopTransitionSpec,
        ),
    )
}

/** The [androidx.navigation3.ui.NavDisplay] parameters that only non-iOS platforms use in full. */
internal class NavDisplayOptions<T : Any>(
    val contentAlignment: Alignment,
    val sceneStrategies: List<SceneStrategy<T>>,
    val sceneDecoratorStrategies: List<SceneDecoratorStrategy<T>>,
    val sharedTransitionScope: SharedTransitionScope?,
    val sizeTransform: SizeTransform?,
    val transitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform,
    val popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform,
    val predictivePopTransitionSpec:
        AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform,
)

@Composable
internal expect fun <T : Any> PlatformNavDisplay(
    entries: List<NavEntry<T>>,
    modifier: Modifier,
    onBack: () -> Unit,
    options: NavDisplayOptions<T>,
)
