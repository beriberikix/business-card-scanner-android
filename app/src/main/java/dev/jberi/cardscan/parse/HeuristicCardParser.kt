package dev.jberi.cardscan.parse

import dev.jberi.cardscan.model.ContactCard
import dev.jberi.cardscan.model.Email
import dev.jberi.cardscan.model.Phone
import dev.jberi.cardscan.model.PhoneType
import dev.jberi.cardscan.ocr.OcrLine
import dev.jberi.cardscan.ocr.OcrResult

/**
 * Layout-aware heuristic parser.
 *
 * Contact channels (email, phone, URL) come out of regexes and are effectively exact. The
 * interesting work is name, title and company, which are separated using the cues a human uses:
 * text size, vertical position, legal-form words, job-title words, and agreement between the name
 * and the email local part.
 */
class HeuristicCardParser : CardParser {

  override fun parse(ocr: OcrResult, defaultRegion: String): ContactCard {
    val ordered = ocr.readingOrder().filter { it.text.isNotBlank() }
    if (ordered.isEmpty()) return ContactCard()
    // Rejoin names and titles that wrapped across two OCR lines before classifying anything.
    val allLines = LineGrouper.group(ordered)

    // Standalone labels ("Mobile", "Email") are noise when looking for a name or company, but
    // they are what types the number underneath them on a two-column card. So contact channels
    // are read from every line, and only the classification pool drops the labels.
    val lines = allLines.filter { !Lexicons.NOISE.matches(it.text) }

    val emails = allLines.flatMap { EmailRepair.extract(it.text) }.distinctBy { it.address }

    val phones = extractPhones(allLines, defaultRegion)

    // Emails are stripped from each line first, so a bare domain only matches when it really
    // appears outside an email address.
    val emailDomains = emails.mapNotNull { it.address.substringAfter('@').takeIf(String::isNotBlank) }.toSet()
    val urls = allLines.flatMap { line ->
      val squeezed = EmailRepair.squeeze(line.text)
      val withoutEmails = Lexicons.EMAIL.replace(squeezed, " ")
      Lexicons.URL.findAll(withoutEmails).map { it.value }
    }.map(::cleanUrl).filter { it.isNotBlank() }.distinct()

    // Lines that carry contact channels are not name/title/company candidates.
    val consumed = lines.filter { line ->
      EmailRepair.extract(line.text).isNotEmpty() ||
        PhoneNormalizer.extract(line.text, defaultRegion).isNotEmpty() ||
        isUrlOnly(line.text)
    }.toSet()

    val addressLines = lines.filter { it !in consumed && looksLikeAddress(it.text) }
    val remaining = lines.filter { it !in consumed && it !in addressLines }

    // Order matters. Title first, so "Senior Solutions Engineer" cannot be claimed as the
    // company by the word "Solutions". Then the name, so the domain-matching company fallback
    // cannot claim a person whose surname is also the domain ("ines@okafor.studio").
    val title = pickTitle(remaining, null)
    val name = pickName(remaining.filter { it != title }, emails, null, title)
    val company = pickCompany(remaining.filter { it != title && it != name }, emailDomains)

    val parsedName = splitName(name?.text.orEmpty())

    return ContactCard(
      givenName = parsedName.given,
      middleName = parsedName.middle,
      familyName = parsedName.family,
      prefix = parsedName.prefix,
      suffix = parsedName.suffix,
      jobTitle = title?.text?.trim().orEmpty(),
      company = company?.text?.trim().orEmpty(),
      phones = phones,
      emails = emails,
      urls = urls,
      address = addressLines.joinToString(", ") { it.text.trim() },
      note = "",
    )
  }

  // ---------- contact channels ----------

