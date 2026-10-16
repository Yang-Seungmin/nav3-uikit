package dev.seungmin.nav3.uikit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.uikit.ComposeUIViewControllerConfiguration
import androidx.compose.ui.window.ComposeUIViewController
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.removeFromParentViewController
import platform.UIKit.willMoveToParentViewController
/**
 * Root view controller for an app whose screens are shown by [NativeTabDisplay] or
 * [NativeNavDisplay]. Use it in place of `ComposeUIViewController`.
 *
 * One [NativeTabDisplay] at a time does not embed its `UITabBarController` in Compose. The tab bar
 * controller becomes a child of this root, above the Compose view, and fills the screen; the
 * display's `modifier` is ignored, and Compose content drawn outside the display is covered. That
 * is the first tab display composed in [content], and when it leaves composition, the tab display
 * that has been waiting longest takes its place. A tab bar controller embedded in Compose works on
 * most iPhones, but on an open iPhone Duo the system does not show its tab bar in the vertical bar.
 * [NativeNavDisplay], and every display when this root is not used, is embedded through UIKit
 * interop, so Compose content around it stays visible.
 *
 * [configure] works as in `ComposeUIViewController`. It configures the root's Compose view and
 * also the controller that hosts each entry of the displays inside it.
 */
@OptIn(ExperimentalForeignApi::class)
public fun NativeNavigationViewController(
    configure: ComposeUIViewControllerConfiguration.() -> Unit = {},
    content: @Composable () -> Unit,
): UIViewController {
    val root = UIViewController()
    val host = NativeRootHost(root, configure)
    // The content lambda reaches the root only through the host's weak reference, so the root,
    // its Compose child, and this lambda do not form a cycle through Objective-C.
    val compose = ComposeUIViewController(configure) {
        CompositionLocalProvider(LocalNativeRootHost provides host) { content() }
    }
    root.addFullScreenChild(compose)
    return root
}

internal val LocalNativeRootHost = staticCompositionLocalOf<NativeRootHost?> { null }

/**
 * Lets one native container at a time sit directly in the root, above the Compose view.
 *
 * UIKit objects are compared with `==` (`isEqual:`), not `===`. The root is only held weakly, and
 * Kotlin/Native can hand out a new Kotlin reference for the same Objective-C object, so `===` can
 * be false for the root controller itself.
 */
@OptIn(ExperimentalNativeApi::class)
internal class NativeRootHost(
    root: UIViewController,
    val configure: ComposeUIViewControllerConfiguration.() -> Unit,
) {
    private val root = WeakReference(root)
    private var owner: RootClaim? = null
    private val waiting = mutableListOf<RootClaim>()

    /** Gives the root to [claim] now if it is free; otherwise [claim] waits for it. */
    fun request(claim: RootClaim): Boolean {
        if (owner == null) {
            owner = claim
            return true
        }
        waiting += claim
        return false
    }

    fun attach(claim: RootClaim) {
        if (owner !== claim) return
        val controller = claim.controller
        val root = root.value ?: return
        if (controller.parentViewController == root) return
        // A container that was embedded until now leaves its wrapper; Compose removes the empty
        // wrapper on its own.
        controller.removeFromParent()
        root.addFullScreenChild(controller)
    }

    fun release(claim: RootClaim) {
        waiting.remove(claim)
        if (owner !== claim) return
        owner = null
        val controller = claim.controller
        if (controller.parentViewController == root.value) controller.removeFromParent()
        val next = waiting.removeFirstOrNull() ?: return
        owner = next
        next.placement = RootPlacement.Root
    }
}

internal enum class RootPlacement {
    /** Deciding; nothing is shown until the end of the current composition pass. */
    Pending,

    /** The container sits in the root of [NativeNavigationViewController]. */
    Root,

    /** The container is embedded in Compose through UIKit interop. */
    Embedded,
}

/**
 * Shows [controller] in the root of [NativeNavigationViewController] when one is available,
 * otherwise in Compose. A display that finds the root taken embeds its container and moves it to
 * the root once the current owner leaves composition.
 *
 * An embedded container sits in a wrapper that this library owns, and Compose only ever holds the
 * wrapper. Moving the container to the root is then one synchronous step that does not wait for
 * Compose to remove the wrapper.
 */
@Composable
internal fun RootOrEmbeddedContainer(controller: UIViewController, modifier: Modifier) {
    val host = LocalNativeRootHost.current
    if (host == null) {
        EmbeddedContainer(controller, modifier)
        return
    }
    val claim = remember(host, controller) { RootClaim(host, controller) }
    when (claim.placement) {
        RootPlacement.Pending -> Unit
        RootPlacement.Root -> DisposableEffect(claim) {
            host.attach(claim)
            onDispose {}
        }
        RootPlacement.Embedded -> EmbeddedContainer(claim.wrapper, modifier)
    }
}

internal class RootClaim(
    private val host: NativeRootHost,
    val controller: UIViewController,
) : RememberObserver {
    var placement by mutableStateOf(RootPlacement.Pending)

    /** Holds [controller] while it is embedded. */
    val wrapper: UIViewController by lazy {
        controller.removeFromParent()
        EmbeddedContainerController().also { it.addFullScreenChild(controller) }
    }

    init {
        // The root is usually free on first composition. When it is not, the owner may be a
        // display leaving in this same pass. Compose sends onForgotten before onRemembered, so
        // this claim usually gets the root before it would embed; if not, it embeds for a frame
        // and then moves.
        if (host.request(this)) placement = RootPlacement.Root
    }

    override fun onRemembered() {
        if (placement == RootPlacement.Pending) placement = RootPlacement.Embedded
    }

    override fun onForgotten() = host.release(this)

    override fun onAbandoned() = host.release(this)
}

/** A plain container, distinct from the root controller, which is a `UIViewController` itself. */
private class EmbeddedContainerController : UIViewController(nibName = null, bundle = null)

private fun UIViewController.removeFromParent() {
    if (parentViewController == null) return
    willMoveToParentViewController(null)
    view.removeFromSuperview()
    removeFromParentViewController()
}

@OptIn(ExperimentalForeignApi::class)
private fun UIViewController.addFullScreenChild(child: UIViewController) {
    addChildViewController(child)
    child.view.setFrame(view.bounds)
    child.view.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
    view.addSubview(child.view)
    child.didMoveToParentViewController(this)
}
