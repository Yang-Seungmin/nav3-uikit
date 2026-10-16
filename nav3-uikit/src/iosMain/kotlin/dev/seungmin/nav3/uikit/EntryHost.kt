package dev.seungmin.nav3.uikit

import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.get
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIColor
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UINavigationItemLargeTitleDisplayMode
import platform.UIKit.UIPresentationController
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.UIViewController
import platform.UIKit.navigationItem
import platform.UIKit.presentationController
import platform.UIKit.sheetPresentationController
import platform.UIKit.systemBackgroundColor
import platform.darwin.NSObject

/** The locals at the NativeNavDisplay call site, shared by every hosted controller. */
internal class ParentLocals(initial: CompositionLocalContext) {
    var value by mutableStateOf(initial)
}

/**
 * One back stack entry hosted in its own Compose-backed UIViewController.
 *
 * Kotlin/Native cannot collect reference cycles that pass through Objective-C objects, so nothing
 * reachable from [viewController] or from a UIKit delegate may point back at this host or at the
 * coordinator with a strong reference. The controller's content only sees [EntryContent] and
 * [ParentLocals]; delegates hold weak references.
 */
internal class EntryHost<T : Any>(
    entry: NavEntry<T>,
    parentLocals: ParentLocals,
) {
    val contentKey: Any = entry.contentKey

    private val content = EntryContent(entry)
    private var presentationDelegate: SheetPresentationDelegate<T>? = null

    val viewController: UIViewController = hostedController(content, parentLocals)

    val isSheet: Boolean
        get() = content.entry.metadata[NativeNavigation.Presentation] is NativePresentation.Sheet

    val navigationBarHidden: Boolean
        get() = content.entry.metadata[NativeNavigation.NavigationBarHidden] == true

    init {
        viewController.view.backgroundColor = UIColor.systemBackgroundColor
        NativeNavDiagnostics.track(viewController)
        applyMetadata()
    }

    fun update(newEntry: NavEntry<T>) {
        content.entry = newEntry
        applyMetadata()
    }

    fun configureAsSheet(coordinator: UIKitNavigationCoordinator<T>) {
        val sheet = content.entry.metadata[NativeNavigation.Presentation] as? NativePresentation.Sheet
            ?: NativePresentation.Sheet()
        viewController.modalPresentationStyle = UIModalPresentationPageSheet
        viewController.sheetPresentationController?.let { controller ->
            controller.detents = sheet.detents.map {
                when (it) {
                    SheetDetent.Medium -> UISheetPresentationControllerDetent.mediumDetent()
                    SheetDetent.Large -> UISheetPresentationControllerDetent.largeDetent()
                }
            }
            controller.prefersGrabberVisible = sheet.grabberVisible
        }
        // UIKit holds presentation delegates weakly, so this host keeps it alive.
        val delegate = SheetPresentationDelegate(this, coordinator)
        presentationDelegate = delegate
        viewController.presentationController?.delegate = delegate
    }

    /** Removes the entry's content from composition. Call once the controller is off screen. */
    fun retire() {
        content.active = false
    }

    private fun applyMetadata() {
        val metadata = content.entry.metadata
        viewController.title = metadata[NativeNavigation.Title]
        viewController.navigationItem.largeTitleDisplayMode =
            if (metadata[NativeNavigation.LargeTitle] == true) {
                UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeAlways
            } else {
                UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeNever
            }
    }
}

private class EntryContent<T : Any>(entry: NavEntry<T>) {
    var entry by mutableStateOf(entry)
    var active by mutableStateOf(true)
}

// A top-level function so the content lambda cannot capture the EntryHost.
private fun <T : Any> hostedController(
    content: EntryContent<T>,
    parentLocals: ParentLocals,
): UIViewController = ComposeUIViewController {
    // Each UIViewController is a separate composition. Re-provide the locals from where
    // NativeNavDisplay was called (theme, DI, ...), then restore this controller's own
    // platform locals (lifecycle, density, UIViewController, ...) on top of them.
    val ownLocals = currentCompositionLocalContext
    CompositionLocalProvider(parentLocals.value) {
        CompositionLocalProvider(ownLocals) {
            // Leaving composition here is what lets Navigation 3 run the decorators' onPop.
            if (content.active) content.entry.Content()
        }
    }
}

@OptIn(ExperimentalNativeApi::class)
private class SheetPresentationDelegate<T : Any>(
    host: EntryHost<T>,
    coordinator: UIKitNavigationCoordinator<T>,
) : NSObject(), UIAdaptivePresentationControllerDelegateProtocol {
    private val host = WeakReference(host)
    private val coordinator = WeakReference(coordinator)

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
        val host = host.value ?: return
        coordinator.value?.onSheetDismissedByUser(host)
    }
}
