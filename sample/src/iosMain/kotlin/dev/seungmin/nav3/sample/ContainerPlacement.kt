package dev.seungmin.nav3.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.coroutines.delay
import platform.UIKit.UINavigationController
import platform.UIKit.UITab
import platform.UIKit.UITabBarController
import platform.UIKit.UIViewController
import platform.UIKit.childViewControllers

/** Reads [read] from this screen's controller now and then; the containers move after composition. */
@Composable
private fun <R> pollController(read: (UIViewController) -> R): R? {
    val controller = LocalUIViewController.current
    var value by remember { mutableStateOf<R?>(null) }
    LaunchedEffect(controller) {
        while (true) {
            value = read(controller)
            delay(300)
        }
    }
    return value
}

@Composable
actual fun containerPlacement(): String? = pollController(::describePlacement)

private fun describePlacement(screen: UIViewController): String {
    var container = screen
    while (true) {
        val parent = container.parentViewController ?: return "detached"
        if (parent is UINavigationController || parent is UITabBarController) {
            container = parent
            continue
        }
        // NativeNavigationViewController's root is a plain UIViewController.
        return if (parent.isMemberOfClass(UIViewController.`class`())) "root" else "embedded"
    }
}

@Composable
actual fun nativeStackSizes(): String? = pollController(::describeStackSizes)

private fun describeStackSizes(screen: UIViewController): String? {
    var container: UIViewController? = screen
    while (container != null && container !is UITabBarController) container = container.parentViewController
    val tabs = container as? UITabBarController ?: return null
    // Tabs built from UITab objects are not listed in viewControllers.
    return tabs.tabs.joinToString(",") {
        ((it as UITab).viewController as? UINavigationController)?.viewControllers?.size?.toString() ?: "-"
    }
}

@Composable
actual fun rootChildCount(): Int? = pollController { controller ->
    var root = controller
    while (true) root = root.parentViewController ?: break
    root.childViewControllers.size
}
