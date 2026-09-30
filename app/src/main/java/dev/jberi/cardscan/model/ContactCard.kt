package dev.jberi.cardscan.model

import kotlinx.serialization.Serializable

@Serializable
enum class PhoneType { MOBILE, WORK, HOME, FAX_WORK, MAIN, OTHER }

/**
 * @param number normalized to E.164 when it could be parsed, otherwise the cleaned raw digits.
 * @param raw exactly what OCR produced, kept so the review UI can show what was read.
 * @param normalized false when E.164 parsing failed, so the UI can flag the field.
 */
@Serializable
data class Phone(
  val number: String,
  val type: PhoneType = PhoneType.WORK,
  val raw: String = number,
  val normalized: Boolean = true,
)

/**
 * @param repaired true when OCR damaged the address and it was reconstructed, e.g. a missing dot
 *   before the top-level domain. Repairs are guesses, so the review UI calls them out.
 */
@Serializable
data class Email(
  val address: String,
  val raw: String = address,
  val repaired: Boolean = false,
)

/** Everything extracted from one card. All fields optional; the review UI is the source of truth. */
@Serializable
data class ContactCard(
  val givenName: String = "",
  val familyName: String = "",
  val middleName: String = "",
  val prefix: String = "",
  val suffix: String = "",
  val jobTitle: String = "",
  val company: String = "",
  val phones: List<Phone> = emptyList(),
  val emails: List<Email> = emptyList(),
  val urls: List<String> = emptyList(),
  val address: String = "",
  val note: String = "",
) {
  val displayName: String
    get() = listOf(prefix, givenName, middleName, familyName, suffix)
      .filter { it.isNotBlank() }
      .joinToString(" ")

  val isEmpty: Boolean
    get() = displayName.isBlank() && company.isBlank() &&
      phones.isEmpty() && emails.isEmpty() && urls.isEmpty() && address.isBlank()
}
