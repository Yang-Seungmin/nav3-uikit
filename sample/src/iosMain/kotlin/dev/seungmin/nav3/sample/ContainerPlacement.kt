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
import platform.UIKit.UITabBarController
import platform.UIKit.UIViewController

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
