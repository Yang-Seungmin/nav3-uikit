package dev.seungmin.nav3.uikit

import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.ui.uikit.ComposeUIViewControllerConfiguration
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.get
import androidx.navigation3.scene.DialogSceneStrategy.Companion.DialogKey
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.UIKit.UIAction
import platform.UIKit.UINavigationController
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.navigationItem
import platform.Foundation.NSSelectorFromString
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Keeps a [UINavigationController] (plus a chain of presented sheets and dialogs) in sync with the
 * decorated Navigation 3 entries.
 *
 * Direction 1, back stack to UIKit: [setEntries] diffs the entries against the hosted controllers
 * by `contentKey` and position, keeps the common prefix, and replaces the rest.
 *
 * Direction 2, UIKit to back stack: when UIKit removes controllers on its own (swipe-back, the
 * back button, dragging a sheet down), the hosts are dropped and [onBack] is called once per
 * removed entry. The resulting back stack change then diffs to a no-op. When the top entry's
 * content has an enabled back handler, UIKit is not allowed to pop it; the back action goes to
 * that handler instead, as it would under `NavDisplay`.
 */
internal class UIKitNavigationCoordinator<T : Any>(
    parentLocals: CompositionLocalContext,
    configure: ComposeUIViewControllerConfiguration.() -> Unit,
    private val onBack: () -> Unit,
) {
    val navigationController = UINavigationController()

    val parentLocals = ParentLocals(parentLocals)

    private val environment = HostEnvironment(this.parentLocals, configure, weakBack(this), weakBackHandlersChanged(this))

    private val stack = mutableListOf<EntryHost<T>>()
    private val modals = mutableListOf<EntryHost<T>>()

    /** Hosts being animated out; their content stays composed until the transition ends. */
    private val retiring = mutableListOf<EntryHost<T>>()

    private var latestEntries: List<NavEntry<T>> = emptyList()
    private var modalTransitionInFlight = false
    private var modalRetryScheduled = false
    private var hasApplied = false
    private var disposed = false

    // UIKit holds delegates weakly, so the coordinator keeps them alive.
    private val navigationDelegate = NavigationDelegate(this)

    init {
        navigationController.delegate = navigationDelegate
        navigationController.navigationBar.prefersLargeTitles = true
    }

    fun setEntries(entries: List<NavEntry<T>>, showsDialogs: Boolean) {
        environment.showsDialogs = showsDialogs
        setEntries(entries)
    }

    private fun setEntries(entries: List<NavEntry<T>>) {
        if (disposed) return
        latestEntries = entries
        if (modalTransitionInFlight) return // re-applied when the transition completes

        val firstModal = entries.indexOfFirst { it.isModal() }.let { if (it == -1) entries.size else it }
        // The root entry is always pushed; there is nothing to present a modal from otherwise.
        val pushCount = if (entries.isEmpty()) 0 else maxOf(firstModal, 1)
        val animated = hasApplied && navigationController.view.window != null

        syncStack(entries.subList(0, pushCount), animated)
        syncModals(entries.subList(pushCount, entries.size), animated)
        updatePopGestures()
        hasApplied = true
    }

    fun dispose() {
        disposed = true
        navigationController.delegate = null
        (stack + modals + retiring).forEach { it.retire() }
        stack.clear()
        modals.clear()
        retiring.clear()
    }

    private fun syncStack(entries: List<NavEntry<T>>, animated: Boolean) {
        val common = commonPrefix(stack, entries)
        for (i in 0 until common) stack[i].update(entries[i])
        if (common == stack.size && common == entries.size) return

        val oldTop = stack.lastOrNull()
        val removed = stack.drop(common)
        stack.subList(common, stack.size).clear()
        entries.drop(common).forEach { stack += EntryHost(it, environment) }
        // UIKit shows a back button for any item with a back action, even the root.
        stack.forEachIndexed { index, host ->
            val item = host.viewController.navigationItem
            if (index == 0) {
                item.backAction = null
            } else if (item.backAction == null) {
                item.backAction = backAction(this, host)
            }
        }

        // Only a change of the top controller produces a transition (and a didShow callback).
        val transition = animated && stack.lastOrNull() !== oldTop
        navigationController.setViewControllers(stack.map { it.viewController }, transition)
        if (transition) retiring += removed else removed.forEach { it.retire() }
    }

    private fun syncModals(entries: List<NavEntry<T>>, animated: Boolean) {
        val common = commonPrefix(modals, entries)
        for (i in 0 until common) modals[i].update(entries[i])

        if (common < modals.size) {
            // Dismissing the presenter's child dismisses everything presented above it too.
            val presenter = presenterFor(common)
            val removed = modals.drop(common)
            modals.subList(common, modals.size).clear()
            modalTransitionInFlight = true
            presenter.dismissViewControllerAnimated(animated) {
                removed.forEach { it.retire() }
                modalTransitionInFlight = false
                setEntries(latestEntries)
            }
            return
        }

        if (common < entries.size) {
            val presenter = presenterFor(common)
            if (presenter.view.window == null) {
                // Not attached yet (e.g. the initial back stack ends with a sheet). Retry on the
                // next main-loop turn.
                scheduleModalRetry()
                return
            }
            val host = EntryHost(entries[common], environment)
            host.configureAsModal(this)
            modals += host
            modalTransitionInFlight = true
            presenter.presentViewController(host.viewController, animated) {
                modalTransitionInFlight = false
                setEntries(latestEntries) // presents the next modal, if any
            }
        }
    }

    private fun presenterFor(modalIndex: Int): UIViewController =
        if (modalIndex == 0) navigationController else modals[modalIndex - 1].viewController

    private fun scheduleModalRetry() {
        if (modalRetryScheduled) return
        modalRetryScheduled = true
        dispatch_async(dispatch_get_main_queue()) {
            modalRetryScheduled = false
            setEntries(latestEntries)
        }
    }

    fun onWillShow(viewController: UIViewController) {
        val host = stack.firstOrNull { it.viewController === viewController } ?: return
        navigationController.setNavigationBarHidden(host.navigationBarHidden, animated = true)
    }

    fun onDidShow() {
        val nativeCount = navigationController.viewControllers.size
        if (nativeCount < stack.size) {
            // UIKit popped on its own: swipe-back, the back button, or its long-press menu.
            val popped = stack.drop(nativeCount)
            stack.subList(nativeCount, stack.size).clear()
            popped.forEach { it.retire() }
            repeat(popped.size) { onBack() }
        }
        retiring.forEach { it.retire() }
        retiring.clear()
        updatePopGestures()
    }

    fun onSheetDismissedByUser(host: EntryHost<T>) {
        val index = modals.indexOf(host)
        if (index == -1) return
        val removed = modals.drop(index)
        modals.subList(index, modals.size).clear()
        removed.forEach { it.retire() }
        repeat(removed.size) { onBack() }
    }

    /**
     * UIKit's swipe-back gestures stay off while the top entry handles back itself. Compose's own
     * edge gesture then delivers the swipe to that handler.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun updatePopGestures() {
        if (disposed) return
        val enabled = stack.lastOrNull()?.interceptsBack != true
        navigationController.interactivePopGestureRecognizer?.enabled = enabled
        // The full-width content swipe exists from iOS 26.
        if (navigationController.respondsToSelector(NSSelectorFromString("interactiveContentPopGestureRecognizer"))) {
            navigationController.interactiveContentPopGestureRecognizer?.enabled = enabled
        }
    }

    fun onBackButton(host: EntryHost<T>) {
        if (host.interceptsBack) {
            host.dispatchBack()
        } else {
            navigationController.popViewControllerAnimated(true)
        }
    }

    /** A dialog asked to close (scrim tap): the app pops it like any other entry. */
    fun requestBack() {
        if (!disposed) onBack()
    }

    private fun commonPrefix(hosts: List<EntryHost<T>>, entries: List<NavEntry<T>>): Int {
        var i = 0
        while (i < hosts.size && i < entries.size && hosts[i].contentKey == entries[i].contentKey) i++
        return i
    }

    private fun NavEntry<T>.isModal(): Boolean =
        isSheet() || (environment.showsDialogs && metadata[DialogKey] != null)
}

