package dev.seungmin.nav3.uikit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
internal class EntryNavigationItem(viewController: UIViewController) {
    private val controller = WeakReference(viewController)

    var metadataTitle: String? = null
        set(value) {
            field = value
            applyTitle()
        }

    var contentTitle: String? = null
        set(value) {
            field = value
            applyTitle()
        }

    fun setTrailing(items: List<UIBarButtonItem>) {
        controller.value?.navigationItem?.rightBarButtonItems = items.reversed()
    }

    private fun applyTitle() {
        val title = contentTitle ?: metadataTitle
        val viewController = controller.value ?: return
        if (viewController.title != title) viewController.title = title
    }
}

@Composable
public actual fun NativeNavigationBarItems(title: String?, trailing: List<NativeBarButton>) {
    val item = LocalEntryNavigationItem.current ?: return
    if (!LocalNativeNavigationBar.current) return

    DisposableEffect(item, title) {
        item.contentTitle = title
        onDispose { item.contentTitle = null }
    }

    // UIKit keeps the buttons; they reach the latest click handlers through this state, so the
    // buttons are only rebuilt when what they show changes.
    val latest by rememberUpdatedState(trailing)
    val shown = trailing.map { Triple(it.title, it.systemImage, it.enabled) }
    DisposableEffect(item, shown) {
        item.setTrailing(
            shown.mapIndexed { index, (title, image, enabled) ->
                barButton(title, image, enabled) { latest.getOrNull(index)?.onClick?.invoke() }
            },
        )
        onDispose { item.setTrailing(emptyList()) }
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
