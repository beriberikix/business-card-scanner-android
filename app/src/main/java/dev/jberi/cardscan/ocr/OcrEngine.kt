package dev.jberi.cardscan.ocr

import android.graphics.Bitmap

/**
 * Text recognition behind an interface so the backend (currently ML Kit, proprietary) can be
 * replaced with an open one such as PaddleOCR without touching capture, parsing or saving.
 */
interface OcrEngine {
  suspend fun recognize(bitmap: Bitmap): OcrResult
  fun close() {}
}
