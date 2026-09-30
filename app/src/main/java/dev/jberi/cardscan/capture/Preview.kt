package dev.jberi.cardscan.capture

import android.graphics.Bitmap
import androidx.core.graphics.scale

/** Longest edge kept for on-screen previews. */
private const val PREVIEW_EDGE = 600

/**
 * Shrinks a captured image down to something cheap to hold onto.
 *
 * A scanning session can return twenty pages. Keeping every full-resolution bitmap would cost
 * hundreds of megabytes, so once OCR has run only this preview is retained.
 */
fun Bitmap.toPreview(): Bitmap {
  val longest = maxOf(width, height)
  if (longest <= PREVIEW_EDGE) return this
  val scale = PREVIEW_EDGE.toFloat() / longest
  return scale(
    (width * scale).toInt().coerceAtLeast(1),
    (height * scale).toInt().coerceAtLeast(1),
  )
}
