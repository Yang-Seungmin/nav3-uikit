package dev.seungmin.nav3.uikit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the current entry is shown under a visible native navigation bar. It is `true` inside
 * the entries of [NativeNavDisplay] and [NativeTabDisplay] on iOS, except for sheets, dialogs, and
 * entries with [NativeNavigation.navigationBarHidden], and `false` everywhere else.
 *
 * A screen that draws its own top app bar on other platforms reads it to skip that bar and call
 * [NativeNavigationBarItems] instead:
 * ```
 * if (LocalNativeNavigationBar.current) {
 *     NativeNavigationBarItems(title = note.title, trailing = listOf(NativeBarButton("Edit") { ... }))
 * } else {
 *     TopAppBar(title = { Text(note.title) }, ...)
 * }
 * ```
 */
public val LocalNativeNavigationBar: CompositionLocal<Boolean> get() = LocalShowsNativeNavigationBar

internal val LocalShowsNativeNavigationBar = compositionLocalOf { false }

/** A button in the trailing part of the native navigation bar. [systemImage] is an SF Symbol name. */
public class NativeBarButton(
    public val title: String,
    public val systemImage: String? = null,
    public val enabled: Boolean = true,
    public val onClick: () -> Unit,
)

/**
 * Sets the native navigation bar's [title] and [trailing] buttons for the entry whose content
 * calls it, for as long as the call stays in composition. [title] takes precedence over
 * [NativeNavigation.title] metadata, so it can follow state such as a loaded item's name.
 *
 * Does nothing where [LocalNativeNavigationBar] is `false`.
 */
@Composable
public expect fun NativeNavigationBarItems(
    title: String? = null,
    trailing: List<NativeBarButton> = emptyList(),
)
