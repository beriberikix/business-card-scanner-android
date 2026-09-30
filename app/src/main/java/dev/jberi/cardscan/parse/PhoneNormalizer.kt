package dev.jberi.cardscan.parse

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import dev.jberi.cardscan.model.Phone
import dev.jberi.cardscan.model.PhoneType

/**
 * Finds and normalizes phone numbers in a line of card text.
 *
 * OCR on printed digits is good but not perfect, so a repair pass fixes the letter/digit
 * confusions that are common in number runs before parsing is attempted.
 */
object PhoneNormalizer {
  private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

  /** Letters OCR commonly produces inside a run of digits. */
  private const val CONFUSABLE = "OoQDlIi|SBZ"

  /**
   * A run long enough to be a phone number, tolerating [CONFUSABLE] letters so a misread digit
   * does not truncate the match. [MIN_REAL_DIGITS] then keeps words from qualifying.
   */
  private val CANDIDATE = Regex(
    """\+?[\d$CONFUSABLE][\d$CONFUSABLE\s().\-/]{6,}[\d$CONFUSABLE]""",
  )

  /** A real number keeps most of its digits even with a couple of OCR errors. */
  private const val MIN_REAL_DIGITS = 6

  /** Labels printed next to numbers on cards, longest first so "mobile" wins over "m". */
  private val TYPE_LABELS: List<Pair<Regex, PhoneType>> = listOf(
    Regex("""\b(mobile|mob|cell|cellular|handy|portable)\b""", RegexOption.IGNORE_CASE) to PhoneType.MOBILE,
    Regex("""\b(facsimile|fax|fx)\b""", RegexOption.IGNORE_CASE) to PhoneType.FAX_WORK,
    Regex("""\b(direct|office|work|tel|telephone|phone|ph|bureau)\b""", RegexOption.IGNORE_CASE) to PhoneType.WORK,
    Regex("""\b(main|switchboard|reception)\b""", RegexOption.IGNORE_CASE) to PhoneType.MAIN,
    Regex("""\b(home|residence)\b""", RegexOption.IGNORE_CASE) to PhoneType.HOME,
    // Single-letter prefixes, only when they stand alone before the number.
    Regex("""(^|\s)m[.:]?\s*(?=\+?\d)""", RegexOption.IGNORE_CASE) to PhoneType.MOBILE,
    Regex("""(^|\s)c[.:]?\s*(?=\+?\d)""", RegexOption.IGNORE_CASE) to PhoneType.MOBILE,
    Regex("""(^|\s)f[.:]?\s*(?=\+?\d)""", RegexOption.IGNORE_CASE) to PhoneType.FAX_WORK,
    Regex("""(^|\s)[todp][.:]?\s*(?=\+?\d)""", RegexOption.IGNORE_CASE) to PhoneType.WORK,
  )

  /** Repairs OCR letter/digit confusions inside what is otherwise a run of digits. */
  fun repairDigits(candidate: String): String = candidate
    .map { c ->
      when (c) {
        'O', 'o', 'Q', 'D' -> '0'
        'l', 'I', '|', 'i' -> '1'
        'S' -> '5'
        'B' -> '8'
        'Z' -> '2'
        else -> c
      }
    }
    .joinToString("")

  /** The type implied by a label on the same line, or null if the line carries no label. */
  fun typeFromLabel(line: String): PhoneType? =
    TYPE_LABELS.firstOrNull { it.first.containsMatchIn(line) }?.second

  /**
   * Extracts every phone number on a line.
   *
   * @param fallbackType used when the line carries no label of its own.
   */
  fun extract(line: String, defaultRegion: String, fallbackType: PhoneType? = null): List<Phone> {
    val labelled = typeFromLabel(line)
    return CANDIDATE.findAll(line).mapNotNull { m ->
      val raw = m.value.trim()
      // Reject runs that are really dates, postal codes, or words made of confusable letters.
      if (raw.count(Char::isDigit) < MIN_REAL_DIGITS) return@mapNotNull null
      val digits = repairDigits(raw).count(Char::isDigit)
      if (digits < 7 || digits > 15) return@mapNotNull null
      val type = labelled ?: fallbackType ?: PhoneType.WORK
      toPhone(raw, defaultRegion, type)
    }.toList()
  }

  /** Normalizes one raw string to E.164, falling back to the cleaned original when invalid. */
  fun toPhone(raw: String, defaultRegion: String, type: PhoneType): Phone {
    val repaired = repairDigits(raw)
    return try {
      val parsed = util.parse(repaired, defaultRegion)
      if (util.isValidNumber(parsed)) {
        Phone(
          number = util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164),
          type = type,
          raw = raw,
          normalized = true,
        )
      } else {
        Phone(number = repaired.trim(), type = type, raw = raw, normalized = false)
      }
    } catch (_: NumberParseException) {
      Phone(number = repaired.trim(), type = type, raw = raw, normalized = false)
    }
  }
}
