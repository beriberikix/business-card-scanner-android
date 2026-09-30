package dev.jberi.cardscan.parse

import dev.jberi.cardscan.model.Email

/**
 * Recovers email addresses that OCR damaged.
 *
 * The common failure on real cards is a dot swallowed before the top-level domain
 * ("marisol@veltrix.io" read as "marisol@veltrixio"), which makes the address fail a strict regex.
 * Repairs are only attempted when the strict pattern already failed, and only produce a result
 * that then passes it, so this can add addresses but never corrupt a clean one.
 */
object EmailRepair {

  /** Longest first, so "com" is tried before "co" and "io" before "o"-like fragments. */
  private val TLDS = listOf(
    "technology", "solutions", "consulting", "digital", "agency", "studio", "design", "cloud",
    "online", "store", "group", "email", "info", "biz", "name", "tech", "team", "works", "world",
    "life", "live", "news", "blog", "site", "shop", "app", "dev", "com", "net", "org", "edu",
    "gov", "mil", "int", "int", "biz", "pro", "xyz", "ltd", "inc", "llc", "ai", "io", "co", "me",
    "us", "uk", "de", "fr", "es", "it", "nl", "be", "ch", "at", "se", "no", "dk", "fi", "pl",
    "pt", "ie", "jp", "cn", "in", "sg", "au", "nz", "br", "mx", "za", "il", "ae", "ca", "eu",
  ).distinct().sortedByDescending { it.length }

  /** Characters OCR commonly substitutes for "@". */
  private val AT_LOOKALIKES = listOf("©", "(at)", "[at]", " at ", "＠")

  /** A token that is email-shaped even if it is not a valid address. */
  private val LOOSE = Regex("""\S*[@©＠]\S*""")

  /**
   * Returns every address found on the line: strict matches first, then a repair attempt for any
   * email-shaped token the strict pattern rejected.
   */
  fun extract(line: String): List<Email> {
    val strict = Lexicons.EMAIL.findAll(line)
      .map { Email(it.value.lowercase().trim('.', ',', ';', ':')) }
      .toList()
    if (strict.isNotEmpty()) return strict

    return LOOSE.findAll(line).mapNotNull { m ->
      repair(m.value)?.let { Email(address = it, raw = m.value, repaired = true) }
    }.toList()
  }

  /** Best-effort reconstruction, or null when the token cannot be made into a valid address. */
  fun repair(token: String): String? {
    var s = token.trim().trim('.', ',', ';', ':', '(', ')').lowercase()
    AT_LOOKALIKES.forEach { s = s.replace(it, "@") }
    s = s.replace(" ", "")
    if (s.count { it == '@' } != 1) return null

    val local = s.substringBefore('@')
    var domain = s.substringAfter('@')
    if (local.isBlank() || domain.isBlank()) return null

    if (!domain.contains('.')) {
      val tld = TLDS.firstOrNull { domain.length > it.length + 1 && domain.endsWith(it) }
        ?: return null
      domain = domain.dropLast(tld.length) + "." + tld
    }

    val candidate = "$local@$domain"
    return candidate.takeIf { Lexicons.EMAIL.matches(it) }
  }
}
