package dev.seungmin.nav3.uikit

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import androidx.navigationevent.NavigationEvent

/**
 * One top-level destination of [NativeTabDisplay], with its own back stack.
 *
 * The tab's icon is [systemImage], an SF Symbol name, on iOS when it is set, and [icon] otherwise.
 * iOS draws [icon] as a template image, so the tab bar tints it for the selected and unselected
 * states. Other platforms draw [icon] in the default tab bar.
 */
public class NativeTab<T : Any>(
    public val backStack: List<T>,
    public val title: String,
    public val icon: Painter? = null,
    public val systemImage: String? = null,
) {
    /** A tab whose icon is the SF Symbol [icon] on iOS. */
    public constructor(backStack: List<T>, title: String, icon: String) :
        this(backStack, title, icon = null, systemImage = icon)
}

/**
 * Displays several Navigation 3 back stacks behind a bottom tab bar.
 *
 * On iOS this is a `UITabBarController` whose tabs each hold a `UINavigationController`, so the
 * system decides where the tab bar goes: at the bottom on most iPhones, in the side bar on
 * iPhone Duo, or as a sidebar where one is available. Each tab behaves like [NativeNavDisplay]:
 * native pushes, swipe-back, sheets, overlays from [sceneStrategies], and per-entry metadata from
 * [NativeNavigation].
 *
 * The caller owns the selection and the back stacks. When the user picks a tab in the tab bar,
 * [onTabSelected] is called with its index. When UIKit pops a tab's screens on its own
 * (swipe-back, the back button, or tapping the selected tab again), [onBack] is called once per
 * removed entry with that tab's index. If the caller does not follow such a change, the display
 * returns UIKit to the selection and back stacks the caller holds.
 *
 * Every tab keeps its entries decorated while another tab is shown, so saveable state and
 * ViewModels survive tab switches. Entries of different tabs never share decorator state, even
 * when the same key is on two back stacks.
 *
 * Other platforms show the selected tab's back stack in a `NavDisplay` that receives the scene and
 * transition parameters, with [tabBar] below it; pass the app's own tab bar there. System back at
 * the root of any tab but the first selects the first tab, as in Navigation 3's multiple back
 * stack recipe. iOS ignores [tabBar] and the transition parameters.
 */
@Composable
public fun <T : Any> NativeTabDisplay(
    tabs: List<NativeTab<T>>,
    selectedTab: Int,
    onTabSelected: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    onBack: (tab: Int) -> Unit = { tab ->
        val backStack = tabs[tab].backStack
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
    tabBar: @Composable () -> Unit = { DefaultTabBar(tabs, selectedTab, onTabSelected) },
    entryProvider: (key: T) -> NavEntry<T>,
) {
    require(tabs.isNotEmpty()) { "NativeTabDisplay needs at least one tab" }
    require(selectedTab in tabs.indices) { "selectedTab $selectedTab is not a tab index" }

    // Decorating every tab, not just the selected one, keeps the hidden tabs' entries out of
    // Navigation 3's pop handling while they are off screen.
    val entries = tabs.mapIndexed { index, tab ->
        key(index) { rememberTabEntries(index, tab.backStack, entryDecorators, entryProvider) }
    }
    PlatformTabDisplay(
        tabs = tabs,
        entries = entries,
        selectedTab = selectedTab,
        onTabSelected = onTabSelected,
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
        tabBar = tabBar,
        modifier = modifier,
    )
}

/** A plain row of tab icons and titles, for platforms where the app passes no tab bar of its own. */
@Composable
private fun DefaultTabBar(tabs: List<NativeTab<*>>, selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedTab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics { this.selected = selected }
                    .clickable(role = Role.Tab) { onTabSelected(index) }
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                tab.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(24.dp)) }
                BasicText(
                    tab.title,
                    style = TextStyle(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                )
            }
        }
    }
}

@Composable
private fun <T : Any> rememberTabEntries(
    tab: Int,
    backStack: List<T>,
    entryDecorators: List<NavEntryDecorator<T>>,
    entryProvider: (key: T) -> NavEntry<T>,
): List<NavEntry<T>> {
    // Decorators are shared by all tabs and keep their state by content key. Prefixing the key
    // with the tab keeps two tabs showing the same destination apart.
    val entries = remember(backStack.toList()) {
        backStack.map { key ->
            val entry = entryProvider(key)
            NavEntry(key, "tab$tab/${entry.contentKey}", entry.metadata) { entry.Content() }
        }
    }
    return rememberDecoratedNavEntries(entries, entryDecorators)
}

@Composable
internal expect fun <T : Any> PlatformTabDisplay(
    tabs: List<NativeTab<T>>,
    entries: List<List<NavEntry<T>>>,
    selectedTab: Int,
    onTabSelected: (index: Int) -> Unit,
    onBack: (tab: Int) -> Unit,
    options: NavDisplayOptions<T>,
    tabBar: @Composable () -> Unit,
    modifier: Modifier,
)
