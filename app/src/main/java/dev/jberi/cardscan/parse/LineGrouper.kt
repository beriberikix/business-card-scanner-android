package dev.jberi.cardscan.parse

import dev.jberi.cardscan.ocr.OcrLine

/**
 * Rejoins text that one logical field wrapped across two OCR lines.
 *
 * Cards routinely break a long name or title over two lines ("Marisol" / "Berenguer",
 * "Senior Solutions Engineer," / "Americas"). OCR reports those as separate lines, and without
 * rejoining them the parser sees half a name.
 *
 * Merging is deliberately conservative: only text-only lines are eligible, so phone numbers,
 * emails and URLs are never glued together.
 */
internal object LineGrouper {

  fun group(lines: List<OcrLine>): List<OcrLine> {
    if (lines.size < 2) return lines
    val out = mutableListOf<OcrLine>()
    var current = lines.first()
    for (next in lines.drop(1)) {
      if (shouldMerge(current, next)) {
        current = merge(current, next)
      } else {
        out += current
        current = next
      }
    }
    out += current
    return out
  }

  private fun merge(a: OcrLine, b: OcrLine) = a.copy(
    text = a.text.trimEnd() + " " + b.text.trim(),
    left = minOf(a.left, b.left),
    top = minOf(a.top, b.top),
    right = maxOf(a.right, b.right),
    bottom = maxOf(a.bottom, b.bottom),
    confidence = minOf(a.confidence, b.confidence),
  )

  private fun shouldMerge(a: OcrLine, b: OcrLine): Boolean {
    if (!isPlainText(a.text) || !isPlainText(b.text)) return false

    val gap = b.top - a.bottom
    val adjacent = gap <= 0.7 * minOf(a.height, b.height) && gap > -a.height
    if (!adjacent) return false

    val leftAligned = kotlin.math.abs(a.left - b.left) <= 0.25 * maxOf(a.width, b.width)
    if (!leftAligned) return false

    // A trailing comma is an explicit continuation mark, so trust it over style similarity.
    if (a.text.trimEnd().endsWith(',')) return true

    val heightRatio = minOf(a.height, b.height).toDouble() / maxOf(a.height, b.height)
    if (heightRatio < 0.7) return false

    // Never glue a job title onto a name: they are different fields in similar type.
    val aTitle = isTitleText(a.text)
    val bTitle = isTitleText(b.text)
    if (aTitle != bTitle) return false

    // Only merge fragments that could each be part of a name or title, never full sentences.
    return a.text.split(Regex("""\s+""")).size <= 4 && b.text.split(Regex("""\s+""")).size <= 4
  }

  /** True for lines carrying no contact channel, i.e. candidates for name/title/company. */
  private fun isPlainText(text: String): Boolean =
    !text.any(Char::isDigit) &&
      !text.contains('@') &&
      !text.contains('©') &&
      !Lexicons.URL.containsMatchIn(text)

  private fun isTitleText(text: String): Boolean =
    text.lowercase()
      .split(Regex("""[\s,/|•·]+"""))
      .any { it.trim('.', ',') in Lexicons.TITLE_MARKERS }
}
