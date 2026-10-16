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
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

private const val ATTACH_RETRY_NANOS = 16_000_000L

/**
 * Root view controller for an app whose screens are shown by [NativeTabDisplay] or
 * [NativeNavDisplay]. Use it in place of `ComposeUIViewController`.
 *
 * One display at a time does not embed its native container in Compose. Its `UITabBarController`
 * or `UINavigationController` becomes a child of this root, next to the Compose view, and fills
 * the screen; the display's `modifier` is ignored. That is the first display composed in
 * [content], and when it leaves composition, the display that has been waiting longest takes its
 * place, so swapping one root display for another (a sign-in flow for the main tabs, say) keeps
 * the new container in the root. A native container embedded in Compose works on most iPhones,
 * but on an open iPhone Duo the system does not show its tab bar in the vertical bar. Other
 * displays, and every display when this root is not used, are embedded through UIKit interop.
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

/** Lets one native container at a time sit directly in the root, above the Compose view. */
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

    fun attach(claim: RootClaim, controller: UIViewController) {
        if (owner !== claim) return
        val root = root.value ?: return
        val parent = controller.parentViewController
        if (parent === root) return
        if (parent != null) {
            // The container was embedded until now. Compose removes it from its interop parent
            // in a transaction at its next render; moving it before that would let the removal
            // take it out of the root again.
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, ATTACH_RETRY_NANOS), dispatch_get_main_queue()) {
                attach(claim, controller)
            }
            return
        }
        root.addFullScreenChild(controller)
    }

    fun release(claim: RootClaim, controller: UIViewController) {
        waiting.remove(claim)
        if (owner !== claim) return
        owner = null
        if (controller.parentViewController === root.value) {
            controller.willMoveToParentViewController(null)
            controller.view.removeFromSuperview()
            controller.removeFromParentViewController()
        }
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
 * Where [controller] goes: the root of [NativeNavigationViewController] when one is available,
 * otherwise Compose. A display that finds the root taken embeds its container and moves it to the
 * root once the current owner leaves composition.
 */
@Composable
internal fun rememberRootPlacement(controller: UIViewController): RootPlacement {
    val host = LocalNativeRootHost.current ?: return RootPlacement.Embedded
    val claim = remember(host, controller) { RootClaim(host, controller) }
    if (claim.placement == RootPlacement.Root) {
        DisposableEffect(claim) {
            host.attach(claim, controller)
            onDispose {}
        }
    }
    return claim.placement
}

internal class RootClaim(
    private val host: NativeRootHost,
    private val controller: UIViewController,
) : RememberObserver {
    var placement by mutableStateOf(RootPlacement.Pending)

    init {
        // The root is usually free on first composition. When it is not, the owner may be a
        // display leaving in this same pass; Compose sends onForgotten before onRemembered, so
        // onRemembered can tell whether this claim got the root.
        if (host.request(this)) placement = RootPlacement.Root
    }

    override fun onRemembered() {
        if (placement == RootPlacement.Pending) placement = RootPlacement.Embedded
    }

    override fun onForgotten() = host.release(this, controller)

    override fun onAbandoned() = host.release(this, controller)
}

@OptIn(ExperimentalForeignApi::class)
private fun UIViewController.addFullScreenChild(child: UIViewController) {
    addChildViewController(child)
    child.view.setFrame(view.bounds)
    child.view.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
    view.addSubview(child.view)
    child.didMoveToParentViewController(this)
}