  private fun extractPhones(lines: List<OcrLine>, region: String): List<Phone> {
    val out = mutableListOf<Phone>()
    // Two-column cards print the label ("Mobile") on its own line above the number, so carry a
    // label-only line forward and apply it to the next number that has no label of its own.
    var pendingLabel: PhoneType? = null
    lines.forEach { line ->
      val found = PhoneNormalizer.extract(line.text, region, fallbackType = pendingLabel)
      if (found.isEmpty()) {
        val labelOnly = !line.text.any(Char::isDigit) &&
          line.text.trim().split(Regex("""\s+""")).size <= 2
        if (labelOnly) PhoneNormalizer.typeFromLabel(line.text)?.let { pendingLabel = it }
      } else {
        out += found
        pendingLabel = null
      }
    }
    val deduped = out.distinctBy { it.number.filter(Char::isDigit) }
    // Unlabelled numbers: the first is usually the one the person wants reached on.
    var assignedMobile = deduped.any { it.type == PhoneType.MOBILE }
    return deduped.map { p ->
      if (p.type == PhoneType.WORK && !assignedMobile && looksMobile(p)) {
        assignedMobile = true
        p.copy(type = PhoneType.MOBILE)
      } else {
        p
      }
    }
  }

  /** UK 07x, US numbers are indistinguishable, so only obvious mobile prefixes are promoted. */
  private fun looksMobile(p: Phone): Boolean {
    val n = p.number
    return n.startsWith("+447") || n.startsWith("+491") || n.startsWith("+336") ||
      n.startsWith("+337") || n.startsWith("+3936") || n.startsWith("+614")
  }

  private fun cleanUrl(s: String) = s.trim().trim('.', ',', ';', ':', ')', '(')

  private fun isUrlOnly(text: String): Boolean {
    val squeezed = EmailRepair.squeeze(text)
    val stripped = Lexicons.URL.replace(squeezed, " ").replace(Regex("""[\s:|/]+"""), "")
    return Lexicons.URL.containsMatchIn(squeezed) &&
      stripped.lowercase().replace(Regex("(web|www|site|url)"), "").isBlank()
  }

  // ---------- line classification ----------

  private fun words(text: String) =
    text.lowercase().split(Regex("""[\s,/|•·]+""")).filter { it.isNotBlank() }

  private fun looksLikeAddress(text: String): Boolean {
    val w = words(text)
    if (w.isEmpty()) return false
    val hasMarker = w.any { it.trim('.', ',') in Lexicons.ADDRESS_MARKERS }
    val lower = text.lowercase()
    val hasCountry = Lexicons.COUNTRIES.any { country ->
      // Whole-word match only: a substring test makes "Marcus" contain "us".
      Regex("""(^|\W)${Regex.escape(country)}($|\W)""").containsMatchIn(lower)
    }
    val hasNumber = text.any(Char::isDigit)
    val hasPostal = Lexicons.POSTAL_PATTERNS.take(3).any { it.containsMatchIn(text) }
    // A street line normally pairs a number with a street word; a city line pairs a
    // postal code or country with a place name.
    return (hasMarker && hasNumber) || hasCountry || (hasPostal && w.size <= 5 && hasNumber)
  }

  private fun pickCompany(candidates: List<OcrLine>, emailDomains: Set<String>): OcrLine? {
    // A legal form ("Ltd", "GmbH") is decisive.
    candidates.firstOrNull { line ->
      words(line.text).any { it.trim('.', ',') in Lexicons.COMPANY_STRONG }
    }?.let { return it }

    // Industry words only count on a line that is not itself a job title.
    candidates.firstOrNull { line ->
      !isTitleLine(line.text) && words(line.text).any { it.trim('.', ',') in Lexicons.COMPANY_WEAK }
    }?.let { return it }

    // Otherwise, a line matching the email domain's own label.
    val labels = emailDomains.mapNotNull { d ->
      d.split('.').firstOrNull { it.length > 2 && it != "www" }?.lowercase()
    }
    if (labels.isNotEmpty()) {
      candidates.firstOrNull { line ->
        val squashed = line.text.lowercase().filter(Char::isLetterOrDigit)
        labels.any { squashed.contains(it) || it.contains(squashed) && squashed.length > 3 }
      }?.let { return it }
    }

    // Last resort: an all-caps line, which cards commonly use for the company.
    return candidates.firstOrNull { line ->
      val letters = line.text.filter(Char::isLetter)
      letters.length > 2 && letters == letters.uppercase() && !isTitleLine(line.text)
    }
  }

  private fun isTitleLine(text: String): Boolean {
    val w = words(text).map { it.trim('.', ',') }
    return w.any { it in Lexicons.TITLE_MARKERS } || text.contains(" of ", true)
  }

  private fun pickTitle(candidates: List<OcrLine>, company: OcrLine?): OcrLine? =
    candidates.firstOrNull { it != company && isTitleLine(it.text) }

