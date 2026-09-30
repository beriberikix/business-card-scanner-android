package dev.jberi.cardscan.ocr

import kotlinx.serialization.Serializable

/**
 * One recognized line of text with enough geometry for layout-aware parsing.
 *
 * Serializable so real OCR output can be captured on-device and committed as a test fixture,
 * letting the parser be tested on the JVM without a phone in the loop.
 */
@Serializable
data class OcrLine(
  val text: String,
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
  val confidence: Float = 0f,
  val angle: Float = 0f,
  val language: String = "",
) {
  val height: Int get() = bottom - top
  val width: Int get() = right - left
  val centerY: Int get() = (top + bottom) / 2
}

@Serializable
data class OcrResult(
  val lines: List<OcrLine> = emptyList(),
  val imageWidth: Int = 0,
  val imageHeight: Int = 0,
) {
  /** Lines in natural reading order: top to bottom, then left to right within a row. */
  fun readingOrder(): List<OcrLine> {
    val rowTolerance = (lines.map { it.height }.minOrNull() ?: 0) / 2
    return lines.sortedWith(
      compareBy({ it.centerY / (rowTolerance.coerceAtLeast(1)) }, { it.left })
    )
  }

  val plainText: String get() = readingOrder().joinToString("\n") { it.text }
}
