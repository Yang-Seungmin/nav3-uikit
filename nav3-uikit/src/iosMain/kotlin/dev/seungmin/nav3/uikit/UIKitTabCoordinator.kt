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
 * The caller owns the selection, as `NavDisplay`'s caller owns the back stack: a tap on another
 * tab only reports its index through [onTabSelected], and [update] selects the tab the caller then
 * asks for. Native pops inside a tab are applied by UIKit first and reported through [onBack];
 * [onNativeChange] makes the display call [update] again, which undoes a pop the caller did not
 * follow.
 */
internal class UIKitTabCoordinator<T : Any>(
    parentLocals: CompositionLocalContext,
    private val configure: ComposeUIViewControllerConfiguration.() -> Unit,
    private val onTabSelected: (index: Int) -> Unit,
    private val onBack: (tab: Int) -> Unit,
    private val onNativeChange: () -> Unit,
) {
    val tabBarController = UITabBarController()

    /** Shared by every tab's navigator. */
    private val parentLocals = ParentLocals(parentLocals)
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
        this.parentLocals.value = parentLocals
        if (tabs.size != navigators.size) resize(tabs)

        tabs.forEachIndexed { index, tab ->
            val uiTab = uiTabs[index]
            if (uiTab.title != tab.title) uiTab.title = tab.title
            if (iconSources[index] != tab.iconSource) {
                iconSources[index] = tab.iconSource
                uiTab.image = tabImage(tab)
            }

            // Only the tab that stays on screen animates. UIKit leaves a tab's transition half
            // done when the tab is switched away during it, with the old screens still stacked.
            navigators[index].setEntries(entries[index], overlays[index], animate = index == selectedTab)
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
     * Whether UIKit may select [tab] itself. Only the selected tab may be selected again, which
     * makes UIKit pop that tab to its root. Another tab is reported to the caller and selected by
     * [update] if the caller follows.
     */
    fun shouldSelect(tab: UITab): Boolean {
        if (tab.identifier == tabBarController.selectedTab?.identifier) return true
        val index = uiTabs.indexOfFirst { it.identifier == tab.identifier }
        if (index != -1) onTabSelected(index)
        return false
    }

    /** Adds or removes tabs at the end; the tabs that stay keep their screens. */
    private fun resize(tabs: List<TabLabel>) {
        while (navigators.size > tabs.size) {
            navigators.removeLast().dispose()
            uiTabs.removeLast()
            iconSources.removeLast()
        }
        // The navigators must not capture this coordinator.
        val onBack = onBack
        val onNativeChange = onNativeChange
        for (index in navigators.size until tabs.size) {
            val tab = tabs[index]
            val navigator = UIKitNavigationCoordinator<T>(
                parentLocals = parentLocals,
                configure = configure,
                onBack = { onBack(index) },
                onNativeChange = onNativeChange,
            )
            navigators += navigator
            uiTabs += tabFor(index, tab, navigator.navigationController)
            iconSources += tab.iconSource
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
}
