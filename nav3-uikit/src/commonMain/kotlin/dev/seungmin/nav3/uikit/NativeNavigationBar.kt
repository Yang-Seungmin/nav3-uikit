package dev.seungmin.nav3.uikit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the current entry can show the native navigation bar. It is `true` inside the pushed
 * entries of [NativeNavDisplay] and [NativeTabDisplay] on iOS, unless the entry has
 * [NativeNavigation.navigationBarHidden], and `false` in sheets, overlays, and on other platforms.
 *
 * The bar is shown only for entries that ask for it, with [NativeNavigation.title] metadata or by
 * calling [NativeNavigationBarItems]. A screen that draws its own top app bar keeps working as it
 * is. To use the native bar on iOS and its own bar elsewhere, a screen reads this value:
 * ```
 * if (LocalNativeNavigationBar.current) {
 *     NativeNavigationBarItems(title = note.title, trailing = listOf(NativeBarButton("Edit") { ... }))
 * } else {
 *     TopAppBar(title = { Text(note.title) }, ...)
 * }
 * ```
 */
public val LocalNativeNavigationBar: CompositionLocal<Boolean> get() = LocalNativeNavigationBarAvailable

internal val LocalNativeNavigationBarAvailable = compositionLocalOf { false }

/** A button in the trailing part of the native navigation bar. [systemImage] is an SF Symbol name. */
public class NativeBarButton(
    public val title: String,
    public val systemImage: String? = null,
    public val enabled: Boolean = true,
    public val onClick: () -> Unit,
)

/**
 * Shows the native navigation bar for the entry whose content calls it, with [title] and the
 * [trailing] buttons, for as long as the call stays in composition. [title] takes precedence over
 * [NativeNavigation.title] metadata, so it can follow state such as a loaded item's name; without
 * either, the bar shows only the back button.
 *
 * Several calls in one entry are combined: the most recently composed non-null title is shown,
 * and the buttons of all calls appear in composition order.
 *
 * Does nothing where [LocalNativeNavigationBar] is `false`.
 */
@Composable
public expect fun NativeNavigationBarItems(
    title: String? = null,
    trailing: List<NativeBarButton> = emptyList(),
)
