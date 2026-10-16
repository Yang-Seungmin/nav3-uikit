package dev.seungmin.nav3.uikit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry

@Composable
internal actual fun <T : Any> PlatformTabDisplay(
    tabs: List<TabLabel>,
    entries: List<List<NavEntry<T>>>,
    selectedTab: Int,
    onTabSelected: (index: Int) -> Unit,
    onBack: (tab: Int) -> Unit,
    options: NavDisplayOptions<T>,
    tabBar: @Composable () -> Unit,
    modifier: Modifier,
) {
    val parentLocals = currentCompositionLocalContext
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val currentOnBack by rememberUpdatedState(onBack)
    val configure = LocalNativeRootHost.current?.configure ?: {}
    val nativeChanges = remember { mutableIntStateOf(0) }
    val coordinator = remember {
        UIKitTabCoordinator<T>(
            parentLocals = parentLocals,
            configure = configure,
            onTabSelected = { currentOnTabSelected(it) },
            onBack = { currentOnBack(it) },
            onNativeChange = { nativeChanges.intValue++ },
        )
    }
    val overlays = entries.mapIndexed { index, tabEntries ->
        key(index) { rememberOverlays(tabEntries, options) { currentOnBack(index) } }
    }

    // See PlatformNavDisplay: a tab selection or pop the app did not follow is undone here.
    nativeChanges.intValue
    SideEffect { coordinator.update(tabs, entries, overlays, selectedTab, parentLocals) }
    DisposableEffect(coordinator) { onDispose { coordinator.dispose() } }

    // On an open iPhone Duo the tab bar only appears in the vertical bar when the tab bar
    // controller is not inside Compose, so it goes to NativeNavigationViewController's root.
    RootOrEmbeddedContainer(coordinator.tabBarController, modifier)
}

@Composable
internal actual fun <T : Any> PlatformStackTabDisplay(
    tabs: List<TabLabel>,
    entries: List<NavEntry<T>>,
    parts: TabParts,
    onTabSelected: (index: Int) -> Unit,
    onBack: () -> Unit,
    options: NavDisplayOptions<T>,
    tabBar: @Composable () -> Unit,
    modifier: Modifier,
) {
    // Only the selected tab is on screen, so a native pop always removes the last entries of the
    // back stack, whichever tab reports it.
    PlatformTabDisplay(
        tabs = tabs,
        entries = parts.ranges.map { range -> range?.let { entries.subList(it.first, it.last + 1) } ?: emptyList() },
        selectedTab = parts.selected,
        onTabSelected = onTabSelected,
        onBack = { onBack() },
        options = options,
        tabBar = tabBar,
        modifier = modifier,
    )
}
