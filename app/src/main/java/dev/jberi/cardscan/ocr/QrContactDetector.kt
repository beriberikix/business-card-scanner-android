package dev.jberi.cardscan.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dev.jberi.cardscan.model.ContactCard
import dev.jberi.cardscan.model.Email
import dev.jberi.cardscan.model.Phone
import dev.jberi.cardscan.model.PhoneType
import kotlinx.coroutines.tasks.await

/**
 * Looks for a vCard/MeCard QR code on the already-captured image.
 *
 * Runs on the same still as OCR, so it costs one extra call and no extra UI. When a card carries
 * an embedded contact barcode the data is exact, which beats anything OCR plus heuristics can do.
 */
class QrContactDetector {
  private val scanner = BarcodeScanning.getClient()

  suspend fun detect(bitmap: Bitmap): ContactCard? {
    val barcodes = scanner.process(InputImage.fromBitmap(bitmap, 0)).await()
    val info = barcodes.firstOrNull { it.valueType == Barcode.TYPE_CONTACT_INFO }
      ?.contactInfo ?: return null

    val name = info.name
    val phones = info.phones.mapNotNull { p ->
      p.number?.takeIf { it.isNotBlank() }?.let { Phone(it, phoneType(p.type), it) }
    }
    return ContactCard(
      givenName = name?.first.orEmpty(),
      middleName = name?.middle.orEmpty(),
      familyName = name?.last.orEmpty(),
      prefix = name?.prefix.orEmpty(),
      suffix = name?.suffix.orEmpty(),
      jobTitle = info.title.orEmpty(),
      company = info.organization.orEmpty(),
      phones = phones,
      emails = info.emails.mapNotNull { e -> e.address?.takeIf(String::isNotBlank)?.let { Email(it) } },
      urls = info.urls.orEmpty().filter { it.isNotBlank() },
      address = info.addresses.firstOrNull()?.addressLines?.joinToString(", ").orEmpty(),
    ).takeIf { !it.isEmpty }
  }

  private fun phoneType(type: Int): PhoneType = when (type) {
    Barcode.Phone.TYPE_MOBILE -> PhoneType.MOBILE
    Barcode.Phone.TYPE_WORK -> PhoneType.WORK
    Barcode.Phone.TYPE_HOME -> PhoneType.HOME
    Barcode.Phone.TYPE_FAX -> PhoneType.FAX_WORK
    else -> PhoneType.OTHER
  }

  fun close() = scanner.close()
}
