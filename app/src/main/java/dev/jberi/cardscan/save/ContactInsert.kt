package dev.jberi.cardscan.save

import android.content.ContentValues
import android.content.Intent
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Intents.Insert
import dev.jberi.cardscan.model.ContactCard
import dev.jberi.cardscan.model.PhoneType

/**
 * Builds an insert intent that opens the system contact editor prefilled with the card.
 *
 * Using the intent rather than a direct ContentProvider write means the app needs no contacts
 * permission at all, and the editor acts as a second review step before anything is committed.
 *
 * The flat extras only cover three phones, one email and no website, so the extra rows go in
 * [Insert.DATA] as ContentValues. The editor silently drops rows it cannot display, so the flat
 * extras are still set for the common fields as a belt-and-braces fallback.
 */
object ContactInsert {

  fun intentFor(card: ContactCard): Intent = Intent(Insert.ACTION).apply {
    type = ContactsContract.RawContacts.CONTENT_TYPE

    if (card.displayName.isNotBlank()) putExtra(Insert.NAME, card.displayName)
    if (card.company.isNotBlank()) putExtra(Insert.COMPANY, card.company)
    if (card.jobTitle.isNotBlank()) putExtra(Insert.JOB_TITLE, card.jobTitle)
    if (card.address.isNotBlank()) putExtra(Insert.POSTAL, card.address)
    if (card.note.isNotBlank()) putExtra(Insert.NOTES, card.note)

    card.phones.getOrNull(0)?.let {
      putExtra(Insert.PHONE, it.number)
      putExtra(Insert.PHONE_TYPE, androidPhoneType(it.type))
    }
    card.phones.getOrNull(1)?.let {
      putExtra(Insert.SECONDARY_PHONE, it.number)
      putExtra(Insert.SECONDARY_PHONE_TYPE, androidPhoneType(it.type))
    }
    card.phones.getOrNull(2)?.let {
      putExtra(Insert.TERTIARY_PHONE, it.number)
      putExtra(Insert.TERTIARY_PHONE_TYPE, androidPhoneType(it.type))
    }
    card.emails.getOrNull(0)?.let {
      putExtra(Insert.EMAIL, it.address)
      putExtra(Insert.EMAIL_TYPE, CommonDataKinds.Email.TYPE_WORK)
    }
    card.emails.getOrNull(1)?.let {
      putExtra(Insert.SECONDARY_EMAIL, it.address)
      putExtra(Insert.SECONDARY_EMAIL_TYPE, CommonDataKinds.Email.TYPE_OTHER)
    }

    val rows = dataRows(card)
    if (rows.isNotEmpty()) putExtra(Insert.DATA, ArrayList(rows))
  }

  /**
   * Rows the flat extras cannot express: structured name parts, website, any phone or email
   * beyond the third/second, and the organization title pairing.
   */
  internal fun dataRows(card: ContactCard): List<ContentValues> {
    val rows = mutableListOf<ContentValues>()

    if (card.givenName.isNotBlank() || card.familyName.isNotBlank()) {
      rows += ContentValues().apply {
        put(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        if (card.givenName.isNotBlank()) put(CommonDataKinds.StructuredName.GIVEN_NAME, card.givenName)
        if (card.middleName.isNotBlank()) put(CommonDataKinds.StructuredName.MIDDLE_NAME, card.middleName)
        if (card.familyName.isNotBlank()) put(CommonDataKinds.StructuredName.FAMILY_NAME, card.familyName)
        if (card.prefix.isNotBlank()) put(CommonDataKinds.StructuredName.PREFIX, card.prefix)
        if (card.suffix.isNotBlank()) put(CommonDataKinds.StructuredName.SUFFIX, card.suffix)
      }
    }

    if (card.company.isNotBlank() || card.jobTitle.isNotBlank()) {
      rows += ContentValues().apply {
        put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
        put(CommonDataKinds.Organization.TYPE, CommonDataKinds.Organization.TYPE_WORK)
        if (card.company.isNotBlank()) put(CommonDataKinds.Organization.COMPANY, card.company)
        if (card.jobTitle.isNotBlank()) put(CommonDataKinds.Organization.TITLE, card.jobTitle)
      }
    }

    card.phones.forEach { phone ->
      rows += ContentValues().apply {
        put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
        put(CommonDataKinds.Phone.NUMBER, phone.number)
        put(CommonDataKinds.Phone.TYPE, androidPhoneType(phone.type))
      }
    }

    card.emails.forEachIndexed { i, email ->
      rows += ContentValues().apply {
        put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
        put(CommonDataKinds.Email.ADDRESS, email.address)
        put(
          CommonDataKinds.Email.TYPE,
          if (i == 0) CommonDataKinds.Email.TYPE_WORK else CommonDataKinds.Email.TYPE_OTHER,
        )
      }
    }

    card.urls.forEach { url ->
      rows += ContentValues().apply {
        put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Website.CONTENT_ITEM_TYPE)
        put(CommonDataKinds.Website.URL, url)
        put(CommonDataKinds.Website.TYPE, CommonDataKinds.Website.TYPE_WORK)
      }
    }

    if (card.address.isNotBlank()) {
      rows += ContentValues().apply {
        put(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
        put(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, card.address)
        put(CommonDataKinds.StructuredPostal.TYPE, CommonDataKinds.StructuredPostal.TYPE_WORK)
      }
    }

    if (card.note.isNotBlank()) {
      rows += ContentValues().apply {
        put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Note.CONTENT_ITEM_TYPE)
        put(CommonDataKinds.Note.NOTE, card.note)
      }
    }

    return rows
  }

  internal fun androidPhoneType(type: PhoneType): Int = when (type) {
    PhoneType.MOBILE -> CommonDataKinds.Phone.TYPE_MOBILE
    PhoneType.WORK -> CommonDataKinds.Phone.TYPE_WORK
    PhoneType.HOME -> CommonDataKinds.Phone.TYPE_HOME
    PhoneType.FAX_WORK -> CommonDataKinds.Phone.TYPE_FAX_WORK
    PhoneType.MAIN -> CommonDataKinds.Phone.TYPE_MAIN
    PhoneType.OTHER -> CommonDataKinds.Phone.TYPE_OTHER
  }
}
