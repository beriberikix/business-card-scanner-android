package dev.jberi.cardscan.parse

import dev.jberi.cardscan.model.Email

/**
 * Recovers email addresses that OCR damaged.
 *
 * Three failures show up repeatedly on real cards, all seen in this project's own samples:
 * a dot swallowed before the top-level domain ("marisol@veltrix.io" read as "marisol@veltrixio"),
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
  fun squeeze(text: String): String = text
    .replace(Regex("""\s*[$AT_CHARS]\s*"""), "@")
    .replace(Regex("""(?<=[A-Za-z0-9])\s*\.\s*(?=[A-Za-z0-9])"""), ".")

  /** Every address on the line: strict matches if there are any, otherwise repair attempts. */
  fun extract(line: String): List<Email> {
    val strict = Lexicons.EMAIL.findAll(line)
      .map { Email(it.value.lowercase().trim('.', ',', ';', ':')) }
      .toList()
    if (strict.isNotEmpty()) return strict

    return LOOSE.findAll(line).mapNotNull { m ->
      val raw = m.value
      repair(raw)?.let { Email(address = it, raw = raw.trim(), repaired = true) }
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
