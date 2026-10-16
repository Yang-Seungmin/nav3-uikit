package dev.seungmin.nav3.sample

import androidx.compose.runtime.Composable
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNumber
import platform.Foundation.NSSelectorFromString
import platform.Foundation.valueForKey

// UITraitCollection.verticalBarEdge is iOS 27.1 API, newer than the SDK Kotlin/Native binds, so
// it is read through key-value coding.
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun verticalBarEdge(): String? {
    val traits = LocalUIViewController.current.traitCollection
    if (!traits.respondsToSelector(NSSelectorFromString("verticalBarEdge"))) return null
    return when ((traits.valueForKey("verticalBarEdge") as? NSNumber)?.longValue) {
        1L -> "leading"
        2L -> "trailing"
        else -> null
    }
}
