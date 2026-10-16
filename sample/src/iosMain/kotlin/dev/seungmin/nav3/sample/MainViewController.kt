package dev.seungmin.nav3.sample

import androidx.compose.ui.window.ComposeUIViewController
import dev.seungmin.nav3.uikit.NativeNavDiagnostics
import dev.seungmin.nav3.uikit.NativeNavigationViewController
import platform.Foundation.NSLog
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIViewController

actual fun platformLog(message: String) {
    NSLog(message.replace("%", "%%"))
}

// NAV3_HOST=interop embeds the native containers in Compose instead of hoisting them to the root.
fun MainViewController(): UIViewController = when (launchFlag("NAV3_HOST")) {
    "interop" -> ComposeUIViewController { App() }
    else -> NativeNavigationViewController { App() }
}

actual suspend fun liveViewControllerCount(): Int? = NativeNavDiagnostics.liveViewControllerCount()

actual fun launchFlag(name: String): String? =
    NSProcessInfo.processInfo.environment[name] as? String