  /**
   * Scores the remaining lines as person names. Cues, strongest first: the line agrees with the
   * email local part; the line sits in large type; the line has 2-3 capitalized word-like tokens.
   */
  private fun pickName(
    candidates: List<OcrLine>,
    emails: List<Email>,
    company: OcrLine?,
    title: OcrLine?,
  ): OcrLine? {
    val pool = candidates.filter { it != company && it != title && plausibleName(it.text) }
    if (pool.isEmpty()) return null

    val localTokens = emails.map { it.address.substringBefore('@') }
      .flatMap { it.split('.', '_', '-', '+') }
      .filter { it.length > 2 }
      .map { it.lowercase() }
      .toSet()

    val maxHeight = candidates.maxOfOrNull { it.height }?.coerceAtLeast(1) ?: 1

    return pool.maxByOrNull { line ->
      var score = 0.0
      val lw = words(line.text).map { it.trim('.', ',') }

      if (localTokens.isNotEmpty()) {
        val hits = lw.count { w -> localTokens.any { it == w || (it.length > 3 && w.startsWith(it)) } }
        score += hits * 40.0
      }
      score += (line.height.toDouble() / maxHeight) * 25.0
      if (lw.size in 2..3) score += 10.0
      // Cards put the name near the top or just above the title.
      if (title != null && line.centerY < title.centerY) score += 8.0
      if (lw.any { it in Lexicons.NAME_PREFIXES || it in Lexicons.NAME_SUFFIXES }) score += 5.0
      if (line.text.any(Char::isDigit)) score -= 30.0
      // A line carrying a company word is describing a business, not a person.
      if (lw.any { it in Lexicons.COMPANY_WEAK || it in Lexicons.COMPANY_STRONG }) score -= 25.0
      score
    }
  }

  private fun plausibleName(text: String): Boolean {
    val t = text.trim()
    if (t.length < 3 || t.length > 48) return false
    if (t.count(Char::isDigit) > 0) return false
    val w = words(t)
    if (w.size !in 1..5) return false
    if (isTitleLine(t)) return false
    if (w.any { it.trim('.', ',') in Lexicons.COMPANY_STRONG }) return false
    // Mostly letters, allowing hyphens, apostrophes and periods in initials.
    val letters = t.count { it.isLetter() }
    return letters.toDouble() / t.length >= 0.6
  }

  // ---------- name splitting ----------

  internal data class ParsedName(
    val prefix: String = "",
    val given: String = "",
    val middle: String = "",
    val family: String = "",
    val suffix: String = "",
  )

  internal fun splitName(input: String): ParsedName {
    var text = input.trim().trim(',')
    if (text.isBlank()) return ParsedName()

    // "Whitfield, Dana R." -> "Dana R. Whitfield"
    if (text.contains(',')) {
      val (a, b) = text.split(',', limit = 2).map { it.trim() }
      if (b.isNotBlank() && b.lowercase().trim('.') !in Lexicons.NAME_SUFFIXES) {
        text = "$b $a"
      }
    }

    val tokens = text.split(Regex("""\s+""")).filter { it.isNotBlank() }.toMutableList()

    var prefix = ""
    while (tokens.isNotEmpty() && tokens.first().lowercase().trim('.') in
      Lexicons.NAME_PREFIXES.map { it.trim('.') }
    ) {
      prefix = (prefix + " " + tokens.removeAt(0)).trim()
    }

    var suffix = ""
    while (tokens.isNotEmpty() && tokens.last().lowercase().trim('.', ',') in
      Lexicons.NAME_SUFFIXES.map { it.trim('.') }
    ) {
      suffix = (tokens.removeAt(tokens.size - 1).trim(',') + " " + suffix).trim()
    }

    return when (tokens.size) {
      0 -> ParsedName(prefix = prefix, suffix = suffix)
      1 -> ParsedName(prefix = prefix, given = tokens[0], suffix = suffix)
      2 -> ParsedName(prefix = prefix, given = tokens[0], family = tokens[1], suffix = suffix)
      else -> ParsedName(
        prefix = prefix,
        given = tokens.first(),
        middle = tokens.subList(1, tokens.size - 1).joinToString(" "),
        family = tokens.last(),
        suffix = suffix,
      )
    }
  }
}
