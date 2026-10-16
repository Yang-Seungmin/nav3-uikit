package dev.seungmin.nav3.uikit

import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.ui.uikit.ComposeUIViewControllerConfiguration
import androidx.navigation3.runtime.NavEntry
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlinx.cinterop.ObjCSignatureOverride
import platform.UIKit.UIImage
import platform.UIKit.UITab
import platform.UIKit.UITabBarController
import platform.UIKit.UITabBarControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/**
 * Keeps a [UITabBarController] in sync with the tabs of [NativeTabDisplay]. Each tab is a
 * [UIKitNavigationCoordinator], so pushes, pops, and sheets inside a tab work exactly as in
 * [NativeNavDisplay].
 *
 * Selection follows the same two directions as the back stack: [update] selects the tab the
 * caller asks for, and a tap in the tab bar reports the new index through [onTabSelected].
 */
internal class UIKitTabCoordinator<T : Any>(
    private val configure: ComposeUIViewControllerConfiguration.() -> Unit,
    private val onTabSelected: (index: Int) -> Unit,
    private val onBack: (tab: Int) -> Unit,
) {
    val tabBarController = UITabBarController()

    private val navigators = mutableListOf<UIKitNavigationCoordinator<T>>()
    private val uiTabs = mutableListOf<UITab>()
    private val icons = mutableListOf<String?>()
    private var disposed = false

    // UIKit holds the delegate weakly, so the coordinator keeps it alive.
    private val delegate = TabBarDelegate(this)

    init {
        tabBarController.delegate = delegate
    }

    fun update(
        tabs: List<NativeTab<T>>,
        entries: List<List<NavEntry<T>>>,
        selectedTab: Int,
        parentLocals: CompositionLocalContext,
        showsDialogs: Boolean,
    ) {
        if (disposed) return
        if (tabs.size != navigators.size) rebuild(tabs, parentLocals)

        tabs.forEachIndexed { index, tab ->
            val uiTab = uiTabs[index]
            if (uiTab.title != tab.title) uiTab.title = tab.title
            if (icons[index] != tab.icon) {
                icons[index] = tab.icon
                uiTab.image = UIImage.systemImageNamed(tab.icon)
            }

            val navigator = navigators[index]
            navigator.parentLocals.value = parentLocals
            navigator.setEntries(entries[index], showsDialogs)
        }

        val wanted = uiTabs[selectedTab]
        if (tabBarController.selectedTab !== wanted) tabBarController.selectedTab = wanted
    }

    fun dispose() {
        disposed = true
        tabBarController.delegate = null
        navigators.forEach { it.dispose() }
        navigators.clear()
    }

    fun onUserSelected(tab: UITab) {
        val index = uiTabs.indexOfFirst { it.identifier == tab.identifier }
        if (index != -1) onTabSelected(index)
    }

    private fun rebuild(tabs: List<NativeTab<T>>, parentLocals: CompositionLocalContext) {
        navigators.forEach { it.dispose() }
        navigators.clear()
        uiTabs.clear()
        icons.clear()
        val onBack = onBack // the navigators must not capture this coordinator
        tabs.forEachIndexed { index, tab ->
            val navigator = UIKitNavigationCoordinator<T>(parentLocals, configure) { onBack(index) }
            navigators += navigator
            uiTabs += tabFor(index, tab, navigator.navigationController)
            icons += tab.icon
        }
        tabBarController.setTabs(uiTabs.toList(), animated = false)
    }
}

// A top-level function so the provider block, which UIKit keeps, captures only the controller.
private fun tabFor(index: Int, tab: NativeTab<*>, controller: UIViewController): UITab =
    UITab(tab.title, UIImage.systemImageNamed(tab.icon), "tab-$index") { controller }

@OptIn(ExperimentalNativeApi::class)
private class TabBarDelegate(
    coordinator: UIKitTabCoordinator<*>,
) : NSObject(), UITabBarControllerDelegateProtocol {
    private val coordinator = WeakReference(coordinator)

    @ObjCSignatureOverride
    override fun tabBarController(
        tabBarController: UITabBarController,
        didSelectTab: UITab,
        previousTab: UITab?,
    ) {
        coordinator.value?.onUserSelected(didSelectTab)
    }
}
