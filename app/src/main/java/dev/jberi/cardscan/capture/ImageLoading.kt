package dev.jberi.cardscan.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Longest edge we feed to OCR. Business card text stays well above ML Kit's 16 px minimum here. */
private const val MAX_EDGE = 2400

/**
 * Decodes an image Uri to a software Bitmap, downscaled and EXIF-rotated.
 *
 * ML Kit needs a software bitmap, and [ImageDecoder] applies EXIF orientation for us, which
 * matters for gallery and share-sheet images (the document scanner already returns upright JPEGs).
 */
suspend fun loadBitmapForOcr(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
  val source = ImageDecoder.createSource(context.contentResolver, uri)
  ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    decoder.isMutableRequired = false
    val longest = maxOf(info.size.width, info.size.height)
    if (longest > MAX_EDGE) {
      val scale = MAX_EDGE.toFloat() / longest
      decoder.setTargetSize(
        (info.size.width * scale).toInt().coerceAtLeast(1),
        (info.size.height * scale).toInt().coerceAtLeast(1),
      )
    }
  }
}
