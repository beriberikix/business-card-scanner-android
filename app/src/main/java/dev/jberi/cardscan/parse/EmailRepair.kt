package dev.jberi.cardscan.parse

import dev.jberi.cardscan.model.Email

/**
 * Recovers email addresses that OCR damaged.
 *
 * Three failures show up repeatedly on real cards, all seen in this project's own samples:
 * a dot swallowed before the top-level domain ("ada@lumen.io" read as "ada@lumenio"),
 * a space inserted around the dot ("marcus@vantalabs. io"), and a space before the at sign
 * ("sofia @mvlaw.com"). Each makes the address fail a strict regex.
 *
 * Repair is only attempted after the strict pattern has already failed, and only returns a value
 * that then passes it, so this can recover addresses but never corrupt a clean one.
 */
object EmailRepair {

  /** Longest first, so "com" is tried before "co" and "tech" before "ch". */
  private val TLDS = listOf(
    "technology", "solutions", "consulting", "digital", "agency", "studio", "design", "cloud",
    "online", "store", "group", "email", "info", "biz", "name", "tech", "team", "works", "world",
    "life", "live", "news", "blog", "site", "shop", "app", "dev", "com", "net", "org", "edu",
    "gov", "mil", "int", "pro", "xyz", "ltd", "inc", "llc", "ai", "io", "co", "me",
    "us", "uk", "de", "fr", "es", "it", "nl", "be", "ch", "at", "se", "no", "dk", "fi", "pl",
    "pt", "ie", "jp", "cn", "in", "sg", "au", "nz", "br", "mx", "za", "il", "ae", "ca", "eu",
  ).distinct().sortedByDescending { it.length }

  /** Characters OCR commonly substitutes for "@". */
  private const val AT_CHARS = "@©＠"

  /**
   * An email-shaped run, tolerating whitespace around the at sign and around each dot, so the
   * whole address is captured even when OCR broke it apart.
   */
  private val LOOSE = Regex(
    """[A-Za-z0-9._%+\-]+\s*[$AT_CHARS]\s*[A-Za-z0-9\-]+(?:\s*\.\s*[A-Za-z0-9\-]+)*""",
  )

  /** Removes the spaces OCR inserted around at signs and dots. */
  fun squeeze(text: String): String = squeezeIndexed(text).first

  /**
   * Squeezes [text] and records, for each character of the result, where it came from.
   *
   * The map is what lets a repaired address still report the characters OCR actually produced.
   * Without it a match found in the squeezed text can only be described by the squeezed text,
   * and the review screen would claim an address was repaired from itself.
   */
  private fun squeezeIndexed(text: String): Pair<String, IntArray> {
    val sb = StringBuilder(text.length)
    val map = ArrayList<Int>(text.length)
    var i = 0
    while (i < text.length) {
      val c = text[i]
      when {
        c.isWhitespace() -> {
          var j = i
          while (j < text.length && text[j].isWhitespace()) j++
          val next = text.getOrNull(j)
          val prev = sb.lastOrNull()
          // Drop the run only when it brackets an at sign, or a dot between two characters
          // that belong to an address.
          val bracketsAt = next != null && next in AT_CHARS
          val bracketsDot = next == '.' && prev != null && prev.isLetterOrDigit()
          if (bracketsAt || bracketsDot) {
            i = j
          } else {
            sb.append(c); map.add(i); i++
          }
        }
        c in AT_CHARS -> {
          sb.append('@'); map.add(i); i++
          while (i < text.length && text[i].isWhitespace()) i++
        }
        c == '.' && sb.lastOrNull()?.isLetterOrDigit() == true -> {
          sb.append('.'); map.add(i); i++
          var j = i
          while (j < text.length && text[j].isWhitespace()) j++
          if (text.getOrNull(j)?.isLetterOrDigit() == true) i = j
        }
        else -> { sb.append(c); map.add(i); i++ }
      }
    }
    return sb.toString() to map.toIntArray()
  }

  /** Every address on the line: strict matches if there are any, otherwise repair attempts. */
  fun extract(line: String): List<Email> {
    // Everything is matched against the squeezed text rather than the raw line. Matching the
    // raw line is not merely weaker, it is actively wrong: OCR splits a local part with a stray
    // space ("andrew. worth@..."), and a strict match then succeeds on the second half alone,
    // yielding a plausible but truncated address with nothing to signal the loss. On an
    // undamaged line squeezing changes nothing, so clean addresses behave exactly as before.
    val (squeezed, map) = squeezeIndexed(line)

    fun sourceText(range: IntRange) =
      line.substring(map[range.first], map[range.last] + 1).trim()

    val strict = Lexicons.EMAIL.findAll(squeezed).map { m ->
      val address = m.value.lowercase().trim('.', ',', ';', ':')
      val raw = sourceText(m.range)
      Email(address = address, raw = raw, repaired = !address.equals(raw, ignoreCase = true))
    }.toList()
    if (strict.isNotEmpty()) return strict

    return LOOSE.findAll(squeezed).mapNotNull { m ->
      val raw = sourceText(m.range)
      repair(m.value)?.let { fixed ->
        Email(address = fixed, raw = raw, repaired = !fixed.equals(raw, ignoreCase = true))
      }
    }.toList()
  }

  /** Best-effort reconstruction, or null when the token cannot be made into a valid address. */
  fun repair(token: String): String? {
    val s = squeeze(token.trim()).trim('.', ',', ';', ':', '(', ')').lowercase()
    if (s.count { it == '@' } != 1) return null

    val local = s.substringBefore('@')
    var domain = s.substringAfter('@')
    if (local.isBlank() || domain.isBlank()) return null

    if (!domain.contains('.')) {
      val tld = TLDS.firstOrNull { domain.length > it.length + 1 && domain.endsWith(it) }
        ?: return null
      domain = domain.dropLast(tld.length) + "." + tld
    }

    return "$local@$domain".takeIf { Lexicons.EMAIL.matches(it) }
  }
}
