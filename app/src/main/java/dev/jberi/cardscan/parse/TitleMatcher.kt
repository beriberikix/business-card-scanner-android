package dev.jberi.cardscan.parse

/**
 * Decides whether a line reads as a job title.
 *
 * Lives on its own because two callers need exactly the same answer. When [LineGrouper] and
 * [HeuristicCardParser] disagreed, a name was merged into the title line beneath it and both
 * fields were lost.
 *
 * Matching is prefix-tolerant, because OCR mangles the ends of words: real cards produced
 * "Directorr" and "Directo" where the print said "Director". Requiring a recognised title word
 * also keeps taglines out; a parent-company tagline is not a job title, and an earlier rule
 * that fired on any line containing " of " claimed it.
 */
internal object TitleMatcher {

  /** Shortest word that may be matched by prefix rather than exactly. */
  private const val MIN_FUZZY = 5

  private val SPLIT = Regex("""[\s,/|•·&]+""")

  /**
   * Words that must never be fuzzy-matched to a title marker.
   *
   * Prefix tolerance is what recovers "Directo" from a damaged "Director", but it also makes
   * "office" look like "officer" and "direct" look like "director". Both of those are contact
   * labels printed beside a phone number, and on a two-column card they sit on their own line.
   */
  private val NEVER_TITLE = setOf(
    "office", "direct", "mobile", "main", "home", "fax", "tel", "telephone", "phone", "cell",
    "email", "e-mail", "web", "website", "site", "address", "part", "parts",
  )

  fun isTitle(text: String): Boolean = words(text).any(::matchesMarker)

  fun words(text: String): List<String> =
    text.lowercase().split(SPLIT).map { it.trim('.', ',', '-') }.filter { it.isNotBlank() }

  /** True when the word is a title marker, allowing for a truncated or over-run OCR ending. */
  private fun matchesMarker(word: String): Boolean {
    if (word in Lexicons.TITLE_MARKERS) return true
    if (word in NEVER_TITLE) return false
    return Lexicons.TITLE_MARKERS.any { marker ->
      when {
        marker.length < MIN_FUZZY && word.length < MIN_FUZZY -> false
        // "Directorr" for "director": OCR added a character.
        word.length > marker.length && marker.length >= MIN_FUZZY && word.startsWith(marker) -> true
        // "Directo" for "director": OCR dropped one.
        marker.length > word.length && word.length >= MIN_FUZZY && marker.startsWith(word) -> true
        else -> false
      }
    }
  }
}
