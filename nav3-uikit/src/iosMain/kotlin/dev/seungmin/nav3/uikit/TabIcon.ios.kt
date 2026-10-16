package dev.seungmin.nav3.uikit

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.UIScreen

/** The point size UIKit uses for tab bar images. */
private const val TAB_ICON_POINTS = 25f

/** The image a tab bar shows for [tab]: its SF Symbol, or its painter drawn as a template image. */
internal fun tabImage(tab: TabLabel): UIImage? =
    tab.systemImage?.let { UIImage.systemImageNamed(it) } ?: tab.icon?.toTemplateImage()

@OptIn(ExperimentalForeignApi::class)
private fun Painter.toTemplateImage(): UIImage? {
    val scale = UIScreen.mainScreen.scale.toFloat()
    val pixels = (TAB_ICON_POINTS * scale).roundToInt()
    val bitmap = ImageBitmap(pixels, pixels)
    // Fit the painter's own aspect ratio into the square, centered.
    val intrinsic = intrinsicSize
    val drawSize = if (intrinsic.isSpecified && intrinsic.width > 0f && intrinsic.height > 0f) {
        val fit = min(pixels / intrinsic.width, pixels / intrinsic.height)
        Size(intrinsic.width * fit, intrinsic.height * fit)
    } else {
        Size(pixels.toFloat(), pixels.toFloat())
    }
    CanvasDrawScope().draw(Density(scale), LayoutDirection.Ltr, Canvas(bitmap), Size(pixels.toFloat(), pixels.toFloat())) {
        translate((size.width - drawSize.width) / 2f, (size.height - drawSize.height) / 2f) {
            with(this@toTemplateImage) { draw(drawSize) }
        }
    }
    val png = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
        ?: return null
    val data = png.usePinned { NSData.create(bytes = it.addressOf(0), length = png.size.toULong()) }
    return UIImage.imageWithData(data, scale.toDouble())
        ?.imageWithRenderingMode(UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate)
}
