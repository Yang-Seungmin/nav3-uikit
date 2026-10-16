package dev.seungmin.nav3.uikit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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
 * The first of those displays composed in [content] does not embed its native container in
 * Compose. Its `UITabBarController` or `UINavigationController` becomes a child of this root,
 * next to the Compose view, and fills the screen; the display's `modifier` is ignored. A native
 * container embedded in Compose works on most iPhones, but on an open iPhone Duo the system does
 * not show its tab bar in the vertical bar. Other displays, and every display when this root is
 * not used, are embedded through UIKit interop as before.
 */
@OptIn(ExperimentalForeignApi::class)
public fun NativeNavigationViewController(content: @Composable () -> Unit): UIViewController {
    val root = UIViewController()
    val host = NativeRootHost(root)
    // The content lambda reaches the root only through the host's weak reference, so the root,
    // its Compose child, and this lambda do not form a cycle through Objective-C.
    val compose = ComposeUIViewController {
        CompositionLocalProvider(LocalNativeRootHost provides host) { content() }
    }
    root.addFullScreenChild(compose)
    return root
}

internal val LocalNativeRootHost = staticCompositionLocalOf<NativeRootHost?> { null }

/** Lets one native container at a time sit directly in the root, above the Compose view. */
@OptIn(ExperimentalNativeApi::class)
internal class NativeRootHost(root: UIViewController) {
    private val root = WeakReference(root)
    private var owner: Any? = null

    fun reserve(owner: Any): Boolean {
        if (this.owner != null) return false
        this.owner = owner
        return true
    }

    fun attach(controller: UIViewController) {
        root.value?.addFullScreenChild(controller)
    }

    fun release(owner: Any, controller: UIViewController) {
        if (this.owner !== owner) return
        this.owner = null
        if (controller.parentViewController != null) {
            controller.willMoveToParentViewController(null)
            controller.view.removeFromSuperview()
            controller.removeFromParentViewController()
        }
    }
}

/**
 * Places [controller] in the root of [NativeNavigationViewController] when one is available and
 * not yet taken. Returns whether it did; if not, the caller embeds the controller in Compose.
 */
@Composable
internal fun rememberHoistedToRoot(controller: UIViewController): Boolean {
    val host = LocalNativeRootHost.current ?: return false
    return remember(host, controller) { RootClaim(host, controller) }.granted
}

private class RootClaim(
    private val host: NativeRootHost,
    private val controller: UIViewController,
) : RememberObserver {
    val granted = host.reserve(this)

    override fun onRemembered() {
        if (granted) host.attach(controller)
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
