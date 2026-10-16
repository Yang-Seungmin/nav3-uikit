package dev.seungmin.nav3.uikit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * One top-level destination of [NativeTabDisplay], with its own back stack.
 *
 * [icon] is an SF Symbol name on iOS; the tab bar draws the selected state itself. Other
 * platforms show only [title].
 */
public class NativeTab<T : Any>(
    public val backStack: List<T>,
    public val title: String,
    public val icon: String,
)

/**
 * Displays several Navigation 3 back stacks behind a bottom tab bar.
 *
 * On iOS this is a `UITabBarController` whose tabs each hold a `UINavigationController`, so the
 * system decides where the tab bar goes: at the bottom on most iPhones, in the side bar on
 * iPhone Duo, or as a sidebar where one is available. Each tab behaves like [NativeNavDisplay]:
 * native pushes, swipe-back, sheets, and per-entry metadata from [NativeNavigation].
 *
 * The caller owns the selection and the back stacks. When the user picks a tab in the tab bar,
 * [onTabSelected] is called with its index. When UIKit pops a tab's screens on its own
 * (swipe-back, the back button, or tapping the selected tab again), [onBack] is called once per
 * removed entry with that tab's index.
 *
 * Every tab keeps its entries decorated while another tab is shown, so saveable state and
 * ViewModels survive tab switches. Entries of different tabs never share decorator state, even
 * when the same key is on two back stacks.
 */
@Composable
public fun <T : Any> NativeTabDisplay(
    tabs: List<NativeTab<T>>,
    selectedTab: Int,
    onTabSelected: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (tab: Int) -> Unit = { tab ->
        val backStack = tabs[tab].backStack
        if (backStack is MutableList<T>) {
            backStack.removeLastOrNull()
        }
    },
    entryDecorators: List<NavEntryDecorator<T>> =
        listOf(rememberSaveableStateHolderNavEntryDecorator()),
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
        modifier = modifier,
    )
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
    modifier: Modifier,
)
