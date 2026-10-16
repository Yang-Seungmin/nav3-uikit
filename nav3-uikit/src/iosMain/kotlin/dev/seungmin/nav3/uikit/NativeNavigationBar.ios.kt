package dev.seungmin.nav3.uikit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import platform.UIKit.UIAction
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIImage
import platform.UIKit.UIViewController
import platform.UIKit.navigationItem

/** The navigation item of the entry being composed; provided only inside hosted entries. */
internal val LocalEntryNavigationItem = staticCompositionLocalOf<EntryNavigationItem?> { null }

/**
 * Applies the title and buttons that an entry's metadata and content ask for to its controller.
 * It holds the controller weakly because the controller's content can reach this object.
 */
@OptIn(ExperimentalNativeApi::class)
internal class EntryNavigationItem(
    viewController: UIViewController,
    /** Called when [requested] changes. Must not hold the coordinator strongly. */
    private val onRequestedChanged: () -> Unit,
) {
    private val controller = WeakReference(viewController)
    private val requests = mutableListOf<Request>()

    var metadataTitle: String? = null
        set(value) {
            field = value
            apply()
        }

    /** Whether the content calls [NativeNavigationBarItems]. */
    val requested: Boolean
        get() = requests.isNotEmpty()

    fun add(request: Request) {
        requests += request
        apply()
        if (requests.size == 1) onRequestedChanged()
    }

    fun remove(request: Request) {
        if (!requests.remove(request)) return
        apply()
        if (requests.isEmpty()) onRequestedChanged()
    }

    fun apply() {
        val viewController = controller.value ?: return
        val title = requests.lastOrNull { it.title != null }?.title ?: metadataTitle
        if (viewController.title != title) viewController.title = title
        // UIKit lays out right bar button items from the trailing edge.
        viewController.navigationItem.rightBarButtonItems = requests.flatMap { it.buttons }.reversed()
    }

    /** What one [NativeNavigationBarItems] call shows. */
    class Request {
        var title: String? = null
        var buttons: List<UIBarButtonItem> = emptyList()
    }
}

@Composable
public actual fun NativeNavigationBarItems(title: String?, trailing: List<NativeBarButton>) {
    val item = LocalEntryNavigationItem.current ?: return
    if (!LocalNativeNavigationBar.current) return

    val request = remember(item) { EntryNavigationItem.Request() }
    DisposableEffect(item, request) {
        item.add(request)
        onDispose { item.remove(request) }
    }

    // UIKit keeps the buttons; they reach the latest click handlers through this state, so the
    // buttons are only rebuilt when what they show changes.
    val latest by rememberUpdatedState(trailing)
    val shown = trailing.map { Triple(it.title, it.systemImage, it.enabled) }
    val buttons = remember(shown) {
        shown.mapIndexed { index, (buttonTitle, image, enabled) ->
            barButton(buttonTitle, image, enabled) { latest.getOrNull(index)?.onClick?.invoke() }
        }
    }
    SideEffect {
        if (request.title != title || request.buttons !== buttons) {
            request.title = title
            request.buttons = buttons
            item.apply()
        }
    }
}

private fun barButton(
    title: String,
    systemImage: String?,
    enabled: Boolean,
    onClick: () -> Unit,
): UIBarButtonItem {
    val action = UIAction.actionWithTitle(
        title = title,
        image = systemImage?.let { UIImage.systemImageNamed(it) },
        identifier = null,
        handler = { _ -> onClick() },
    )
    return UIBarButtonItem(primaryAction = action).apply {
        this.enabled = enabled
    }
}
