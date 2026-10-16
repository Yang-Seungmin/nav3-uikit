package dev.seungmin.nav3.uikit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.ui.uikit.ComposeUIViewControllerConfiguration
import androidx.navigation3.runtime.NavEntry
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlinx.cinterop.ObjCSignatureOverride
import platform.UIKit.UITab
import platform.UIKit.UITabBarController
import platform.UIKit.UITabBarControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/**
 * Keeps a [UITabBarController] in sync with the tabs of [NativeTabDisplay]. Each tab is a
 * [UIKitNavigationCoordinator], so pushes, pops, sheets, and overlays inside a tab work exactly as
 * in [NativeNavDisplay].
 *
 * Selection follows the same two directions as the back stack: [update] selects the tab the
 * caller asks for, and a tap in the tab bar reports the new index through [onTabSelected]. After
 * either kind of native change, [onNativeChange] makes the display call [update] again, which
 * undoes a change the caller did not follow.
 */
internal class UIKitTabCoordinator<T : Any>(
    private val configure: ComposeUIViewControllerConfiguration.() -> Unit,
    private val onTabSelected: (index: Int) -> Unit,
    private val onBack: (tab: Int) -> Unit,
    private val onNativeChange: () -> Unit,
) {
    val tabBarController = UITabBarController()

    private val navigators = mutableListOf<UIKitNavigationCoordinator<T>>()
    private val uiTabs = mutableListOf<UITab>()

    /** What each tab's image was made from, so it is only redrawn when that changes. */
    private val iconSources = mutableListOf<Any?>()
    private var disposed = false

    // UIKit holds the delegate weakly, so the coordinator keeps it alive.
    private val delegate = TabBarDelegate(this)

    init {
        tabBarController.delegate = delegate
    }

    fun update(
        tabs: List<TabLabel>,
        entries: List<List<NavEntry<T>>>,
        overlays: List<Map<Any, @Composable () -> Unit>>,
        selectedTab: Int,
        parentLocals: CompositionLocalContext,
    ) {
        if (disposed) return
        if (tabs.size != navigators.size) rebuild(tabs, parentLocals)

        tabs.forEachIndexed { index, tab ->
            val uiTab = uiTabs[index]
            if (uiTab.title != tab.title) uiTab.title = tab.title
            val iconSource = tab.systemImage ?: tab.icon
            if (iconSources[index] != iconSource) {
                iconSources[index] = iconSource
                uiTab.image = tabImage(tab)
            }

            val navigator = navigators[index]
            navigator.parentLocals.value = parentLocals
            // Only the tab that stays on screen animates. UIKit leaves a tab's transition half
            // done when the tab is switched away during it, with the old screens still stacked.
            navigator.setEntries(entries[index], overlays[index], animate = index == selectedTab)
        }

        val wanted = uiTabs[selectedTab]
        if (tabBarController.selectedTab != wanted) tabBarController.selectedTab = wanted
    }

    fun dispose() {
        disposed = true
        tabBarController.delegate = null
        navigators.forEach { it.dispose() }
        navigators.clear()
    }

    /**
     * Whether UIKit may switch to [tab] itself. A tab with no entries would show an empty screen
     * until the app's next frame, so its selection is reported instead and [update] selects the
     * tab once the app gives it entries.
     */
    fun shouldSelect(tab: UITab): Boolean {
        val index = uiTabs.indexOfFirst { it.identifier == tab.identifier }
        if (index == -1 || !navigators[index].isEmpty) return true
        onTabSelected(index)
        onNativeChange()
        return false
    }

    fun onUserSelected(tab: UITab) {
        val index = uiTabs.indexOfFirst { it.identifier == tab.identifier }
        if (index == -1) return
        onTabSelected(index)
        onNativeChange()
    }

    private fun rebuild(tabs: List<TabLabel>, parentLocals: CompositionLocalContext) {
        navigators.forEach { it.dispose() }
        navigators.clear()
        uiTabs.clear()
        iconSources.clear()
        // The navigators must not capture this coordinator.
        val onBack = onBack
        val onNativeChange = onNativeChange
        tabs.forEachIndexed { index, tab ->
            val navigator = UIKitNavigationCoordinator<T>(
                parentLocals = parentLocals,
                configure = configure,
                onBack = { onBack(index) },
                onNativeChange = onNativeChange,
            )
            navigators += navigator
            uiTabs += tabFor(index, tab, navigator.navigationController)
            iconSources += tab.systemImage ?: tab.icon
        }
        tabBarController.setTabs(uiTabs.toList(), animated = false)
    }
}

// A top-level function so the provider block, which UIKit keeps, captures only the controller.
private fun tabFor(index: Int, tab: TabLabel, controller: UIViewController): UITab =
    UITab(tab.title, tabImage(tab), "tab-$index") { controller }

@OptIn(ExperimentalNativeApi::class)
private class TabBarDelegate(
    coordinator: UIKitTabCoordinator<*>,
) : NSObject(), UITabBarControllerDelegateProtocol {
    private val coordinator = WeakReference(coordinator)

    @ObjCSignatureOverride
    override fun tabBarController(tabBarController: UITabBarController, shouldSelectTab: UITab): Boolean =
        coordinator.value?.shouldSelect(shouldSelectTab) ?: true

    @ObjCSignatureOverride
    override fun tabBarController(
        tabBarController: UITabBarController,
        didSelectTab: UITab,
        previousTab: UITab?,
    ) {
        coordinator.value?.onUserSelected(didSelectTab)
    }
}
