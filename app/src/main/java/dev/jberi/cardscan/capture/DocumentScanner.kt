package dev.jberi.cardscan.capture

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.tasks.await

/**
 * ML Kit Document Scanner. The capture UI, crop and perspective correction all run inside Play
 * services, so this app needs no CAMERA permission and no viewfinder code. The module is
 * downloaded once on first use; after that it works offline.
 *
 * The page limit is deliberately high. The scanner keeps the camera open until you finish, so a
 * whole stack of cards can be captured in one uninterrupted session and each page comes back as a
 * separate image. That is the fast way to build a fixture set, and it keeps the crop and
 * perspective correction that make those fixtures match what the app really sees.
 */
object DocumentScanner {

  /** Cards per scanning session. High enough for a stack, bounded so memory stays predictable. */
  const val PAGE_LIMIT = 20

  private val options = GmsDocumentScannerOptions.Builder()
    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
    .setGalleryImportAllowed(true)
    .setPageLimit(PAGE_LIMIT)
    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
    .build()

  suspend fun startScanIntent(activity: Activity): IntentSender =
    GmsDocumentScanning.getClient(options).getStartScanIntent(activity).await()

  /** Page image Uris from a scanner result, or empty if the scan was cancelled. */
  fun pagesFrom(data: Intent?): List<Uri> =
    GmsDocumentScanningResult.fromActivityResultIntent(data)
      ?.pages.orEmpty()
      .map { it.imageUri }
}