// Top-level functions so the blocks UIKit and hosted content keep capture only weak references.
@OptIn(ExperimentalNativeApi::class)
private fun weakBack(coordinator: UIKitNavigationCoordinator<*>): () -> Unit {
    val weak = WeakReference(coordinator)
    return { weak.value?.requestBack() }
}

@OptIn(ExperimentalNativeApi::class)
private fun weakBackHandlersChanged(coordinator: UIKitNavigationCoordinator<*>): () -> Unit {
    val weak = WeakReference(coordinator)
    return { weak.value?.updatePopGestures() }
}

@OptIn(ExperimentalNativeApi::class)
private fun <T : Any> backAction(coordinator: UIKitNavigationCoordinator<T>, host: EntryHost<T>): UIAction {
    val weakCoordinator = WeakReference(coordinator)
    val weakHost = WeakReference(host)
    return UIAction.actionWithHandler { _ ->
        val h = weakHost.value
        if (h != null) weakCoordinator.value?.onBackButton(h)
    }
}

@OptIn(ExperimentalNativeApi::class)
private class NavigationDelegate(
    coordinator: UIKitNavigationCoordinator<*>,
) : NSObject(), UINavigationControllerDelegateProtocol {
    private val coordinator = WeakReference(coordinator)

    @ObjCSignatureOverride
    override fun navigationController(
        navigationController: UINavigationController,
        willShowViewController: UIViewController,
        animated: Boolean,
    ) {
        coordinator.value?.onWillShow(willShowViewController)
    }

    @ObjCSignatureOverride
    override fun navigationController(
        navigationController: UINavigationController,
        didShowViewController: UIViewController,
        animated: Boolean,
    ) {
        coordinator.value?.onDidShow()
    }
}
