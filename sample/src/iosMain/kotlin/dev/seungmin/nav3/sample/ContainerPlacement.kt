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

@Composable
actual fun containerPlacement(): String? {
    val controller = LocalUIViewController.current
    var placement by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(controller) {
        // The containers move after composition, so keep reading.
        while (true) {
            placement = describePlacement(controller)
            delay(300)
        }
    }
    return placement
}

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
actual fun nativeStackSizes(): String? {
    val controller = LocalUIViewController.current
    var sizes by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(controller) {
        while (true) {
            sizes = describeStackSizes(controller)
            delay(300)
        }
    }
    return sizes
}

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
actual fun rootChildCount(): Int? {
    val controller = LocalUIViewController.current
    var count by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(controller) {
        while (true) {
            var root = controller
            while (true) root = root.parentViewController ?: break
            count = root.childViewControllers.size
            delay(300)
        }
    }
    return count
}
