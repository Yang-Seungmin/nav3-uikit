package dev.seungmin.nav3.uikit

import androidx.compose.runtime.CompositionLocalContext
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.get
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlinx.cinterop.ObjCSignatureOverride
import platform.UIKit.UINavigationController
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Keeps a [UINavigationController] (plus a chain of presented sheets) in sync with the decorated
 * Navigation 3 entries.
 *
 * Direction 1, back stack to UIKit: [setEntries] diffs the entries against the hosted controllers
 * by `contentKey` and position, keeps the common prefix, and replaces the rest.
 *
 * Direction 2, UIKit to back stack: when UIKit removes controllers on its own (swipe-back, the
 * back button, dragging a sheet down), the hosts are dropped and [onBack] is called once per
 * removed entry. The resulting back stack change then diffs to a no-op.
 */
internal class UIKitNavigationCoordinator<T : Any>(
    parentLocals: CompositionLocalContext,
    private val onBack: () -> Unit,
) {
    val navigationController = UINavigationController()

    val parentLocals = ParentLocals(parentLocals)

    private val stack = mutableListOf<EntryHost<T>>()
    private val sheets = mutableListOf<EntryHost<T>>()

    /** Hosts being animated out; their content stays composed until the transition ends. */
    private val retiring = mutableListOf<EntryHost<T>>()

    private var latestEntries: List<NavEntry<T>> = emptyList()
    private var sheetTransitionInFlight = false
    private var sheetRetryScheduled = false
    private var hasApplied = false
    private var disposed = false

    // UIKit holds the delegate weakly, so the coordinator keeps it alive.
    private val navigationDelegate = NavigationDelegate(this)

    init {
        navigationController.delegate = navigationDelegate
        navigationController.navigationBar.prefersLargeTitles = true
    }

    fun setEntries(entries: List<NavEntry<T>>) {
        if (disposed) return
        latestEntries = entries
        if (sheetTransitionInFlight) return // re-applied when the transition completes

        val firstSheet = entries.indexOfFirst { it.isSheet() }.let { if (it == -1) entries.size else it }
        // The root entry is always pushed; there is nothing to present a sheet from otherwise.
        val pushCount = if (entries.isEmpty()) 0 else maxOf(firstSheet, 1)
        val animated = hasApplied && navigationController.view.window != null

        syncStack(entries.subList(0, pushCount), animated)
        syncSheets(entries.subList(pushCount, entries.size), animated)
        hasApplied = true
    }

    fun dispose() {
        disposed = true
        navigationController.delegate = null
        (stack + sheets + retiring).forEach { it.retire() }
        stack.clear()
        sheets.clear()
        retiring.clear()
    }

    private fun syncStack(entries: List<NavEntry<T>>, animated: Boolean) {
        val common = commonPrefix(stack, entries)
        for (i in 0 until common) stack[i].update(entries[i])
        if (common == stack.size && common == entries.size) return

        val oldTop = stack.lastOrNull()
        val removed = stack.drop(common)
        stack.subList(common, stack.size).clear()
        entries.drop(common).forEach { stack += EntryHost(it, parentLocals) }

        // Only a change of the top controller produces a transition (and a didShow callback).
        val transition = animated && stack.lastOrNull() !== oldTop
        navigationController.setViewControllers(stack.map { it.viewController }, transition)
        if (transition) retiring += removed else removed.forEach { it.retire() }
    }

    private fun syncSheets(entries: List<NavEntry<T>>, animated: Boolean) {
        val common = commonPrefix(sheets, entries)
        for (i in 0 until common) sheets[i].update(entries[i])

        if (common < sheets.size) {
            // Dismissing the presenter's child dismisses everything presented above it too.
            val presenter = presenterFor(common)
            val removed = sheets.drop(common)
            sheets.subList(common, sheets.size).clear()
            sheetTransitionInFlight = true
            presenter.dismissViewControllerAnimated(animated) {
                removed.forEach { it.retire() }
                sheetTransitionInFlight = false
                setEntries(latestEntries)
            }
            return
        }

        if (common < entries.size) {
            val presenter = presenterFor(common)
            if (presenter.view.window == null) {
                // Not attached yet (e.g. the initial back stack ends with a sheet). Retry on the
                // next main-loop turn.
                scheduleSheetRetry()
                return
            }
            val host = EntryHost(entries[common], parentLocals)
            host.configureAsSheet(this)
            sheets += host
            sheetTransitionInFlight = true
            presenter.presentViewController(host.viewController, animated) {
                sheetTransitionInFlight = false
                setEntries(latestEntries) // presents the next sheet, if any
            }
        }
    }

    private fun presenterFor(sheetIndex: Int): UIViewController =
        if (sheetIndex == 0) navigationController else sheets[sheetIndex - 1].viewController

    private fun scheduleSheetRetry() {
        if (sheetRetryScheduled) return
        sheetRetryScheduled = true
        dispatch_async(dispatch_get_main_queue()) {
            sheetRetryScheduled = false
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
    }

    fun onSheetDismissedByUser(host: EntryHost<T>) {
        val index = sheets.indexOf(host)
        if (index == -1) return
        val removed = sheets.drop(index)
        sheets.subList(index, sheets.size).clear()
        removed.forEach { it.retire() }
        repeat(removed.size) { onBack() }
    }

    private fun commonPrefix(hosts: List<EntryHost<T>>, entries: List<NavEntry<T>>): Int {
        var i = 0
        while (i < hosts.size && i < entries.size && hosts[i].contentKey == entries[i].contentKey) i++
        return i
    }

    private fun NavEntry<T>.isSheet(): Boolean =
        metadata[NativeNavigation.Presentation] is NativePresentation.Sheet
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
