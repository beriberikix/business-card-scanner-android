package dev.jberi.cardscan.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.jberi.cardscan.capture.loadBitmapForOcr
import dev.jberi.cardscan.capture.toPreview
import dev.jberi.cardscan.model.ContactCard
import dev.jberi.cardscan.ocr.MlKitOcrEngine
import dev.jberi.cardscan.ocr.OcrResult
import dev.jberi.cardscan.ocr.QrContactDetector
import dev.jberi.cardscan.parse.CardParser
import dev.jberi.cardscan.parse.HeuristicCardParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One scanned card: a preview to show, the OCR behind it, and the fields parsed out. */
data class CardResult(
  val preview: Bitmap,
  val ocr: OcrResult,
  val card: ContactCard,
  val fromBarcode: Boolean,
  val ocrMillis: Long,
)

data class ScanUiState(
  val busy: Boolean = false,
  val progress: String = "",
  val results: List<CardResult> = emptyList(),
  val index: Int = 0,
  val error: String? = null,
) {
  val current: CardResult? get() = results.getOrNull(index)
  val total: Int get() = results.size
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {
  private val ocrEngine = MlKitOcrEngine()
  private val barcode = QrContactDetector()
  private val parser: CardParser = HeuristicCardParser()

  private val _state = MutableStateFlow(ScanUiState())
  val state: StateFlow<ScanUiState> = _state.asStateFlow()

  /** When on, each scan is kept on the device so it can be pulled into the test fixture set. */
  var keepForTesting: Boolean = true

  /**
   * Runs the pipeline over every page of a scanning session, one card per page.
   *
   * Pages are processed in order and appended as they finish, so the first card is reviewable
   * while the rest are still being read.
   */
  fun process(uris: List<Uri>, defaultRegion: String = "US") {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      _state.value = ScanUiState(busy = true, progress = progressText(0, uris.size))
      val done = mutableListOf<CardResult>()

      uris.forEachIndexed { i, uri ->
        _state.update { it.copy(progress = progressText(i, uris.size)) }
        try {
          done += readCard(uri, defaultRegion)
          _state.update { it.copy(results = done.toList()) }
        } catch (t: Throwable) {
          Log.e(TAG, "page ${i + 1} failed", t)
          // One unreadable page should not discard the rest of the stack.
          _state.update {
            it.copy(error = "Card ${i + 1} could not be read: ${t.message}")
          }
        }
      }

      _state.update {
        it.copy(
          busy = false,
          progress = "",
          error = if (done.isEmpty()) (it.error ?: "Nothing could be read") else it.error,
        )
      }
    }
  }

  private fun progressText(doneCount: Int, total: Int) =
    if (total == 1) "Reading card" else "Reading card ${doneCount + 1} of $total"

  private suspend fun readCard(uri: Uri, defaultRegion: String): CardResult {
    val full = loadBitmapForOcr(getApplication(), uri)
    val t0 = System.currentTimeMillis()
    val ocr = withContext(Dispatchers.Default) { ocrEngine.recognize(full) }
    val ocrMs = System.currentTimeMillis() - t0

    // A vCard barcode is exact data, so it wins over anything parsed from pixels.
    val fromQr = runCatching { barcode.detect(full) }.getOrNull()
    val card = fromQr ?: parser.parse(ocr, defaultRegion)

    Log.i(TAG, "ocr ${ocr.lines.size} lines in ${ocrMs}ms, barcode=${fromQr != null}")
    if (keepForTesting) saveForTesting(full, ocr)

    // Drop the full-resolution bitmap here: a stack of twenty would not fit in memory.
    return CardResult(full.toPreview(), ocr, card, fromQr != null, ocrMs)
  }

  /**
   * Writes the captured image and its OCR result side by side under the app's external files
   * dir, named by timestamp. `tools/pull-scans.sh` copies them into `samples/`, which is how a
   * stack of real cards becomes a regression suite.
   */
  private suspend fun saveForTesting(bitmap: Bitmap, ocr: OcrResult) = withContext(Dispatchers.IO) {
    runCatching {
      val dir = File(getApplication<Application>().getExternalFilesDir(null), "captures")
        .apply { mkdirs() }
      val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
      File(dir, "$stamp.jpg").outputStream().use {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)
      }
      File(dir, "$stamp.ocr.json").writeText(Json.encodeToString(ocr))
      Log.i(TAG, "saved scan $stamp")
    }.onFailure { Log.w(TAG, "could not save scan", it) }
    Unit
  }

  /** Applies an edit from the review screen to the card currently being shown. */
  fun edited(card: ContactCard) = _state.update { s ->
    val updated = s.results.toMutableList()
    val at = s.index
    if (at !in updated.indices) return@update s
    updated[at] = updated[at].copy(card = card)
    s.copy(results = updated)
  }

  fun show(index: Int) = _state.update {
    it.copy(index = index.coerceIn(0, (it.results.size - 1).coerceAtLeast(0)))
  }

  fun next() = show(_state.value.index + 1)

  fun previous() = show(_state.value.index - 1)

  fun reset() = _state.update { ScanUiState() }

  fun fail(message: String) = _state.update { ScanUiState(error = message) }

  override fun onCleared() {
    ocrEngine.close()
    barcode.close()
  }

  companion object {
    const val TAG = "CardScan"
  }
}
