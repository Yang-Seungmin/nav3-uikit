package dev.seungmin.nav3.uikit

import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.metadata

/**
 * [androidx.navigation3.runtime.NavEntry.metadata] keys read by [NativeNavDisplay] on iOS.
 * Other platforms ignore them.
 *
 * ```
 * entry<Detail>(metadata = NativeNavigation.title("Detail") + NativeNavigation.sheet()) { ... }
 * ```
 */
public object NativeNavigation {

    /** Title shown in the native navigation bar. */
    public object Title : NavMetadataKey<String> {
        override fun toString(): String = "dev.seungmin.nav3.uikit.Title"
    }

    /** Whether the navigation bar uses the large title style for this entry. */
    public object LargeTitle : NavMetadataKey<Boolean> {
        override fun toString(): String = "dev.seungmin.nav3.uikit.LargeTitle"
    }

    /** Hides the native navigation bar while this entry is on top. */
    public object NavigationBarHidden : NavMetadataKey<Boolean> {
        override fun toString(): String = "dev.seungmin.nav3.uikit.NavigationBarHidden"
    }

    /** How the entry is shown. Entries without this key are pushed. */
    public object Presentation : NavMetadataKey<NativePresentation> {
        override fun toString(): String = "dev.seungmin.nav3.uikit.Presentation"
    }

    public fun title(text: String, large: Boolean = false): Map<String, Any> = metadata {
        put(Title, text)
        put(LargeTitle, large)
    }

    public fun navigationBarHidden(): Map<String, Any> = metadata { put(NavigationBarHidden, true) }

    public fun sheet(
        detents: List<SheetDetent> = listOf(SheetDetent.Large),
        grabberVisible: Boolean = true,
    ): Map<String, Any> = metadata {
        put(Presentation, NativePresentation.Sheet(detents, grabberVisible))
    }
}

public sealed interface NativePresentation {
    /** Pushed onto the navigation stack. */
    public data object Push : NativePresentation

    /**
     * Presented modally as a sheet. Sheet entries must come after all pushed entries in the back
     * stack; a pushed entry that follows a sheet is presented as a sheet too.
     */
    public data class Sheet(
        val detents: List<SheetDetent> = listOf(SheetDetent.Large),
        val grabberVisible: Boolean = true,
    ) : NativePresentation
}

public enum class SheetDetent { Medium, Large }
