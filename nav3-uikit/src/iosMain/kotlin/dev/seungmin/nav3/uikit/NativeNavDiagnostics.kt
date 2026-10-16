package dev.seungmin.nav3.uikit

import kotlinx.coroutines.delay
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import platform.UIKit.UIViewController

/** Debug helpers for checking that hosted controllers are released after they are popped. */
@OptIn(ExperimentalNativeApi::class, NativeRuntimeApi::class)
public object NativeNavDiagnostics {
    private val hosted = mutableListOf<WeakReference<UIViewController>>()

    internal fun track(viewController: UIViewController) {
        hosted += WeakReference(viewController)
    }


    /**
     * Returns how many hosted controllers are still alive. Objective-C objects owned by Kotlin
     * are released on the main loop after a collection, so this collects a few times with
     * pauses in between.
     */
    public suspend fun liveViewControllerCount(): Int {
        repeat(4) {
            GC.collect()
            delay(250)
        }
        hosted.removeAll { it.value == null }
        return hosted.size
    }
}
